package de.kewl.fullscreendy.kiosk

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Prozessweiter Laufzeit-Status, den die UI (Menü, Test-Indikatoren) anzeigen kann. */
object KioskStatus {
    private val _mqttConnected = MutableStateFlow(false)
    val mqttConnected = _mqttConnected.asStateFlow()

    /** Bewegungserkennung meldet gerade Aktivität (für den Empfindlichkeits-Test). */
    private val _motionActive = MutableStateFlow(false)
    val motionActive = _motionActive.asStateFlow()

    /** Zeitstempel des letzten Ton-Triggers (für den Empfindlichkeits-Test). */
    private val _soundAt = MutableStateFlow(0L)
    val soundAt = _soundAt.asStateFlow()

    fun setMqttConnected(connected: Boolean) {
        _mqttConnected.value = connected
    }

    fun setMotionActive(active: Boolean) {
        _motionActive.value = active
    }

    fun pulseSound() {
        _soundAt.value = System.currentTimeMillis()
    }
}
