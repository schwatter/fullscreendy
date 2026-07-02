package de.kewl.fullscreendy.kiosk

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Prozessweiter Laufzeit-Status, den die UI (Menü) anzeigen kann. */
object KioskStatus {
    private val _mqttConnected = MutableStateFlow(false)
    val mqttConnected = _mqttConnected.asStateFlow()

    fun setMqttConnected(connected: Boolean) {
        _mqttConnected.value = connected
    }
}
