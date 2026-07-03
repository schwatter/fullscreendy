package de.kewl.fullscreendy.device

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import de.kewl.fullscreendy.AdminReceiver

/**
 * Systemnahe Aktionen: Bildschirm sperren (Geräteadmin) und echte Hardware-
 * Helligkeit setzen (Settings.System, benötigt WRITE_SETTINGS).
 */
object SystemController {

    private fun dpm(ctx: Context): DevicePolicyManager? =
        ctx.getSystemService(DevicePolicyManager::class.java)

    private fun admin(ctx: Context) = ComponentName(ctx, AdminReceiver::class.java)

    fun isAdminActive(ctx: Context): Boolean =
        dpm(ctx)?.isAdminActive(admin(ctx)) == true

    /** Sperrt sofort. Erfordert aktiven Geräteadministrator. */
    fun lock(ctx: Context): Boolean {
        val d = dpm(ctx) ?: return false
        if (!d.isAdminActive(admin(ctx))) return false
        return runCatching { d.lockNow() }.isSuccess
    }

    fun canWriteSettings(ctx: Context): Boolean = Settings.System.canWrite(ctx)

    /** Setzt die echte Geräte-Helligkeit (0..100 → 0..255). true bei Erfolg. */
    fun setSystemBrightness(ctx: Context, percent: Int): Boolean {
        if (!Settings.System.canWrite(ctx)) return false
        val value = percent.coerceIn(0, 100) * 255 / 100
        return runCatching {
            Settings.System.putInt(
                ctx.contentResolver,
                Settings.System.SCREEN_BRIGHTNESS_MODE,
                Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL
            )
            Settings.System.putInt(ctx.contentResolver, Settings.System.SCREEN_BRIGHTNESS, value)
        }.isSuccess
    }

    // ---- Intents, um die nötigen Berechtigungen zu erteilen --------------------

    fun writeSettingsIntent(ctx: Context): Intent =
        Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:${ctx.packageName}"))

    fun homeSettingsIntent(): Intent = Intent(Settings.ACTION_HOME_SETTINGS)

    fun deviceAdminIntent(ctx: Context): Intent =
        Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
            .putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, admin(ctx))
            .putExtra(
                DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                "Für 'Bildschirm sperren' per MQTT."
            )
}
