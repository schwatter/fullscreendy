package de.kewl.fullscreendy.device

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager

data class BatteryState(
    val level: Int,          // 0..100
    val charging: Boolean,
    val plugged: String,     // "ac" | "usb" | "wireless" | "none"
    val temperatureC: Float, // Grad Celsius
)

/**
 * Beobachtet Akkuänderungen über [Intent.ACTION_BATTERY_CHANGED] und meldet sie
 * per Callback. Registrierung/Deregistrierung übernimmt der aufrufende Dienst.
 */
class BatteryMonitor(
    private val context: Context,
    private val onChange: (BatteryState) -> Unit,
) {
    private var receiver: BroadcastReceiver? = null

    fun start() {
        if (receiver != null) return
        val r = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                onChange(parse(intent))
            }
        }
        receiver = r
        // Registrierung liefert direkt den aktuellen Sticky-Intent zurück.
        val sticky = context.registerReceiver(r, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        sticky?.let { onChange(parse(it)) }
    }

    fun stop() {
        receiver?.let { runCatching { context.unregisterReceiver(it) } }
        receiver = null
    }

    private fun parse(intent: Intent): BatteryState {
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
        val percent = if (level >= 0 && scale > 0) (level * 100 / scale) else -1

        val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        val charging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
            status == BatteryManager.BATTERY_STATUS_FULL

        val plugged = when (intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0)) {
            BatteryManager.BATTERY_PLUGGED_AC -> "ac"
            BatteryManager.BATTERY_PLUGGED_USB -> "usb"
            BatteryManager.BATTERY_PLUGGED_WIRELESS -> "wireless"
            else -> "none"
        }

        val temp = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) / 10f

        return BatteryState(percent, charging, plugged, temp)
    }
}
