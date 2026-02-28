package com.jarvis.android.data.repository

import com.jarvis.android.data.db.dao.SummaryDao
import com.jarvis.android.data.db.entities.SummaryEntity
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SummaryRepository @Inject constructor(private val summaryDao: SummaryDao) {

    suspend fun saveSummary(convId: Long, content: String, startId: Long, endId: Long) =
        summaryDao.insert(
            SummaryEntity(
                conversationId = convId,
                content = content,
                messageRangeStart = startId,
                messageRangeEnd = endId
            )
        )

    suspend fun getSummaries(convId: Long): List<SummaryEntity> = summaryDao.getSummaries(convId)
}
