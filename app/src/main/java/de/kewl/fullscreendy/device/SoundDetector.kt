package de.kewl.fullscreendy.device

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import kotlin.math.abs

/**
 * Erkennt lauten Umgebungsschall über das Mikrofon (AudioRecord) und meldet ihn
 * per Callback – z. B. um den Bildschirm zu wecken. Es wird nur die Lautstärke
 * (Spitzenamplitude) ausgewertet, keine Aufnahme gespeichert.
 *
 * @param sensitivity 0..100 – höher = empfindlicher (niedrigere Schwelle).
 */
class SoundDetector(
    sensitivity: Int,
    private val onSound: () -> Unit,
) {
    private val threshold = (20000 - sensitivity.coerceIn(0, 100) * 194).coerceIn(400, 30000)
    private var recorder: AudioRecord? = null
    private var thread: Thread? = null
    @Volatile private var running = false
    @Volatile private var lastTrigger = 0L

    @SuppressLint("MissingPermission") // Aufrufer prüft RECORD_AUDIO
    fun start() {
        if (running) return
        val minBuf = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL, ENCODING)
        if (minBuf <= 0) return
        val bufSize = maxOf(minBuf, BUFFER)
        val rec = runCatching {
            AudioRecord(MediaRecorder.AudioSource.MIC, SAMPLE_RATE, CHANNEL, ENCODING, bufSize)
        }.getOrNull() ?: return
        if (rec.state != AudioRecord.STATE_INITIALIZED) {
            rec.release(); return
        }
        recorder = rec
        running = true
        runCatching { rec.startRecording() }
        thread = Thread { loop(rec, bufSize) }.apply { isDaemon = true; start() }
    }

    private fun loop(rec: AudioRecord, bufSize: Int) {
        val buf = ShortArray(bufSize)
        while (running) {
            val n = runCatching { rec.read(buf, 0, buf.size) }.getOrDefault(0)
            if (n <= 0) continue
            var peak = 0
            var i = 0
            while (i < n) {
                val a = abs(buf[i].toInt())
                if (a > peak) peak = a
                i++
            }
            val now = System.currentTimeMillis()
            if (peak > threshold && now - lastTrigger > DEBOUNCE_MS) {
                lastTrigger = now
                onSound()
            }
        }
    }

    fun stop() {
        running = false
        recorder?.let { r ->
            runCatching { r.stop() }
            runCatching { r.release() }
        }
        recorder = null
        thread = null
    }

    companion object {
        private const val TAG = "SoundDetector"
        private const val SAMPLE_RATE = 16000
        private const val CHANNEL = AudioFormat.CHANNEL_IN_MONO
        private const val ENCODING = AudioFormat.ENCODING_PCM_16BIT
        private const val BUFFER = 4096
        private const val DEBOUNCE_MS = 3000L
    }
}
