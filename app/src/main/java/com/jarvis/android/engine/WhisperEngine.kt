package com.jarvis.android.engine

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class WhisperEngine(modelPath: String) {
    private val nativeCtx: Long

    init {
        System.loadLibrary("whisper_jni")
        nativeCtx = nativeInit(modelPath)
        require(nativeCtx != 0L) { "Failed to initialize Whisper model from $modelPath" }
    }

    /**
     * Transcribe PCM audio (16kHz, mono, float32) to text.
     * Runs on IO dispatcher — safe to call from a coroutine.
     */
    suspend fun transcribe(pcm: FloatArray): String = withContext(Dispatchers.IO) {
        nativeTranscribe(nativeCtx, pcm).trim()
    }

    fun release() = nativeRelease(nativeCtx)

    private external fun nativeInit(modelPath: String): Long
    private external fun nativeTranscribe(ctxPtr: Long, pcm: FloatArray): String
    private external fun nativeRelease(ctxPtr: Long)
}
