package com.jarvis.android.engine

import android.content.Context
import android.util.Log
import kotlin.math.sqrt

/**
 * Voice Activity Detection using RMS energy.
 *
 * The Silero VAD ONNX model returns ~0.001 on all inputs on certain Android
 * hardware (tested S24 Ultra) due to a tensor format incompatibility with
 * ONNX Runtime. Energy-based VAD is simpler, universal, and reliable enough
 * for a voice assistant use case.
 *
 * Tuned thresholds based on observed S24 Ultra microphone levels:
 *   - Silence/ambient:  RMS < 0.01
 *   - Normal speech:    RMS 0.03 – 0.15
 *   - Loud speech:      RMS > 0.15
 */
class VADEngine(context: Context) {
    companion object {
        const val CHUNK_SIZE = 512
        const val SAMPLE_RATE = 16000L
        private const val TAG = "VADEngine"

        // RMS threshold — speech detected when RMS exceeds this
        private const val RMS_THRESHOLD = 0.02f
    }

    private var callCount = 0

    // Smoothing: keep a running average to reduce noise spikes
    private val windowSize = 3
    private val recentRms = ArrayDeque<Float>(windowSize)

    fun isSpeech(chunk: FloatArray, threshold: Float = 0.5f): Boolean {
        // Compute RMS energy of the chunk
        val sumSq = chunk.sumOf { (it * it).toDouble() }
        val rms = sqrt(sumSq / chunk.size).toFloat()

        // Smooth over last N chunks
        recentRms.addLast(rms)
        if (recentRms.size > windowSize) recentRms.removeFirst()
        val smoothedRms = recentRms.average().toFloat()

        val speech = smoothedRms >= RMS_THRESHOLD

        callCount++
        if (callCount % 50 == 0 || speech) {
            Log.d(TAG, "rms=${"%.4f".format(rms)} smoothed=${"%.4f".format(smoothedRms)} speech=$speech")
        }

        return speech
    }

    fun reset() {
        recentRms.clear()
    }

    // No-op — no model to release
    fun release() {}
}
