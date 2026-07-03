package de.kewl.fullscreendy

import android.app.admin.DeviceAdminReceiver

/**
 * Geräteadministrator – nötig, damit die App den Bildschirm per MQTT sperren kann
 * (DevicePolicyManager.lockNow). Muss vom Nutzer einmalig aktiviert werden.
 */
class AdminReceiver : DeviceAdminReceiver()
