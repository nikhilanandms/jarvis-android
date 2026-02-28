package com.jarvis.android.worker

import com.jarvis.android.data.db.entities.MessageEntity
import com.jarvis.android.data.repository.ConversationRepository
import com.jarvis.android.data.repository.SummaryRepository
import com.jarvis.android.engine.LLMEngine
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Test

class SummarizationWorkerTest {

    private val conversationRepo = mockk<ConversationRepository>(relaxed = true)
    private val summaryRepo = mockk<SummaryRepository>(relaxed = true)
    private val llmEngine = mockk<LLMEngine>()
    private val worker = SummarizationWorker(conversationRepo, summaryRepo, llmEngine)

    private fun makeMessages(count: Int, convId: Long = 1L): List<MessageEntity> =
        (1..count).map {
            MessageEntity(
                id = it.toLong(),
                conversationId = convId,
                role = if (it % 2 == 0) "assistant" else "user",
                content = "message $it"
            )
        }

    @Test
    fun `summarizes when count exceeds threshold`() = runTest {
        val messages = makeMessages(20)
        coEvery { conversationRepo.countActiveMessages(1L) } returns 31
        coEvery { conversationRepo.getOldestMessages(1L, 20) } returns messages
        coEvery { llmEngine.generate(any()) } returns flowOf("Summary of the first 20 messages.")

        worker.runIfNeeded(1L)

        coVerify { summaryRepo.saveSummary(1L, any(), 1L, 20L) }
        coVerify { conversationRepo.markSummarized(messages.map { it.id }) }
    }

    @Test
    fun `skips when below threshold`() = runTest {
        coEvery { conversationRepo.countActiveMessages(1L) } returns 29

        worker.runIfNeeded(1L)

        coVerify(exactly = 0) { summaryRepo.saveSummary(any(), any(), any(), any()) }
        coVerify(exactly = 0) { conversationRepo.markSummarized(any()) }
    }

    @Test
    fun `does nothing when no messages to summarize`() = runTest {
        coEvery { conversationRepo.countActiveMessages(1L) } returns 31
        coEvery { conversationRepo.getOldestMessages(1L, 20) } returns emptyList()

        worker.runIfNeeded(1L)

        coVerify(exactly = 0) { summaryRepo.saveSummary(any(), any(), any(), any()) }
    }
}
