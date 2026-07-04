package de.kewl.fullscreendy.device

import android.content.Context
import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import kotlin.math.abs

/**
 * Bewegungserkennung über die Frontkamera per CameraX. Verglichen wird die
 * **mittlere absolute Differenz pro Pixel** aufeinanderfolgender Frames – so wird
 * auch lokale Bewegung (Person bewegt sich) erkannt, nicht nur globale
 * Helligkeitsänderung. Kein Bild verlässt das Gerät.
 *
 * - [onMotionChanged] meldet die Flanken aktiv/inaktiv (für das MQTT-Reading).
 * - [onMotionPulse] feuert bei JEDER erkannten Bewegung (gedrosselt) – damit
 *   dauerhafte Bewegung den Abdunkel-Timer immer wieder zurücksetzt und weckt.
 */
class MotionDetector(
    private val context: Context,
    sensitivity: Int,
    private val onMotionChanged: (active: Boolean) -> Unit,
    private val onMotionPulse: () -> Unit,
) {
    // Empfindlichkeit 0..100 → Schwelle 8.0 (unempfindlich) .. 1.0 (sehr empfindlich),
    // bezogen auf die mittlere Pro-Pixel-Differenz (0..255).
    private val threshold = (8.0 - sensitivity.coerceIn(0, 100) * 0.07).coerceIn(1.0, 8.0)

    private val analysisExecutor = Executors.newSingleThreadExecutor()
    private val timer: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor()
    private var provider: ProcessCameraProvider? = null

    @Volatile private var previous: IntArray? = null
    @Volatile private var lastMotionAt: Long = 0L
    @Volatile private var lastPulseAt: Long = 0L
    @Volatile private var active: Boolean = false

    fun start(lifecycleOwner: LifecycleOwner) {
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({
            val cameraProvider = runCatching { future.get() }.getOrNull() ?: return@addListener
            provider = cameraProvider

            // Ohne (Front-)Kamera nicht binden – vermeidet endlose Fehler-Retries.
            val hasFront = cameraProvider.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA)
            val hasBack = cameraProvider.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA)
            if (!hasFront && !hasBack) {
                Log.w(TAG, "Keine Kamera verfügbar – Bewegungserkennung aus")
                return@addListener
            }
            val selector = if (hasFront) CameraSelector.DEFAULT_FRONT_CAMERA
            else CameraSelector.DEFAULT_BACK_CAMERA

            val analysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also { it.setAnalyzer(analysisExecutor, ::analyze) }

            runCatching {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(lifecycleOwner, selector, analysis)
            }.onFailure { Log.e(TAG, "Kamera-Bind fehlgeschlagen", it) }
        }, ContextCompat.getMainExecutor(context))

        // Prüft periodisch, ob die Bewegung wieder abgeklungen ist.
        timer.scheduleWithFixedDelay({
            if (active && System.currentTimeMillis() - lastMotionAt > QUIET_MILLIS) {
                active = false
                onMotionChanged(false)
            }
        }, 1, 1, TimeUnit.SECONDS)
    }

    private fun analyze(image: ImageProxy) {
        try {
            val current = sample(image)
            val prev = previous
            if (prev != null && prev.size == current.size) {
                var sum = 0L
                var i = 0
                while (i < current.size) {
                    sum += abs(current[i] - prev[i])
                    i++
                }
                val meanDiff = sum.toDouble() / current.size
                if (meanDiff > threshold) onMotion()
            }
            previous = current
        } finally {
            image.close()
        }
    }

    private fun onMotion() {
        val now = System.currentTimeMillis()
        lastMotionAt = now
        if (!active) {
            active = true
            onMotionChanged(true)
        }
        // Impuls gedrosselt, damit dauerhafte Bewegung regelmäßig (aber nicht in
        // jedem Frame) weckt und den Abdunkel-Timer zurücksetzt.
        if (now - lastPulseAt > PULSE_THROTTLE_MS) {
            lastPulseAt = now
            onMotionPulse()
        }
    }

    /** Stichprobe der Y-Ebene (Helligkeit) als IntArray, für den Pro-Pixel-Vergleich. */
    private fun sample(image: ImageProxy): IntArray {
        val buffer = image.planes[0].buffer
        buffer.rewind()
        val size = buffer.remaining()
        val n = size / SAMPLE_STEP
        val out = IntArray(n)
        var idx = 0
        var i = 0
        while (idx < n) {
            out[idx] = buffer.get(i).toInt() and 0xFF
            idx++
            i += SAMPLE_STEP
        }
        return out
    }

    fun stop() {
        runCatching { provider?.unbindAll() }
        provider = null
        previous = null
        active = false
        timer.shutdownNow()
        analysisExecutor.shutdown()
    }

    companion object {
        private const val TAG = "MotionDetector"
        /** Nach so vielen ms ohne Bewegung gilt der Zustand als "inaktiv". */
        private const val QUIET_MILLIS = 8_000L
        /** Frühestens alle so viele ms einen Weck-Impuls senden. */
        private const val PULSE_THROTTLE_MS = 700L
        /** Nur jedes n-te Byte auswerten (Performance). */
        private const val SAMPLE_STEP = 16
    }
}
