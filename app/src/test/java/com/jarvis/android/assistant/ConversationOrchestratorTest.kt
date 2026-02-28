package com.jarvis.android.assistant

import com.jarvis.android.data.repository.ConversationRepository
import com.jarvis.android.engine.AudioRecorder
import com.jarvis.android.engine.LLMEngine
import com.jarvis.android.engine.TTSEngine
import com.jarvis.android.engine.VADEngine
import com.jarvis.android.engine.WhisperEngine
import com.jarvis.android.worker.SummarizationWorker
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class ConversationOrchestratorTest {

    private val whisperEngine    = mockk<WhisperEngine>(relaxed = true)
    private val vadEngine        = mockk<VADEngine>(relaxed = true)
    private val llmEngine        = mockk<LLMEngine>(relaxed = true)
    private val ttsEngine        = mockk<TTSEngine>(relaxed = true)
    private val contextBuilder   = mockk<ContextBuilder>(relaxed = true)
    private val conversationRepo = mockk<ConversationRepository>(relaxed = true)
    private val memoryExtractor  = mockk<MemoryExtractor>(relaxed = true)
    private val sumWorker        = mockk<SummarizationWorker>(relaxed = true)
    private val audioRecorder    = mockk<AudioRecorder>(relaxed = true)

    private fun makeOrchestrator() = ConversationOrchestrator(
        whisperEngine, vadEngine, llmEngine, ttsEngine,
        contextBuilder, conversationRepo, memoryExtractor, sumWorker,
        audioRecorder
    )

    @Test
    fun `initial state is IDLE`() {
        val orc = makeOrchestrator()
        assertEquals(OrchestratorState.IDLE, orc.state.value)
    }

    @Test
    fun `startListening transitions to LISTENING`() = runTest {
        val orc = makeOrchestrator()
        orc.startListening(convId = 1L, scope = this)
        assertEquals(OrchestratorState.LISTENING, orc.state.value)
        orc.stop()
    }

    @Test
    fun `stop transitions back to IDLE`() = runTest {
        val orc = makeOrchestrator()
        orc.startListening(convId = 1L, scope = this)
        orc.stop()
        assertEquals(OrchestratorState.IDLE, orc.state.value)
    }

    @Test
    fun `cannot startListening when already listening`() = runTest {
        val orc = makeOrchestrator()
        orc.startListening(convId = 1L, scope = this)
        orc.startListening(convId = 1L, scope = this) // second call is a no-op
        assertEquals(OrchestratorState.LISTENING, orc.state.value)
        orc.stop()
    }
}
