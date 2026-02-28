package com.jarvis.android.engine

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.time.Duration.Companion.seconds

@RunWith(AndroidJUnit4::class)
class TTSEngineTest {

    @Test
    fun speakAndStopDoesNotCrash() = runTest(timeout = 10.seconds) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val engine = TTSEngine(context)
        engine.awaitReady()
        engine.speak("Hello from Jarvis")
        engine.stop()
        engine.release()
    }

    @Test
    fun blankTextIsIgnored() = runTest(timeout = 10.seconds) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val engine = TTSEngine(context)
        engine.awaitReady()
        engine.speak("   ")   // should not crash or queue anything
        engine.release()
    }
}
