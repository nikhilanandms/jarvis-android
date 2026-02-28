package com.jarvis.android.assistant

import com.jarvis.android.data.repository.MemoryRepository
import com.jarvis.android.engine.LLMEngine
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Test

class MemoryExtractorTest {

    private val llmEngine = mockk<LLMEngine>()
    private val memoryRepo = mockk<MemoryRepository>(relaxed = true)
    private val extractor = MemoryExtractor(llmEngine, memoryRepo)

    @Test
    fun `extracts and saves key-value facts`() = runTest {
        val json = """[{"key":"user_name","value":"Nikhil"},{"key":"city","value":"Mumbai"}]"""
        coEvery { llmEngine.generate(any()) } returns flowOf(json)

        extractor.extractAndSave("User said: My name is Nikhil and I live in Mumbai.")

        coVerify { memoryRepo.upsert("user_name", "Nikhil") }
        coVerify { memoryRepo.upsert("city", "Mumbai") }
    }

    @Test
    fun `empty array saves nothing`() = runTest {
        coEvery { llmEngine.generate(any()) } returns flowOf("[]")

        extractor.extractAndSave("User asked about the weather.")

        coVerify(exactly = 0) { memoryRepo.upsert(any(), any()) }
    }

    @Test
    fun `malformed JSON is silently ignored`() = runTest {
        coEvery { llmEngine.generate(any()) } returns flowOf("not json at all")

        extractor.extractAndSave("Some conversation text.")

        coVerify(exactly = 0) { memoryRepo.upsert(any(), any()) }
    }

    @Test
    fun `JSON embedded in prose is extracted`() = runTest {
        // LLM may wrap JSON in explanation text — extractor should still find it
        val response = """Sure! Here are the facts: [{"key":"pet","value":"dog"}]"""
        coEvery { llmEngine.generate(any()) } returns flowOf(response)

        extractor.extractAndSave("User mentioned they have a dog.")

        coVerify { memoryRepo.upsert("pet", "dog") }
    }
}
