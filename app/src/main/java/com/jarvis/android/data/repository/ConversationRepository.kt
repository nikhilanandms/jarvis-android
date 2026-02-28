package com.jarvis.android.data.repository

import com.jarvis.android.data.db.dao.ConversationDao
import com.jarvis.android.data.db.dao.MessageDao
import com.jarvis.android.data.db.entities.ConversationEntity
import com.jarvis.android.data.db.entities.MessageEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ConversationRepository @Inject constructor(
    private val conversationDao: ConversationDao,
    private val messageDao: MessageDao
) {
    suspend fun createConversation(title: String = "New Conversation"): Long =
        conversationDao.insert(ConversationEntity(title = title))

    suspend fun addMessage(convId: Long, role: String, content: String): Long {
        conversationDao.touch(convId, System.currentTimeMillis())
        return messageDao.insert(MessageEntity(conversationId = convId, role = role, content = content))
    }

    fun getMessages(convId: Long): Flow<List<MessageEntity>> = messageDao.getMessages(convId)

    suspend fun getRecentMessages(convId: Long, limit: Int = 10): List<MessageEntity> =
        messageDao.getMessagesList(convId).takeLast(limit)

    suspend fun countActiveMessages(convId: Long): Int = messageDao.countActiveMessages(convId)

    suspend fun getOldestMessages(convId: Long, count: Int): List<MessageEntity> =
        messageDao.getOldestMessages(convId, count)

    suspend fun markSummarized(ids: List<Long>) = messageDao.markSummarized(ids)

    fun getAllConversations(): Flow<List<ConversationEntity>> = conversationDao.getAll()
}
