package com.jarvis.android.engine

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume

/**
 * Android TextToSpeech wrapper with:
 *  - Sentence-by-sentence queuing via [speak]
 *  - Immediate [stop] for barge-in
 *  - [awaitReady] suspend fun to wait for TTS engine initialisation
 */
class TTSEngine(context: Context) {
    private val tts: TextToSpeech
    private val ready = AtomicBoolean(false)
    private var onReadyCallback: (() -> Unit)? = null

    init {
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts.language = Locale.US
                ready.set(true)
                onReadyCallback?.invoke()
            }
        }
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {}
            override fun onDone(utteranceId: String?) {}
            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {}
        })
    }

    /** Speak [text], queuing after any currently playing utterance. */
    fun speak(text: String) {
        if (!ready.get() || text.isBlank()) return
        tts.speak(text, TextToSpeech.QUEUE_ADD, null, "jarvis_${System.currentTimeMillis()}")
    }

    /** Immediately stop all speech — used for barge-in. */
    fun stop() = tts.stop()

    fun isSpeaking(): Boolean = tts.isSpeaking

    /**
     * Suspend until the TTS engine is initialised.
     * Returns immediately if already ready.
     */
    suspend fun awaitReady() {
        if (ready.get()) return
        suspendCancellableCoroutine { cont ->
            onReadyCallback = { cont.resume(Unit) }
        }
    }

    fun release() = tts.shutdown()
}
