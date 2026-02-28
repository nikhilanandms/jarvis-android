package com.jarvis.android.assistant

import com.jarvis.android.data.repository.ConversationRepository
import com.jarvis.android.data.repository.MemoryRepository
import com.jarvis.android.data.repository.SummaryRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Assembles the full prompt for each LLM call.
 *
 * Prompt structure (Gemma instruction format):
 *   <start_of_turn>system
 *   You are Jarvis...
 *   Known facts: ...
 *   <end_of_turn>
 *   <start_of_turn>model
 *   [Earlier summary: ...]
 *   <end_of_turn>
 *   <start_of_turn>user / model  (recent messages)
 *   ...
 *   <start_of_turn>user
 *   [new message]
 *   <end_of_turn>
 *   <start_of_turn>model
 */
@Singleton
class ContextBuilder @Inject constructor(
    private val memoryRepo: MemoryRepository,
    private val summaryRepo: SummaryRepository,
    private val conversationRepo: ConversationRepository
) {
    suspend fun build(convId: Long, newUserMessage: String): String {
        val memories = memoryRepo.getAll()
        val summaries = summaryRepo.getSummaries(convId)
        val recentMessages = conversationRepo.getRecentMessages(convId, 10)

        return buildString {
            // System turn
            append("<start_of_turn>system\n")
            append("You are Jarvis, a local AI voice assistant. Be concise and helpful.\n")
            if (memories.isNotEmpty()) {
                append("Known facts about the user:\n")
                memories.forEach { append("- ${it.key}: ${it.value}\n") }
            }
            append("<end_of_turn>\n")

            // Summaries of older conversation
            summaries.forEach { summary ->
                append("<start_of_turn>model\n")
                append("[Earlier conversation summary: ${summary.content}]\n")
                append("<end_of_turn>\n")
            }

            // Recent message history
            recentMessages.forEach { msg ->
                val turn = if (msg.role == "user") "user" else "model"
                append("<start_of_turn>$turn\n")
                append("${msg.content}\n")
                append("<end_of_turn>\n")
            }

            // New user message
            append("<start_of_turn>user\n")
            append("$newUserMessage\n")
            append("<end_of_turn>\n")
            append("<start_of_turn>model\n")
        }
    }
}
