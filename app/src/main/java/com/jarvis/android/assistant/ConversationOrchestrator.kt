package com.jarvis.android.assistant

import com.jarvis.android.engine.AudioRecorder
import com.jarvis.android.engine.LLMEngine
import com.jarvis.android.engine.TTSEngine
import com.jarvis.android.engine.VADEngine
import com.jarvis.android.engine.WhisperEngine
import com.jarvis.android.data.repository.ConversationRepository
import com.jarvis.android.worker.SummarizationWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Drives the continuous voice chat loop:
 *
 *   IDLE → LISTENING → TRANSCRIBING → THINKING → SPEAKING → LISTENING → …
 *
 * Barge-in: VAD runs continuously during SPEAKING. If speech is detected
 * while Jarvis is talking, TTS and LLM inference are cancelled immediately
 * and the state returns to LISTENING.
 */
@Singleton
class ConversationOrchestrator @Inject constructor(
    private val whisperEngine: WhisperEngine,
    private val vadEngine: VADEngine,
    private val llmEngine: LLMEngine,
    private val ttsEngine: TTSEngine,
    private val contextBuilder: ContextBuilder,
    private val conversationRepo: ConversationRepository,
    private val memoryExtractor: MemoryExtractor,
    private val summarizationWorker: SummarizationWorker,
    private val audioRecorder: AudioRecorder = AudioRecorder()
) {
    private val _state = MutableStateFlow(OrchestratorState.IDLE)
    val state: StateFlow<OrchestratorState> = _state.asStateFlow()

    // Streams partial LLM tokens to the UI as they arrive
    private val _streamingResponse = MutableSharedFlow<String>(extraBufferCapacity = 64)
    val streamingResponse: SharedFlow<String> = _streamingResponse.asSharedFlow()

    private var listeningJob: Job? = null
    private var inferenceJob: Job? = null

    /** Start the continuous voice chat loop for [convId]. No-op if already listening. */
    fun startListening(convId: Long, scope: CoroutineScope) {
        if (_state.value != OrchestratorState.IDLE) return
        _state.value = OrchestratorState.LISTENING
        vadEngine.reset()

        listeningJob = scope.launch(Dispatchers.Default) {
            val pcmBuffer = mutableListOf<Float>()
            var speechActive = false

            audioRecorder.record().collect { chunk ->
                val isSpeech = vadEngine.isSpeech(chunk)

                when {
                    // Barge-in: user speaks while Jarvis is talking
                    isSpeech && _state.value == OrchestratorState.SPEAKING -> {
                        ttsEngine.stop()
                        inferenceJob?.cancel()
                        pcmBuffer.clear()
                        speechActive = true
                        pcmBuffer.addAll(chunk.toList())
                        _state.value = OrchestratorState.LISTENING
                    }

                    // Accumulate speech
                    isSpeech -> {
                        speechActive = true
                        pcmBuffer.addAll(chunk.toList())
                    }

                    // Silence after speech → end of utterance
                    !isSpeech && speechActive -> {
                        speechActive = false
                        val pcm = pcmBuffer.toFloatArray()
                        pcmBuffer.clear()
                        inferenceJob = scope.launch(Dispatchers.Default) {
                            processUtterance(pcm, convId, scope)
                        }
                    }
                }
            }
        }
    }

    private suspend fun processUtterance(pcm: FloatArray, convId: Long, scope: CoroutineScope) {
        // Transcribe
        _state.value = OrchestratorState.TRANSCRIBING
        val transcript = whisperEngine.transcribe(pcm)
        if (transcript.isBlank()) {
            _state.value = OrchestratorState.LISTENING
            return
        }
        conversationRepo.addMessage(convId, "user", transcript)

        // Generate response
        _state.value = OrchestratorState.THINKING
        val prompt = contextBuilder.build(convId, transcript)
        val responseBuilder = StringBuilder()
        val sentenceBuffer = StringBuilder()

        _state.value = OrchestratorState.SPEAKING

        llmEngine.generate(prompt).collect { token ->
            responseBuilder.append(token)
            sentenceBuffer.append(token)
            _streamingResponse.emit(token)

            // Speak each complete sentence without waiting for the full response
            val buf = sentenceBuffer.toString()
            val sentenceEnd = buf.indexOfFirst { it == '.' || it == '!' || it == '?' }
            if (sentenceEnd != -1 && sentenceEnd < buf.length - 1) {
                ttsEngine.speak(buf.substring(0, sentenceEnd + 1).trim())
                sentenceBuffer.clear()
                sentenceBuffer.append(buf.substring(sentenceEnd + 1))
            }
        }

        // Speak any remaining text after generation finishes
        val remaining = sentenceBuffer.toString().trim()
        if (remaining.isNotEmpty()) ttsEngine.speak(remaining)

        val fullResponse = responseBuilder.toString().trim()
        conversationRepo.addMessage(convId, "assistant", fullResponse)

        // Background: extract memories + maybe summarise
        scope.launch(Dispatchers.Default) {
            memoryExtractor.extractAndSave("User: $transcript\nAssistant: $fullResponse")
            summarizationWorker.runIfNeeded(convId)
        }

        if (_state.value == OrchestratorState.SPEAKING) {
            _state.value = OrchestratorState.LISTENING
        }
    }

    /** Stop all ongoing activity and return to IDLE. */
    fun stop() {
        listeningJob?.cancel()
        inferenceJob?.cancel()
        ttsEngine.stop()
        _state.value = OrchestratorState.IDLE
    }
}
