package com.jarvis.android.integration

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jarvis.android.data.db.AppDatabase
import com.jarvis.android.data.repository.MemoryRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MemoryPersistenceTest {

    @Test
    fun memoriesPersistedAcrossDbInstances() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dbFile = context.getDatabasePath("test_jarvis_memory.db")
        dbFile.delete()

        // Session 1 — write a memory
        val db1 = Room.databaseBuilder(context, AppDatabase::class.java, "test_jarvis_memory.db")
            .build()
        MemoryRepository(db1.memoryDao()).upsert("user_name", "Nikhil")
        db1.close()

        // Session 2 — read it back
        val db2 = Room.databaseBuilder(context, AppDatabase::class.java, "test_jarvis_memory.db")
            .build()
        val memories = MemoryRepository(db2.memoryDao()).getAll()
        db2.close()

        assertEquals(1, memories.size)
        assertEquals("user_name", memories[0].key)
        assertEquals("Nikhil", memories[0].value)

        dbFile.delete()
    }

    @Test
    fun upsertOverwritesExistingKey() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        val repo = MemoryRepository(db.memoryDao())

        repo.upsert("city", "Mumbai")
        repo.upsert("city", "Pune")  // same key, different value

        val memories = repo.getAll()
        assertEquals(1, memories.size)
        assertEquals("Pune", memories[0].value)
        db.close()
    }

    @Test
    fun summarizationThresholdIntegration() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        val convRepo = com.jarvis.android.data.repository.ConversationRepository(
            db.conversationDao(), db.messageDao()
        )

        val convId = convRepo.createConversation()
        // Insert 31 messages
        repeat(31) { convRepo.addMessage(convId, "user", "message $it") }

        val count = db.messageDao().countActiveMessages(convId)
        assertEquals(31, count)

        // Mark oldest 20 as summarized
        val oldest = db.messageDao().getOldestMessages(convId, 20)
        convRepo.markSummarized(oldest.map { it.id })

        val activeCount = db.messageDao().countActiveMessages(convId)
        assertEquals(11, activeCount)
        db.close()
    }
}
