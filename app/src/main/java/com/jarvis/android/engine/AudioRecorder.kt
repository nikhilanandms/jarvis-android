package com.jarvis.android.engine

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn

/**
 * Streams 16kHz mono float32 PCM audio from the microphone as a [Flow].
 *
 * Uses PCM_16BIT (universally supported on all Android hardware) and converts
 * to float32 in-place. ENCODING_PCM_FLOAT is unreliable on some Samsung devices
 * and can return zeros silently.
 *
 * Each emitted [FloatArray] is exactly [CHUNK_SIZE] (512) samples — 32ms —
 * matching the Silero VAD requirement.
 */
class AudioRecorder {
    companion object {
        const val SAMPLE_RATE = 16000
        const val CHUNK_SIZE = 512   // must match VADEngine.CHUNK_SIZE
        private const val TAG = "AudioRecorder"
        private val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        // PCM_16BIT: universally supported on all devices
        private val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
    }

    private val minBufferSize = AudioRecord.getMinBufferSize(
        SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT
    ).coerceAtLeast(CHUNK_SIZE * 2 * 4)  // 2 bytes/sample, at least 4 chunks

    fun record(): Flow<FloatArray> = flow {
        val recorder = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            SAMPLE_RATE,
            CHANNEL_CONFIG,
            AUDIO_FORMAT,
            minBufferSize
        )
        check(recorder.state == AudioRecord.STATE_INITIALIZED) {
            "AudioRecord failed to initialise — check RECORD_AUDIO permission"
        }
        recorder.startRecording()
        Log.d(TAG, "Recording started, bufferSize=$minBufferSize")

        val shortBuf = ShortArray(CHUNK_SIZE)
        val floatBuf = FloatArray(CHUNK_SIZE)
        try {
            while (true) {
                val read = recorder.read(shortBuf, 0, CHUNK_SIZE)
                if (read > 0) {
                    // Convert 16-bit PCM to float32 in range [-1.0, 1.0]
                    for (i in 0 until read) {
                        floatBuf[i] = shortBuf[i] / 32768f
                    }
                    emit(floatBuf.copyOf())
                }
            }
        } finally {
            recorder.stop()
            recorder.release()
            Log.d(TAG, "Recording stopped")
        }
    }.flowOn(Dispatchers.IO)
}
