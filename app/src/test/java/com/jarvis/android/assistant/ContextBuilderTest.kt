package com.jarvis.android.assistant

import com.jarvis.android.data.db.entities.MemoryEntity
import com.jarvis.android.data.db.entities.MessageEntity
import com.jarvis.android.data.db.entities.SummaryEntity
import com.jarvis.android.data.repository.ConversationRepository
import com.jarvis.android.data.repository.MemoryRepository
import com.jarvis.android.data.repository.SummaryRepository
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Test

class ContextBuilderTest {

    private val memoryRepo = mockk<MemoryRepository>()
    private val summaryRepo = mockk<SummaryRepository>()
    private val conversationRepo = mockk<ConversationRepository>()
    private val builder = ContextBuilder(memoryRepo, summaryRepo, conversationRepo)

    @Test
    fun `buildPrompt includes memories and recent messages`() = runTest {
        coEvery { memoryRepo.getAll() } returns listOf(
            MemoryEntity(key = "user_name", value = "Nikhil")
        )
        coEvery { summaryRepo.getSummaries(1L) } returns emptyList()
        coEvery { conversationRepo.getRecentMessages(1L, 10) } returns listOf(
            MessageEntity(conversationId = 1L, role = "user", content = "Hello"),
            MessageEntity(conversationId = 1L, role = "assistant", content = "Hi there")
        )

        val prompt = builder.build(convId = 1L, newUserMessage = "What is my name?")

        assertTrue("Missing memory", prompt.contains("user_name: Nikhil"))
        assertTrue("Missing history user message", prompt.contains("Hello"))
        assertTrue("Missing history assistant message", prompt.contains("Hi there"))
        assertTrue("Missing new user message", prompt.contains("What is my name?"))
    }

    @Test
    fun `buildPrompt includes summary when present`() = runTest {
        coEvery { memoryRepo.getAll() } returns emptyList()
        coEvery { summaryRepo.getSummaries(1L) } returns listOf(
            SummaryEntity(
                conversationId = 1L,
                content = "We discussed the weather.",
                messageRangeStart = 1L,
                messageRangeEnd = 20L
            )
        )
        coEvery { conversationRepo.getRecentMessages(1L, 10) } returns emptyList()

        val prompt = builder.build(convId = 1L, newUserMessage = "Tell me more")

        assertTrue("Summary missing from prompt", prompt.contains("We discussed the weather."))
    }

    @Test
    fun `buildPrompt with no memories or history still includes new message`() = runTest {
        coEvery { memoryRepo.getAll() } returns emptyList()
        coEvery { summaryRepo.getSummaries(1L) } returns emptyList()
        coEvery { conversationRepo.getRecentMessages(1L, 10) } returns emptyList()

        val prompt = builder.build(convId = 1L, newUserMessage = "Hello Jarvis")

        assertTrue(prompt.contains("Hello Jarvis"))
        assertTrue(prompt.contains("Jarvis"))  // system message present
    }
}
