package de.kewl.fullscreendy.i18n

import androidx.compose.runtime.staticCompositionLocalOf

enum class AppLang {
    EN, DE;

    companion object {
        fun from(code: String): AppLang = if (code.equals("de", ignoreCase = true)) DE else EN
    }
}

/**
 * Zentrale, zweisprachige UI-Texte. Standard ist Englisch; Deutsch per [AppLang.DE].
 * Bewusst kein Android-Ressourcen-Locale, damit die Sprache in der App umschaltbar
 * ist, unabhängig von der Systemsprache.
 */
class Strings(private val lang: AppLang) {
    private fun t(en: String, de: String) = if (lang == AppLang.DE) de else en

    // Menü / Drawer
    val version get() = t("Version", "Version")
    val statusConnected get() = t("MQTT connected", "MQTT verbunden")
    val statusDisconnected get() = t("MQTT disconnected", "MQTT getrennt")
    val navDashboard get() = t("Dashboard", "Dashboard")
    val navReload get() = t("Reload", "Neu laden")
    val navClearCache get() = t("Clear cache", "Cache leeren")
    val navScreenOff get() = t("Screen off", "Bildschirm aus")
    val navSettings get() = t("Settings", "Einstellungen")
    val navHelp get() = t("Help", "Hilfe")
    val navAbout get() = t("About", "Über")

    // Allgemein
    val save get() = t("Save", "Speichern")
    val back get() = t("Back", "Zurück")
    val cancel get() = t("Cancel", "Abbrechen")
    val ok get() = t("OK", "OK")

    // Einstellungen – Abschnitte
    val settings get() = t("Settings", "Einstellungen")
    val secConnection get() = t("Connection", "Verbindung")
    val secConnectionDesc get() = t("Dashboard URL, MQTT broker, topics", "Dashboard-URL, MQTT-Broker, Topics")
    val secDisplay get() = t("Display", "Anzeige")
    val secDisplayDesc get() = t("Zoom, font size, screen", "Zoom, Schriftgröße, Bildschirm")
    val secBehavior get() = t("Behavior", "Verhalten")
    val secBehaviorDesc get() = t("Motion, speech, sound, refresh", "Bewegung, Sprache, Ton, Aktualisieren")
    val secSounds get() = t("Sounds", "Töne")
    val secSoundsDesc get() = t("Sound folder", "Sound-Ordner")
    val secSystem get() = t("System", "System")
    val secSystemDesc get() = t("Language, autostart, PIN", "Sprache, Autostart, PIN")

    // Verbindung
    val dashboardUrl get() = t("Dashboard URL (http/https)", "Dashboard-URL (http/https)")
    val mqttBroker get() = t("MQTT broker", "MQTT-Broker")
    val host get() = t("Host / IP", "Host / IP")
    val port get() = t("Port", "Port")
    val useTls get() = t("Use TLS/SSL", "TLS/SSL verwenden")
    val username get() = t("User (optional)", "Benutzer (optional)")
    val password get() = t("Password (optional)", "Passwort (optional)")
    val topics get() = t("Topics", "Topics")
    val baseTopic get() = t("Base topic", "Basis-Topic")
    val deviceId get() = t("Device ID", "Geräte-ID")

    // Anzeige
    val ignoreFontScale get() = t("Font independent of system zoom", "Schrift unabhängig von System-Zoom")
    val allowZoom get() = t("Allow pinch-zoom in dashboard", "Zoomen im Dashboard erlauben")
    val keepScreenOn get() = t("Keep screen always on", "Bildschirm immer an")

    // Verhalten
    val motionDetection get() = t("Motion detection (camera)", "Bewegungserkennung (Kamera)")
    val motionWakesScreen get() = t("Motion wakes the screen", "Bewegung weckt Bildschirm")
    val motionSensitivity get() = t("Motion sensitivity", "Bewegungs-Empfindlichkeit")
    val soundWake get() = t("Wake on sound (microphone)", "Wecken bei Ton (Mikrofon)")
    val soundSensitivity get() = t("Sound sensitivity", "Ton-Empfindlichkeit")
    val pullToRefresh get() = t("Pull down to reload", "Zum Aktualisieren nach unten ziehen")
    val ttsEnabled get() = t("Text-to-speech enabled", "Text-to-Speech aktiv")
    val mediaEnabled get() = t("Sound playback enabled", "Tonwiedergabe aktiv")

    // Töne
    val soundsHint get() = t(
        "Copy sound files into this folder (grant 'Allow file access' under System first), " +
            "then play via MQTT (e.g. cmd/mediaplay = ding.mp3):",
        "Tondateien in diesen Ordner kopieren (vorher unter System 'Dateizugriff erlauben'), " +
            "dann per MQTT abspielen (z. B. cmd/mediaplay = ding.mp3):"
    )

    // System
    val language get() = t("Language", "Sprache")
    val languageEnglish get() = t("English", "Englisch")
    val languageGerman get() = t("German", "Deutsch")
    val startOnBoot get() = t("Start on boot", "Beim Booten starten")
    val adminPin get() = t("Admin PIN (access to settings)", "Admin-PIN (Zugang zu Einstellungen)")
    val permissionsTitle get() = t("Permissions", "Berechtigungen")
    val enableDeviceAdmin get() = t("Enable device admin (screen lock)", "Geräteadmin aktivieren (Sperren)")
    val allowBrightness get() = t("Allow brightness control", "Helligkeitssteuerung erlauben")
    val allowFileAccess get() = t("Allow file access (sounds)", "Dateizugriff erlauben (Töne)")
    val setAsHomeApp get() = t("Set as Home app (autostart)", "Als Home-App festlegen (Autostart)")

    // Hilfe / Über
    val help get() = t("Help", "Hilfe")
    val about get() = t("About", "Über")
    val appVersionLabel get() = t("App version", "App-Version")
    val androidVersionLabel get() = t("Android version", "Android-Version")
    val ipAddressLabel get() = t("IP address", "IP-Adresse")
    val urlLabel get() = t("Dashboard URL", "Dashboard-URL")
    val deviceIdLabel get() = t("Device ID", "Geräte-ID")

    // PIN
    val pinTitle get() = t("Admin PIN", "Admin-PIN")
    val pinEnter get() = t("Enter PIN", "PIN eingeben")
    val pinWrong get() = t("Wrong PIN", "Falsche PIN")
}

val LocalStrings = staticCompositionLocalOf { Strings(AppLang.EN) }
