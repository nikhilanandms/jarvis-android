package com.jarvis.android.data.db

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jarvis.android.data.db.entities.MemoryEntity
import com.jarvis.android.data.db.entities.MessageEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DatabaseTest {
    private lateinit var db: AppDatabase
    private lateinit var messageDao: com.jarvis.android.data.db.dao.MessageDao
    private lateinit var memoryDao: com.jarvis.android.data.db.dao.MemoryDao

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        messageDao = db.messageDao()
        memoryDao = db.memoryDao()
    }

    @After
    fun teardown() = db.close()

    @Test
    fun insertAndRetrieveMessage() = runTest {
        val convId = 1L
        val msg = MessageEntity(
            conversationId = convId,
            role = "user",
            content = "hello",
            timestamp = System.currentTimeMillis()
        )
        messageDao.insert(msg)
        val messages = messageDao.getMessages(convId).first()
        assertEquals(1, messages.size)
        assertEquals("hello", messages[0].content)
        assertEquals("user", messages[0].role)
    }

    @Test
    fun upsertMemoryByKey() = runTest {
        memoryDao.upsert(MemoryEntity(key = "user_name", value = "Nikhil"))
        memoryDao.upsert(MemoryEntity(key = "user_name", value = "Nikhil Anand"))
        val memories = memoryDao.getAllMemories().first()
        assertEquals(1, memories.size)
        assertEquals("Nikhil Anand", memories[0].value)
    }

    @Test
    fun countActiveMessages() = runTest {
        repeat(5) { i ->
            messageDao.insert(MessageEntity(conversationId = 1L, role = "user", content = "msg $i"))
        }
        assertEquals(5, messageDao.countActiveMessages(1L))
    }

    @Test
    fun markSummarizedExcludesFromActiveCount() = runTest {
        val ids = (1..3).map {
            messageDao.insert(MessageEntity(conversationId = 1L, role = "user", content = "msg $it"))
        }
        messageDao.markSummarized(ids)
        assertEquals(0, messageDao.countActiveMessages(1L))
    }
}
