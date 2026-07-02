package de.kewl.fullscreendy.device

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.util.Log
import java.io.File

/**
 * Spielt lokal gespeicherte Tondateien (oder Stream-URLs) im Hintergrund ab –
 * unabhängig davon, was die WebView gerade anzeigt.
 *
 * Payload-Auflösung:
 *  - beginnt mit "http"  → wird als Stream-URL abgespielt
 *  - beginnt mit "/"      → absoluter Dateipfad
 *  - sonst                → relativ zum Sound-Ordner [soundsDir]
 */
class MediaManager(private val context: Context) {

    private var player: MediaPlayer? = null

    /** Ordner, in den Tondateien kopiert werden: .../Android/data/<pkg>/files/sounds */
    val soundsDir: File
        get() = (context.getExternalFilesDir("sounds") ?: File(context.filesDir, "sounds"))
            .also { it.mkdirs() }

    fun play(spec: String) {
        val src = spec.trim()
        if (src.isEmpty()) return

        val source: String = when {
            src.startsWith("http", ignoreCase = true) -> src
            src.startsWith("/") -> src
            else -> File(soundsDir, src).absolutePath
        }

        if (!source.startsWith("http") && !File(source).exists()) {
            Log.w(TAG, "Tondatei nicht gefunden: $source")
            return
        }

        stop()
        runCatching {
            player = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                setDataSource(source)
                setOnCompletionListener { it.release(); if (player === it) player = null }
                setOnErrorListener { mp, what, extra ->
                    Log.w(TAG, "MediaPlayer-Fehler ($what/$extra)")
                    mp.release(); if (player === mp) player = null; true
                }
                setOnPreparedListener { it.start() }
                prepareAsync()
            }
        }.onFailure { Log.e(TAG, "Abspielen fehlgeschlagen: $source", it) }
    }

    fun stop() {
        player?.let { p ->
            runCatching { if (p.isPlaying) p.stop() }
            runCatching { p.release() }
        }
        player = null
    }

    companion object {
        private const val TAG = "MediaManager"
    }
}
