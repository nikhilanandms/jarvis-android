package com.jarvis.android.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.jarvis.android.data.db.entities.MessageEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MessageDao {
    @Insert
    suspend fun insert(message: MessageEntity): Long

    @Query("SELECT * FROM messages WHERE conversationId = :convId AND isSummarized = 0 ORDER BY timestamp ASC")
    fun getMessages(convId: Long): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages WHERE conversationId = :convId AND isSummarized = 0 ORDER BY timestamp ASC")
    suspend fun getMessagesList(convId: Long): List<MessageEntity>

    @Query("SELECT COUNT(*) FROM messages WHERE conversationId = :convId AND isSummarized = 0")
    suspend fun countActiveMessages(convId: Long): Int

    @Query("SELECT * FROM messages WHERE conversationId = :convId AND isSummarized = 0 ORDER BY timestamp ASC LIMIT :limit")
    suspend fun getOldestMessages(convId: Long, limit: Int): List<MessageEntity>

    @Query("UPDATE messages SET isSummarized = 1 WHERE id IN (:ids)")
    suspend fun markSummarized(ids: List<Long>)
}
