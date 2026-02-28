package com.jarvis.android.assistant

import com.jarvis.android.data.repository.MemoryRepository
import com.jarvis.android.engine.LLMEngine
import org.json.JSONArray
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Extracts persistent facts from conversation snippets using Gemma,
 * then upserts them into the MemoryRepository.
 *
 * Expected LLM output: JSON array of {"key": "...", "value": "..."} pairs.
 * JSON may be embedded in surrounding prose — the extractor finds the first
 * '[' ... ']' range and parses only that region.
 */
@Singleton
class MemoryExtractor @Inject constructor(
    private val llmEngine: LLMEngine,
    private val memoryRepo: MemoryRepository
) {
    suspend fun extractAndSave(conversationSnippet: String) {
        val prompt = buildString {
            append("<start_of_turn>system\n")
            append("Extract facts about the user from the conversation below.\n")
            append("Return ONLY a JSON array of {\"key\": \"...\", \"value\": \"...\"} objects.\n")
            append("Keys must be snake_case (e.g. user_name, city, favourite_food).\n")
            append("If there are no facts to extract, return an empty array [].\n")
            append("Do not include any explanation text — only the JSON array.\n")
            append("<end_of_turn>\n")
            append("<start_of_turn>user\n")
            append("$conversationSnippet\n")
            append("<end_of_turn>\n")
            append("<start_of_turn>model\n")
        }

        val responseBuilder = StringBuilder()
        llmEngine.generate(prompt).collect { responseBuilder.append(it) }
        val response = responseBuilder.toString().trim()

        parseAndSave(response)
    }

    private suspend fun parseAndSave(response: String) {
        try {
            val start = response.indexOf('[')
            val end = response.lastIndexOf(']')
            if (start == -1 || end == -1 || end < start) return

            val jsonArray = JSONArray(response.substring(start, end + 1))
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val key = obj.optString("key").trim()
                val value = obj.optString("value").trim()
                if (key.isNotBlank() && value.isNotBlank()) {
                    memoryRepo.upsert(key, value)
                }
            }
        } catch (_: Exception) {
            // Malformed JSON — skip silently
        }
    }
}
