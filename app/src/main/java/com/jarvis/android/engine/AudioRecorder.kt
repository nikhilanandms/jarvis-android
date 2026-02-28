package com.jarvis.android.engine

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn

/**
 * Streams raw 16kHz mono float32 PCM audio from the microphone as a [Flow].
 *
 * Each emitted [FloatArray] is exactly [CHUNK_SIZE] (512) samples — 32ms of audio —
 * matching the Silero VAD requirement.
 *
 * Usage:
 *   val job = scope.launch {
 *       audioRecorder.record().collect { chunk -> vadEngine.isSpeech(chunk) }
 *   }
 *   job.cancel()  // stops recording
 */
class AudioRecorder {
    companion object {
        const val SAMPLE_RATE = 16000
        const val CHUNK_SIZE = 512   // must match VADEngine.CHUNK_SIZE
        private val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        private val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_FLOAT
    }

    private val minBufferSize = AudioRecord.getMinBufferSize(
        SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT
    ).coerceAtLeast(CHUNK_SIZE * 4 * Float.SIZE_BYTES)

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
        val chunk = FloatArray(CHUNK_SIZE)
        try {
            while (true) {
                val read = recorder.read(chunk, 0, CHUNK_SIZE, AudioRecord.READ_BLOCKING)
                if (read > 0) emit(chunk.copyOf())
            }
        } finally {
            recorder.stop()
            recorder.release()
        }
    }.flowOn(Dispatchers.IO)
}
