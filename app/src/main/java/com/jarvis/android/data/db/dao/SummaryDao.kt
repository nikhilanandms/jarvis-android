package com.jarvis.android.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.jarvis.android.data.db.entities.SummaryEntity

@Dao
interface SummaryDao {
    @Insert
    suspend fun insert(summary: SummaryEntity)

    @Query("SELECT * FROM summaries WHERE conversationId = :convId ORDER BY createdAt ASC")
    suspend fun getSummaries(convId: Long): List<SummaryEntity>
}
