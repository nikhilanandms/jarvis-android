package com.jarvis.android.data.repository

import com.jarvis.android.data.db.dao.ConversationDao
import com.jarvis.android.data.db.dao.MessageDao
import com.jarvis.android.data.db.entities.MessageEntity
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Test

class ConversationRepositoryTest {
    private val messageDao = mockk<MessageDao>(relaxed = true)
    private val conversationDao = mockk<ConversationDao>(relaxed = true)
    private val repo = ConversationRepository(conversationDao, messageDao)

    @Test
    fun `addMessage inserts message and touches conversation`() = runTest {
        coEvery { messageDao.insert(any()) } returns 1L

        repo.addMessage(convId = 1L, role = "user", content = "hello")

        coVerify { messageDao.insert(match { it.content == "hello" && it.role == "user" && it.conversationId == 1L }) }
        coVerify { conversationDao.touch(1L, any()) }
    }

    @Test
    fun `getRecentMessages returns last N messages`() = runTest {
        val messages = (1..15).map {
            MessageEntity(id = it.toLong(), conversationId = 1L, role = "user", content = "msg $it")
        }
        coEvery { messageDao.getMessagesList(1L) } returns messages

        val result = repo.getRecentMessages(convId = 1L, limit = 10)

        assert(result.size == 10)
        assert(result.first().content == "msg 6")
        assert(result.last().content == "msg 15")
    }
}
