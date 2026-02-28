package com.jarvis.android.engine

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.time.Duration.Companion.seconds

@RunWith(AndroidJUnit4::class)
class LLMEngineTest {

    @Test
    fun generateStreamsTokens() = runTest(timeout = 120.seconds) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val modelPath = context.getExternalFilesDir(null)?.absolutePath + "/gemma3-1b-it-int4.task"
        assumeTrue("Gemma model must exist at $modelPath", File(modelPath).exists())

        val engine = LLMEngine(context, modelPath)
        val tokens = engine.generate("Say hello in one word.").toList()
        assertTrue("Should produce at least one token", tokens.isNotEmpty())
        engine.close()
    }
}
