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
        _state.value = OrchestratorState.LISTENING  // guard BEFORE launching coroutine
        vadEngine.reset()

        listeningJob = scope.launch(Dispatchers.Default) {
            val pcmBuffer = mutableListOf<Float>()
            var speechActive = false
            var silentChunks = 0

            // Tuning constants
            // 15 silent chunks × 32ms = ~480ms silence → end of utterance
            val SILENCE_TO_END = 15
            // Whisper needs at least 1s (16000 samples) to work reliably
            val MIN_SPEECH_SAMPLES = 16000

            audioRecorder.record().collect { chunk ->
                val isSpeech = vadEngine.isSpeech(chunk, threshold = 0.3f)

                when {
                    // Barge-in: user speaks while Jarvis is talking
                    isSpeech && _state.value == OrchestratorState.SPEAKING -> {
                        ttsEngine.stop()
                        inferenceJob?.cancel()
                        pcmBuffer.clear()
                        speechActive = true
                        silentChunks = 0
                        pcmBuffer.addAll(chunk.toList())
                        _state.value = OrchestratorState.LISTENING
                    }

                    // Accumulate speech
                    isSpeech -> {
                        speechActive = true
                        silentChunks = 0
                        pcmBuffer.addAll(chunk.toList())
                    }

                    // Silence while speech was active — wait for sustained silence
                    !isSpeech && speechActive -> {
                        silentChunks++
                        pcmBuffer.addAll(chunk.toList()) // include trailing silence for natural end
                        if (silentChunks >= SILENCE_TO_END) {
                            speechActive = false
                            silentChunks = 0
                            val pcm = pcmBuffer.toFloatArray()
                            pcmBuffer.clear()
                            // Only send to Whisper if enough audio was captured
                            if (pcm.size >= MIN_SPEECH_SAMPLES) {
                                inferenceJob = scope.launch(Dispatchers.Default) {
                                    processUtterance(pcm, convId, scope)
                                }
                            } else {
                                android.util.Log.d("Orchestrator", "Utterance too short (${pcm.size} samples), discarding")
                            }
                        }
                    }
                }
            }
        }
    }

    private suspend fun processUtterance(pcm: FloatArray, convId: Long, scope: CoroutineScope) {
        android.util.Log.d("Orchestrator", "processUtterance: ${pcm.size} samples")
        if (pcm.size < 16000) {
            android.util.Log.d("Orchestrator", "Discarding short audio (${pcm.size} samples < 16000)")
            _state.value = OrchestratorState.LISTENING
            return
        }
        // Transcribe
        _state.value = OrchestratorState.TRANSCRIBING
        val transcript = whisperEngine.transcribe(pcm)
        android.util.Log.d("Orchestrator", "transcript: '$transcript'")
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

    /**
     * Submit a typed text message and generate a response.
     * Skips the audio/transcription step — goes straight to THINKING → SPEAKING.
     */
    fun submitText(text: String, convId: Long, scope: CoroutineScope) {
        // Stop voice if listening, but don't cancel an already-running inference
        if (_state.value == OrchestratorState.LISTENING) stopListening()
        if (_state.value != OrchestratorState.IDLE) return
        _state.value = OrchestratorState.THINKING  // guard BEFORE launching coroutine
        inferenceJob = scope.launch(Dispatchers.Default) {
            android.util.Log.d("Orchestrator", "submitText: $text")
            conversationRepo.addMessage(convId, "user", text)
            _state.value = OrchestratorState.THINKING
            val prompt = contextBuilder.build(convId, text)
            val responseBuilder = StringBuilder()
            val sentenceBuffer = StringBuilder()

            _state.value = OrchestratorState.SPEAKING
            llmEngine.generate(prompt).collect { token ->
                android.util.Log.d("Orchestrator", "token: $token")
                responseBuilder.append(token)
                sentenceBuffer.append(token)
                _streamingResponse.emit(token)

                val buf = sentenceBuffer.toString()
                val sentenceEnd = buf.indexOfFirst { it == '.' || it == '!' || it == '?' }
                if (sentenceEnd != -1 && sentenceEnd < buf.length - 1) {
                    ttsEngine.speak(buf.substring(0, sentenceEnd + 1).trim())
                    sentenceBuffer.clear()
                    sentenceBuffer.append(buf.substring(sentenceEnd + 1))
                }
            }

            val remaining = sentenceBuffer.toString().trim()
            if (remaining.isNotEmpty()) ttsEngine.speak(remaining)

            val fullResponse = responseBuilder.toString().trim()
            android.util.Log.d("Orchestrator", "full response: $fullResponse")
            conversationRepo.addMessage(convId, "assistant", fullResponse)

            scope.launch(Dispatchers.Default) {
                memoryExtractor.extractAndSave("User: $text\nAssistant: $fullResponse")
                summarizationWorker.runIfNeeded(convId)
            }
            _state.value = OrchestratorState.IDLE
        }
    }

    /**
     * Stop voice listening only — does NOT cancel in-progress LLM inference.
     * Call this before submitting a text message if voice might be active.
     */
    fun stopListening() {
        listeningJob?.cancel()
        listeningJob = null
        if (_state.value == OrchestratorState.LISTENING) {
            _state.value = OrchestratorState.IDLE
        }
    }

    /** Stop all ongoing activity (voice + inference) and return to IDLE. */
    fun stop() {
        listeningJob?.cancel()
        inferenceJob?.cancel()
        ttsEngine.stop()
        listeningJob = null
        inferenceJob = null
        _state.value = OrchestratorState.IDLE
    }
}
