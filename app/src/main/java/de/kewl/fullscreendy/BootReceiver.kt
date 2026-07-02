package de.kewl.fullscreendy

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import de.kewl.fullscreendy.data.SettingsRepository
import de.kewl.fullscreendy.service.KioskService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Startet Dienst und Kiosk-Activity nach dem Booten – sofern in den Einstellungen
 * "Beim Booten starten" aktiv ist. Der zuverlässigste Weg für einen echten Kiosk
 * ist, die App zusätzlich als Standard-Home-App zu setzen.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action != Intent.ACTION_BOOT_COMPLETED &&
            action != "android.intent.action.QUICKBOOT_POWERON"
        ) return

        val pending = goAsync()
        val appContext = context.applicationContext
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val settings = SettingsRepository(appContext).settings.first()
                if (!settings.startOnBoot) return@launch
                withContext(Dispatchers.Main) {
                    ContextCompat.startForegroundService(
                        appContext, Intent(appContext, KioskService::class.java)
                    )
                    runCatching {
                        appContext.startActivity(
                            Intent(appContext, MainActivity::class.java)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    }
                }
            } finally {
                pending.finish()
            }
        }
    }
}
