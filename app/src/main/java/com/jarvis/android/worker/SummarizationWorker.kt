package com.jarvis.android.worker

import com.jarvis.android.data.repository.ConversationRepository
import com.jarvis.android.data.repository.SummaryRepository
import com.jarvis.android.engine.LLMEngine
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Summarises old messages when a conversation exceeds [SUMMARIZE_THRESHOLD].
 * Runs in the background after each assistant response — zero latency impact.
 *
 * Strategy:
 *  1. Count active (non-summarized) messages.
 *  2. If count > threshold, take the oldest [SUMMARIZE_COUNT] messages.
 *  3. Ask Gemma to summarise them in 2–3 sentences.
 *  4. Save the summary and mark those messages as summarized.
 */
@Singleton
class SummarizationWorker @Inject constructor(
    private val conversationRepo: ConversationRepository,
    private val summaryRepo: SummaryRepository,
    private val llmEngine: LLMEngine
) {
    companion object {
        const val SUMMARIZE_THRESHOLD = 30
        const val SUMMARIZE_COUNT = 20
    }

    suspend fun runIfNeeded(convId: Long) {
        val count = conversationRepo.countActiveMessages(convId)
        if (count <= SUMMARIZE_THRESHOLD) return

        val messages = conversationRepo.getOldestMessages(convId, SUMMARIZE_COUNT)
        if (messages.isEmpty()) return

        val conversationText = messages.joinToString("\n") { msg ->
            "${msg.role.replaceFirstChar { it.uppercase() }}: ${msg.content}"
        }

        val prompt = buildString {
            append("<start_of_turn>system\n")
            append("Summarise the following conversation in 2-3 sentences.\n")
            append("Focus on key information, decisions, and facts mentioned.\n")
            append("Be concise.\n")
            append("<end_of_turn>\n")
            append("<start_of_turn>user\n")
            append("$conversationText\n")
            append("<end_of_turn>\n")
            append("<start_of_turn>model\n")
        }

        val summaryBuilder = StringBuilder()
        llmEngine.generate(prompt).collect { summaryBuilder.append(it) }
        val summary = summaryBuilder.toString().trim()

        val startId = messages.first().id
        val endId = messages.last().id
        summaryRepo.saveSummary(convId, summary, startId, endId)
        conversationRepo.markSummarized(messages.map { it.id })
    }
}
