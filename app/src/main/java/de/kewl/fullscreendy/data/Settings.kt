package de.kewl.fullscreendy.data

/**
 * Alle vom Nutzer konfigurierbaren Einstellungen der Kiosk-App.
 */
data class Settings(
    val dashboardUrl: String = "",
    val mqttHost: String = "",
    val mqttPort: Int = 1883,
    val mqttTls: Boolean = false,
    val mqttUser: String = "",
    val mqttPass: String = "",
    val baseTopic: String = "fhem/tablet",
    val deviceId: String = "tablet1",
    val motionEnabled: Boolean = true,
    val motionWakesScreen: Boolean = true,
    val keepScreenOn: Boolean = true,
    val ignoreSystemFontScale: Boolean = true,
    val ttsEnabled: Boolean = true,
    val mediaEnabled: Boolean = true,
    val zoomEnabled: Boolean = false,
    val pullToRefresh: Boolean = true,
    val startOnBoot: Boolean = true,
    /** UI-Sprache: "en" (Standard) oder "de". */
    val language: String = "en",
    val adminPin: String = "0000",
) {
    val isConfigured: Boolean
        get() = dashboardUrl.isNotBlank()

    /** Basis-Topic für dieses Gerät, z. B. "fhem/tablet/tablet1". */
    val deviceTopic: String
        get() = "${baseTopic.trimEnd('/')}/$deviceId"
}
