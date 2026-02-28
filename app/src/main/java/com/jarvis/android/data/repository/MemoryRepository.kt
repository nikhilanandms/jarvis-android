package com.jarvis.android.data.repository

import com.jarvis.android.data.db.dao.MemoryDao
import com.jarvis.android.data.db.entities.MemoryEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MemoryRepository @Inject constructor(private val memoryDao: MemoryDao) {

    suspend fun upsert(key: String, value: String) =
        memoryDao.upsert(MemoryEntity(key = key, value = value, updatedAt = System.currentTimeMillis()))

    suspend fun getAll(): List<MemoryEntity> = memoryDao.getAllMemoriesList()

    fun getAllFlow(): Flow<List<MemoryEntity>> = memoryDao.getAllMemories()
}
