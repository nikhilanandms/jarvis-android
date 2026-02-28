package com.jarvis.android.engine

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertNotNull
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class WhisperEngineTest {

    @Test
    fun transcribeSilenceDoesNotCrash() = runTest {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val modelPath = context.getExternalFilesDir(null)?.absolutePath + "/ggml-tiny.en.bin"
        assumeTrue("Whisper model must exist at $modelPath", File(modelPath).exists())

        val engine = WhisperEngine(modelPath)
        // 1 second of silence at 16kHz — should return empty string, not crash
        val silentPcm = FloatArray(16000) { 0f }
        val result = engine.transcribe(silentPcm)
        assertNotNull(result)
        engine.release()
    }
}
