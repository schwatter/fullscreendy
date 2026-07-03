package de.kewl.fullscreendy.service

import android.Manifest
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import de.kewl.fullscreendy.FhemKioskApp
import de.kewl.fullscreendy.MainActivity
import de.kewl.fullscreendy.R
import de.kewl.fullscreendy.data.Settings
import de.kewl.fullscreendy.data.SettingsRepository
import de.kewl.fullscreendy.device.BatteryMonitor
import de.kewl.fullscreendy.device.BatteryState
import de.kewl.fullscreendy.device.DeviceInfo
import de.kewl.fullscreendy.device.MediaManager
import de.kewl.fullscreendy.device.MotionDetector
import de.kewl.fullscreendy.device.SoundDetector
import de.kewl.fullscreendy.device.SystemController
import de.kewl.fullscreendy.device.TtsManager
import de.kewl.fullscreendy.kiosk.KioskBus
import de.kewl.fullscreendy.kiosk.KioskCommand
import de.kewl.fullscreendy.kiosk.KioskStatus
import de.kewl.fullscreendy.mqtt.MqttConfig
import de.kewl.fullscreendy.mqtt.MqttManager
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/**
 * Dauerhaft laufender Dienst: hält die MQTT-Verbindung zu FHEM, meldet Akku- und
 * Bewegungsstatus und führt eingehende Befehle (TTS, Bildschirm, URL) aus.
 */
class KioskService : LifecycleService() {

    private lateinit var repo: SettingsRepository
    private lateinit var tts: TtsManager
    private lateinit var media: MediaManager

    private var mqtt: MqttManager? = null
    private var battery: BatteryMonitor? = null
    private var motion: MotionDetector? = null
    private var sound: SoundDetector? = null

    private var settings: Settings = Settings()
    private var lastBattery: BatteryState? = null
    private var screenOn: Boolean = true
    private var currentUrl: String = ""
    private var brightnessPercent: Int = -1 // -1 = auto

    override fun onCreate() {
        super.onCreate()
        repo = SettingsRepository(applicationContext)
        tts = TtsManager(applicationContext)
        media = MediaManager(applicationContext)
        updateForeground(camera = false, microphone = false)

        lifecycleScope.launch {
            repo.settings.distinctUntilChanged().collect { s ->
                settings = s
                applySettings(s)
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        // Von der Activity beim Fokus ausgelöst: Kamera/Mikrofon-Detektoren (neu)
        // starten, wenn die App im Vordergrund ist (im Hintergrund oft blockiert).
        if (intent?.action == ACTION_REFRESH) startDetectors(settings)
        return START_STICKY
    }

    override fun onBind(intent: Intent): IBinder? {
        super.onBind(intent)
        return null
    }

    // ---- Konfiguration anwenden -------------------------------------------------

    private fun applySettings(s: Settings) {
        currentUrl = s.dashboardUrl

        // Batteriemonitor (immer aktiv)
        if (battery == null) {
            battery = BatteryMonitor(applicationContext) { state -> onBattery(state) }.also { it.start() }
        }

        // Detektoren neu aufsetzen (damit u. a. die Empfindlichkeit greift).
        motion?.stop(); motion = null
        sound?.stop(); sound = null
        startDetectors(s)

        // MQTT neu verbinden
        connectMqtt(s)
    }

    private fun granted(perm: String) =
        ContextCompat.checkSelfPermission(this, perm) == PackageManager.PERMISSION_GRANTED

    /**
     * Startet Bewegungs-/Ton-Erkennung passend zu den Einstellungen. Wird sowohl
     * bei Einstellungsänderungen als auch beim App-Fokus (ACTION_REFRESH) aufgerufen;
     * bereits laufende Detektoren bleiben unangetastet. Kamera/Mikrofon im Hintergrund
     * zu starten kann fehlschlagen – daher runCatching (kein Absturz).
     */
    private fun startDetectors(s: Settings) {
        val wantMotion = s.motionEnabled && granted(Manifest.permission.CAMERA)
        val wantSound = s.soundWakeEnabled && granted(Manifest.permission.RECORD_AUDIO)

        updateForeground(camera = wantMotion, microphone = wantSound)

        if (wantMotion && motion == null) {
            runCatching {
                motion = MotionDetector(applicationContext, s.motionSensitivity) { active ->
                    onMotion(active)
                }.also { it.start(this) }
            }.onFailure { Log.w(TAG, "Bewegungserkennung nicht gestartet", it) }
        } else if (!wantMotion && motion != null) {
            motion?.stop(); motion = null
        }

        if (wantSound && sound == null) {
            runCatching {
                sound = SoundDetector(s.soundSensitivity) { onSoundWake() }.also { it.start() }
            }.onFailure { Log.w(TAG, "Tonerkennung nicht gestartet", it) }
        } else if (!wantSound && sound != null) {
            sound?.stop(); sound = null
        }
    }

    private fun connectMqtt(s: Settings) {
        mqtt?.disconnect()
        if (s.mqttHost.isBlank()) {
            mqtt = null
            return
        }
        val dt = s.deviceTopic
        val cfg = MqttConfig(
            host = s.mqttHost,
            port = s.mqttPort,
            tls = s.mqttTls,
            username = s.mqttUser,
            password = s.mqttPass,
            clientId = "fullscreendy-${s.deviceId}",
            commandTopic = "$dt/cmd/#",
            statusTopic = "$dt/status",
        )
        mqtt = MqttManager(
            onCommand = { topic, payload -> handleCommand(topic, payload) },
            onConnected = { onMqttConnected() },
            onConnectionChanged = { connected -> KioskStatus.setMqttConnected(connected) },
        ).also { it.connect(cfg) }
    }

    // ---- Telemetrie -------------------------------------------------------------

    private fun onMqttConnected() {
        val dt = settings.deviceTopic
        mqtt?.apply {
            publish("$dt/status", "online", retained = true, qos = 1)
            publish("$dt/appVersion", DeviceInfo.appVersion, retained = true)
            publish("$dt/androidVersion", DeviceInfo.androidVersion, retained = true)
            publish("$dt/ip", DeviceInfo.ipv4(), retained = true)
        }
        lastBattery?.let { publishBattery(it) }
        publishScreen(screenOn)
        publishUrl()
        publishBrightness()
    }

    private fun publishUrl() {
        mqtt?.publish("${settings.deviceTopic}/url", currentUrl, retained = true)
    }

    private fun publishBrightness() {
        val value = if (brightnessPercent < 0) "auto" else brightnessPercent.toString()
        mqtt?.publish("${settings.deviceTopic}/brightness", value, retained = true)
    }

    private fun onBattery(state: BatteryState) {
        lastBattery = state
        publishBattery(state)
    }

    private fun publishBattery(state: BatteryState) {
        val dt = settings.deviceTopic
        mqtt?.apply {
            publish("$dt/battery", state.level.toString(), retained = true)
            publish("$dt/charging", if (state.charging) "on" else "off", retained = true)
            publish("$dt/plug", state.plugged, retained = true)
            publish("$dt/batteryTemp", "%.1f".format(state.temperatureC), retained = true)
        }
    }

    private fun onMotion(active: Boolean) {
        val dt = settings.deviceTopic
        mqtt?.publish("$dt/motion", if (active) "on" else "off", retained = true)
        mqtt?.publish("$dt/presence", if (active) "present" else "absent", retained = true)
        if (active && settings.motionWakesScreen) {
            // Bei Bewegung Display physisch aufwecken und Overlay entfernen.
            wakeScreen()
            screenOn = true
            KioskBus.send(KioskCommand.Screen(on = true))
            publishScreen(true)
        }
    }

    private fun onSoundWake() {
        wakeScreen()
        screenOn = true
        KioskBus.send(KioskCommand.Screen(on = true))
        publishScreen(true)
    }

    @Suppress("DEPRECATION")
    private fun wakeScreen() {
        val pm = getSystemService(PowerManager::class.java) ?: return
        val wl = pm.newWakeLock(
            PowerManager.SCREEN_BRIGHT_WAKE_LOCK or
                PowerManager.ACQUIRE_CAUSES_WAKEUP or
                PowerManager.ON_AFTER_RELEASE,
            "fullscreendy:wake"
        )
        runCatching { wl.acquire(3_000L) }
    }

    private fun publishScreen(on: Boolean) {
        val dt = settings.deviceTopic
        mqtt?.publish("$dt/screen", if (on) "on" else "off", retained = true)
    }

    // ---- Eingehende Befehle -----------------------------------------------------

    private fun handleCommand(topic: String, payload: String) {
        val sub = topic.substringAfterLast("/cmd/", "").ifEmpty {
            topic.substringAfterLast('/')
        }
        Log.i(TAG, "Befehl: $sub = $payload")
        when (sub.lowercase()) {
            "tts", "say", "speak" -> if (settings.ttsEnabled) tts.speak(payload)
            "mediaplay", "media", "play" -> if (settings.mediaEnabled) media.play(payload)
            "mediastop" -> media.stop()
            "clearcache", "cache" -> KioskBus.send(KioskCommand.ClearCache)
            "url", "load" -> {
                currentUrl = payload.trim()
                KioskBus.send(KioskCommand.LoadUrl(currentUrl))
                publishUrl()
            }
            "reload", "refresh" -> KioskBus.send(KioskCommand.Reload)
            "screen" -> setScreen(isOn(payload))
            "screensaver" -> setScreen(!isOn(payload)) // screensaver an == Bildschirm aus
            "lock" -> if (!SystemController.lock(this)) Log.w(TAG, "Sperren fehlgeschlagen (Geräteadmin aktiv?)")
            "unlock" -> KioskBus.send(KioskCommand.Unlock)
            "brightness" -> {
                val trimmed = payload.trim().lowercase()
                if (trimmed == "auto" || trimmed == "-1") {
                    brightnessPercent = -1
                    KioskBus.send(KioskCommand.Brightness(-1f)) // Fenster-Override lösen
                } else {
                    val pct = trimmed.toIntOrNull()?.coerceIn(0, 100) ?: return
                    brightnessPercent = pct
                    // Echte Hardware-Helligkeit (voller Bereich); sonst Fenster-Fallback.
                    if (SystemController.setSystemBrightness(this, pct)) {
                        KioskBus.send(KioskCommand.Brightness(-1f))
                    } else {
                        KioskBus.send(KioskCommand.Brightness(pct / 100f))
                    }
                }
                publishBrightness()
            }
            else -> Log.w(TAG, "Unbekannter Befehl: $sub")
        }
    }

    private fun setScreen(on: Boolean) {
        if (on) wakeScreen()
        screenOn = on
        KioskBus.send(KioskCommand.Screen(on))
        publishScreen(on)
    }

    private fun isOn(payload: String): Boolean = when (payload.trim().lowercase()) {
        "on", "1", "true", "an", "ein" -> true
        else -> false
    }

    // ---- Foreground / Notification ---------------------------------------------

    private fun updateForeground(camera: Boolean, microphone: Boolean) {
        val openIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        val notif = NotificationCompat.Builder(this, FhemKioskApp.NOTIF_CHANNEL_ID)
            .setContentTitle(getString(R.string.notif_title))
            .setContentText(getString(R.string.notif_text))
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentIntent(openIntent)
            .setOngoing(true)
            .build()

        var type = ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        if (camera) type = type or ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA
        if (microphone) type = type or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
        runCatching { ServiceCompat.startForeground(this, FhemKioskApp.NOTIF_ID, notif, type) }
            .onFailure { Log.w(TAG, "startForeground(type=$type) fehlgeschlagen", it) }
    }

    override fun onDestroy() {
        val dt = settings.deviceTopic
        mqtt?.publish("$dt/status", "offline", retained = true, qos = 1)
        mqtt?.disconnect()
        battery?.stop()
        motion?.stop()
        sound?.stop()
        tts.shutdown()
        media.stop()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "KioskService"
        const val ACTION_REFRESH = "de.kewl.fullscreendy.action.REFRESH"
    }
}
