package com.jarvis.android.engine

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import java.nio.LongBuffer

/**
 * Silero VAD v5 engine using ONNX Runtime.
 *
 * Input schema:
 *   input  : [1, chunk_size] float32  — audio chunk (512 samples @ 16kHz = 32ms)
 *   state  : [2, 1, 128]    float32  — GRU hidden state (maintained between calls)
 *   sr     : []              int64    — sample rate (16000)
 *
 * Output schema:
 *   output : [1, 1]          float32  — speech probability [0..1]
 *   stateN : [2, 1, 128]    float32  — updated GRU state
 */
class VADEngine(context: Context) {
    companion object {
        const val CHUNK_SIZE = 512      // 32ms at 16kHz
        const val SAMPLE_RATE = 16000L
        private const val STATE_SIZE = 2 * 1 * 128  // [2, 1, 128]
    }

    private val env: OrtEnvironment = OrtEnvironment.getEnvironment()
    private val session: OrtSession

    // GRU state — reset at start of each listening session
    private var state = FloatArray(STATE_SIZE)

    init {
        val modelBytes = context.assets.open("silero_vad.onnx").use { it.readBytes() }
        session = env.createSession(modelBytes, OrtSession.SessionOptions())
    }

    /**
     * Returns true if the chunk contains speech.
     * @param chunk Exactly CHUNK_SIZE (512) float32 samples at 16kHz
     * @param threshold Speech probability threshold (0.5 is default)
     */
    fun isSpeech(chunk: FloatArray, threshold: Float = 0.5f): Boolean {
        require(chunk.size == CHUNK_SIZE) { "Chunk must be $CHUNK_SIZE samples, got ${chunk.size}" }

        val inputTensor = OnnxTensor.createTensor(env, arrayOf(chunk))
        val stateTensor = OnnxTensor.createTensor(env,
            arrayOf(
                arrayOf(state.copyOfRange(0, 128)),
                arrayOf(state.copyOfRange(128, 256))
            )
        )
        val srTensor = OnnxTensor.createTensor(env,
            LongBuffer.wrap(longArrayOf(SAMPLE_RATE)), longArrayOf(1)
        )

        val inputs = mapOf("input" to inputTensor, "state" to stateTensor, "sr" to srTensor)
        val results = session.run(inputs)

        // output shape: [1, 1] → float32
        val outputTensor = results.get("output").get() as OnnxTensor
        val prob = (outputTensor.getValue() as Array<*>)[0].let { (it as FloatArray)[0] }

        // stateN shape: [2, 1, 128] → update GRU state for next call
        val stateNTensor = results.get("stateN").get() as OnnxTensor
        val newState = stateNTensor.getValue() as Array<*>
        val row0 = ((newState[0] as Array<*>)[0] as FloatArray)
        val row1 = ((newState[1] as Array<*>)[0] as FloatArray)
        state = row0 + row1

        listOf(inputTensor, stateTensor, srTensor, outputTensor, stateNTensor).forEach { it.close() }
        results.close()

        return prob >= threshold
    }

    /** Reset GRU state — call at the start of each new listening session. */
    fun reset() {
        state = FloatArray(STATE_SIZE)
    }

    fun release() = session.close()
}
