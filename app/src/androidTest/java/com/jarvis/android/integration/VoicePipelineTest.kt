package com.jarvis.android.integration

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jarvis.android.assistant.ContextBuilder
import com.jarvis.android.data.db.AppDatabase
import com.jarvis.android.data.repository.ConversationRepository
import com.jarvis.android.data.repository.MemoryRepository
import com.jarvis.android.data.repository.SummaryRepository
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class VoicePipelineTest {

    private lateinit var db: AppDatabase
    private lateinit var convRepo: ConversationRepository
    private lateinit var memRepo: MemoryRepository
    private lateinit var sumRepo: SummaryRepository
    private lateinit var contextBuilder: ContextBuilder

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        convRepo = ConversationRepository(db.conversationDao(), db.messageDao())
        memRepo = MemoryRepository(db.memoryDao())
        sumRepo = SummaryRepository(db.summaryDao())
        contextBuilder = ContextBuilder(memRepo, sumRepo, convRepo)
    }

    @After
    fun teardown() = db.close()

    @Test
    fun contextBuilderIncludesMemoriesAndHistory() = runTest {
        val convId = convRepo.createConversation()
        convRepo.addMessage(convId, "user", "My name is Nikhil")
        convRepo.addMessage(convId, "assistant", "Nice to meet you, Nikhil!")
        memRepo.upsert("user_name", "Nikhil")

        val prompt = contextBuilder.build(convId, "What is my name?")

        assertTrue("Prompt missing memory", prompt.contains("user_name: Nikhil"))
        assertTrue("Prompt missing history", prompt.contains("My name is Nikhil"))
        assertTrue("Prompt missing assistant turn", prompt.contains("Nice to meet you"))
        assertTrue("Prompt missing new question", prompt.contains("What is my name?"))
    }

    @Test
    fun contextBuilderIncludesSummaryWhenPresent() = runTest {
        val convId = convRepo.createConversation()
        sumRepo.saveSummary(convId, "We discussed Android development.", 1L, 20L)

        val prompt = contextBuilder.build(convId, "Tell me more")

        assertTrue("Prompt missing summary", prompt.contains("We discussed Android development."))
        assertTrue("Prompt missing new message", prompt.contains("Tell me more"))
    }

    @Test
    fun messagesMarkedSummarizedAreExcludedFromContext() = runTest {
        val convId = convRepo.createConversation()
        // Add 5 messages then mark them summarized
        repeat(5) { convRepo.addMessage(convId, "user", "old message $it") }
        val oldMessages = db.messageDao().getMessagesList(convId)
        convRepo.markSummarized(oldMessages.map { it.id })

        // Add 2 new messages
        convRepo.addMessage(convId, "user", "new question")
        convRepo.addMessage(convId, "assistant", "new answer")

        val prompt = contextBuilder.build(convId, "follow up")

        assertTrue("New messages should appear", prompt.contains("new question"))
        assertTrue("Old summarized messages should not appear",
            !prompt.contains("old message 0"))
    }
}
