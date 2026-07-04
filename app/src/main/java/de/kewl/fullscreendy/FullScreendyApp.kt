package de.kewl.fullscreendy

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context

class FullScreendyApp : Application() {

    override fun onCreate() {
        super.onCreate()
        val channel = NotificationChannel(
            NOTIF_CHANNEL_ID,
            getString(R.string.notif_channel_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = getString(R.string.notif_channel_desc)
            setShowBadge(false)
        }
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(channel)
    }

    companion object {
        const val NOTIF_CHANNEL_ID = "kiosk_service"
        const val NOTIF_ID = 42
    }
}
