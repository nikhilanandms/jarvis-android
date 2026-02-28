package com.jarvis.android.engine

import android.content.Context
import android.util.Log
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.atomic.AtomicReference

class LLMEngine(context: Context, modelPath: String) {
    private val activeChannel = AtomicReference<Channel<String>?>(null)
    private val mutex = Mutex()

    init {
        Log.d("LLMEngine", "Initialising model from: $modelPath")
    }

    private val inference: LlmInference = LlmInference.createFromOptions(
        context,
        LlmInference.LlmInferenceOptions.builder()
            .setModelPath(modelPath)
            .setMaxTokens(1024)
            .setTopK(40)
            .setTemperature(0.7f)
            .setRandomSeed(42)
            .setResultListener { partial, done ->
                Log.d("LLMEngine", "resultListener partial='$partial' done=$done")
                activeChannel.get()?.let { ch ->
                    if (!partial.isNullOrEmpty()) ch.trySendBlocking(partial)
                    if (done) ch.close()
                }
            }
            .setErrorListener { e ->
                Log.e("LLMEngine", "Error from MediaPipe", e)
                activeChannel.get()?.close(e)
            }
            .build()
    ).also { Log.d("LLMEngine", "Model loaded successfully") }

    fun generate(prompt: String): Flow<String> = channelFlow {
        mutex.withLock {
            Log.d("LLMEngine", "generate() called, prompt length=${prompt.length}")
            val ch = Channel<String>(Channel.UNLIMITED)
            activeChannel.set(ch)
            try {
                inference.generateResponseAsync(prompt)
                Log.d("LLMEngine", "generateResponseAsync dispatched, waiting for tokens...")
                for (token in ch) send(token)
                Log.d("LLMEngine", "generate() complete")
            } catch (e: Exception) {
                Log.e("LLMEngine", "Exception in generate()", e)
            } finally {
                activeChannel.set(null)
                ch.cancel()
            }
        }
    }

    fun close() = inference.close()
}
