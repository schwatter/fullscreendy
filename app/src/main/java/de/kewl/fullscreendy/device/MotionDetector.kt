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
 * Bewegungserkennung über die (Front-)Kamera per CameraX. Es wird die
 * mittlere Helligkeit (Luminanz) aufeinanderfolgender Frames verglichen –
 * kein Bild verlässt das Gerät. Meldet Zustandswechsel "Bewegung aktiv/inaktiv".
 */
class MotionDetector(
    private val context: Context,
    sensitivity: Int,
    private val onMotionChanged: (active: Boolean) -> Unit,
) {
    // Empfindlichkeit 0..100 → Schwelle 8.0 (unempfindlich) .. 0.5 (sehr empfindlich).
    private val threshold = (8.0 - sensitivity.coerceIn(0, 100) * 0.075).coerceIn(0.5, 8.0)

    private val analysisExecutor = Executors.newSingleThreadExecutor()
    private val timer: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor()
    private var provider: ProcessCameraProvider? = null

    @Volatile private var previousLuma: Double = -1.0
    @Volatile private var lastMotionAt: Long = 0L
    @Volatile private var active: Boolean = false

    fun start(lifecycleOwner: LifecycleOwner) {
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({
            val cameraProvider = runCatching { future.get() }.getOrNull() ?: return@addListener
            provider = cameraProvider

            val analysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also { it.setAnalyzer(analysisExecutor, ::analyze) }

            val selector = if (cameraProvider.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA))
                CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA

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
            val luma = averageLuma(image)
            if (previousLuma >= 0) {
                val diff = abs(luma - previousLuma)
                if (diff > threshold) {
                    lastMotionAt = System.currentTimeMillis()
                    if (!active) {
                        active = true
                        onMotionChanged(true)
                    }
                }
            }
            previousLuma = luma
        } finally {
            image.close()
        }
    }

    /** Mittelwert über die Y-Ebene (Helligkeit), mit Sub-Sampling für Effizienz. */
    private fun averageLuma(image: ImageProxy): Double {
        val plane = image.planes[0]
        val buffer = plane.buffer
        buffer.rewind()
        var sum = 0L
        var count = 0
        val size = buffer.remaining()
        var i = 0
        while (i < size) {
            sum += (buffer.get(i).toInt() and 0xFF)
            count++
            i += SAMPLE_STEP
        }
        return if (count > 0) sum.toDouble() / count else 0.0
    }

    fun stop() {
        runCatching { provider?.unbindAll() }
        provider = null
        previousLuma = -1.0
        active = false
        timer.shutdownNow()
        analysisExecutor.shutdown()
    }

    companion object {
        private const val TAG = "MotionDetector"
        /** Nach so vielen ms ohne Bewegung gilt der Zustand als "inaktiv". */
        private const val QUIET_MILLIS = 15_000L
        /** Nur jedes n-te Byte auswerten (Performance). */
        private const val SAMPLE_STEP = 16
    }
}
