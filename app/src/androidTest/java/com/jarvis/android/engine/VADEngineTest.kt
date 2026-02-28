package com.jarvis.android.engine

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.sin

@RunWith(AndroidJUnit4::class)
class VADEngineTest {

    @Test
    fun silenceReturnsFalse() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val engine = VADEngine(context)
        val silent = FloatArray(VADEngine.CHUNK_SIZE) { 0f }
        val result = engine.isSpeech(silent)
        assertFalse("Pure silence should not be detected as speech", result)
        engine.release()
    }

    @Test
    fun resetClearsState() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val engine = VADEngine(context)
        // Run a few chunks then reset — should not throw
        val chunk = FloatArray(VADEngine.CHUNK_SIZE) { 0f }
        repeat(5) { engine.isSpeech(chunk) }
        engine.reset()
        engine.isSpeech(chunk) // should work fine after reset
        engine.release()
    }
}
