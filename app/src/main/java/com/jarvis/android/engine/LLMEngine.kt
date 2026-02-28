package com.jarvis.android.engine

import android.content.Context
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import java.util.concurrent.atomic.AtomicReference

/**
 * MediaPipe Gemma LLM engine with streaming token output.
 *
 * The result listener is configured once at creation. Each call to [generate]
 * wires the listener to a fresh Channel, collects tokens as a Flow, then
 * closes the Channel when generation finishes.
 */
class LLMEngine(context: Context, modelPath: String) {
    // Holds the active channel for the current generate() call.
    private val activeChannel = AtomicReference<Channel<String>?>(null)

    private val inference: LlmInference = LlmInference.createFromOptions(
        context,
        LlmInference.LlmInferenceOptions.builder()
            .setModelPath(modelPath)
            .setMaxTokens(1024)
            .setTopK(40)
            .setTemperature(0.7f)
            .setRandomSeed(42)
            .setResultListener { partial, done ->
                activeChannel.get()?.let { ch ->
                    if (partial != null) ch.trySendBlocking(partial)
                    if (done) ch.close()
                }
            }
            .setErrorListener { e ->
                activeChannel.get()?.close(e)
            }
            .build()
    )

    /**
     * Generate a response for [prompt], streaming tokens as they are produced.
     * Collect the returned Flow to receive each token string.
     */
    fun generate(prompt: String): Flow<String> = channelFlow {
        val ch = Channel<String>(Channel.UNLIMITED)
        activeChannel.set(ch)
        try {
            inference.generateResponseAsync(prompt)
            for (token in ch) send(token)
        } finally {
            activeChannel.set(null)
            ch.cancel()
        }
    }

    fun close() = inference.close()
}
