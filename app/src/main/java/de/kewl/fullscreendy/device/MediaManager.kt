package de.kewl.fullscreendy.device

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.Environment
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

    /**
     * Öffentlicher Ordner, in den Tondateien kopiert werden: /sdcard/FullScreendy.
     * Über Dateimanager/USB erreichbar (benötigt Datei-Zugriff-Berechtigung).
     * Fällt auf den app-eigenen Ordner zurück, falls der öffentliche nicht anlegbar ist.
     */
    @Suppress("DEPRECATION")
    val soundsDir: File
        get() {
            val public = File(Environment.getExternalStorageDirectory(), PUBLIC_DIR)
            if (runCatching { public.mkdirs(); public.isDirectory }.getOrDefault(false) ||
                public.isDirectory
            ) {
                return public
            }
            return (context.getExternalFilesDir("sounds") ?: File(context.filesDir, "sounds"))
                .also { it.mkdirs() }
        }

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
        private const val PUBLIC_DIR = "FullScreendy"
    }
}
