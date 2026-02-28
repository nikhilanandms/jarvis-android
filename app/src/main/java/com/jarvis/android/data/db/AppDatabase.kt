package com.jarvis.android.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.jarvis.android.data.db.dao.ConversationDao
import com.jarvis.android.data.db.dao.MemoryDao
import com.jarvis.android.data.db.dao.MessageDao
import com.jarvis.android.data.db.dao.SummaryDao
import com.jarvis.android.data.db.entities.ConversationEntity
import com.jarvis.android.data.db.entities.MemoryEntity
import com.jarvis.android.data.db.entities.MessageEntity
import com.jarvis.android.data.db.entities.SummaryEntity

@Database(
    entities = [
        ConversationEntity::class,
        MessageEntity::class,
        MemoryEntity::class,
        SummaryEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun conversationDao(): ConversationDao
    abstract fun messageDao(): MessageDao
    abstract fun memoryDao(): MemoryDao
    abstract fun summaryDao(): SummaryDao
}
