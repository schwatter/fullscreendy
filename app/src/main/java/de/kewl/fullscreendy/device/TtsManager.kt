package de.kewl.fullscreendy.device

import android.content.Context
import android.speech.tts.TextToSpeech
import android.util.Log
import java.util.Locale
import java.util.UUID

/**
 * Kapselt Android Text-to-Speech. Sprache standardmäßig Deutsch.
 * Ausgaben, die vor der Initialisierung ankommen, werden gepuffert.
 */
class TtsManager(context: Context) {

    private var tts: TextToSpeech? = null
    private var ready = false
    private val pending = ArrayDeque<String>()

    init {
        tts = TextToSpeech(context.applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale.GERMAN
                ready = true
                while (pending.isNotEmpty()) speak(pending.removeFirst())
            } else {
                Log.e(TAG, "TTS-Init fehlgeschlagen: $status")
            }
        }
    }

    fun speak(text: String) {
        if (text.isBlank()) return
        val engine = tts
        if (!ready || engine == null) {
            pending.addLast(text)
            return
        }
        engine.speak(text, TextToSpeech.QUEUE_ADD, null, UUID.randomUUID().toString())
    }

    fun stop() {
        tts?.stop()
    }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        ready = false
    }

    companion object {
        private const val TAG = "TtsManager"
    }
}
