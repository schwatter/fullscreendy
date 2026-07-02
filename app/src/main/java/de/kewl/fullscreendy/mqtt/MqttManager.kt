package de.kewl.fullscreendy.mqtt

import android.util.Log
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken
import org.eclipse.paho.client.mqttv3.MqttAsyncClient
import org.eclipse.paho.client.mqttv3.MqttCallbackExtended
import org.eclipse.paho.client.mqttv3.MqttConnectOptions
import org.eclipse.paho.client.mqttv3.MqttMessage
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence

data class MqttConfig(
    val host: String,
    val port: Int,
    val tls: Boolean,
    val username: String,
    val password: String,
    val clientId: String,
    /** Topic-Wildcard, das abonniert wird, z. B. "fhem/tablet/tablet1/cmd/#". */
    val commandTopic: String,
    /** Last-Will Topic (Online/Offline-Status). */
    val statusTopic: String,
)

/**
 * Dünner Wrapper um den Paho MQTT-Client mit Auto-Reconnect und Last-Will.
 * Läuft komplett im [de.kewl.fullscreendy.service.KioskService].
 */
class MqttManager(
    private val onCommand: (topic: String, payload: String) -> Unit,
    private val onConnected: () -> Unit,
    private val onConnectionChanged: (Boolean) -> Unit = {},
) {
    private var client: MqttAsyncClient? = null
    private var subscription: String = ""

    val isConnected: Boolean
        get() = client?.isConnected == true

    fun connect(cfg: MqttConfig) {
        disconnect()
        val scheme = if (cfg.tls) "ssl" else "tcp"
        val uri = "$scheme://${cfg.host}:${cfg.port}"
        subscription = cfg.commandTopic

        val c = MqttAsyncClient(uri, cfg.clientId, MemoryPersistence())
        c.setCallback(object : MqttCallbackExtended {
            override fun connectComplete(reconnect: Boolean, serverURI: String?) {
                Log.i(TAG, "verbunden (reconnect=$reconnect) mit $serverURI")
                runCatching { c.subscribe(subscription, 1) }
                    .onFailure { Log.w(TAG, "subscribe fehlgeschlagen", it) }
                onConnectionChanged(true)
                onConnected()
            }

            override fun connectionLost(cause: Throwable?) {
                Log.w(TAG, "Verbindung verloren", cause)
                onConnectionChanged(false)
            }

            override fun messageArrived(topic: String, message: MqttMessage) {
                onCommand(topic, String(message.payload, Charsets.UTF_8))
            }

            override fun deliveryComplete(token: IMqttDeliveryToken?) {}
        })

        val opts = MqttConnectOptions().apply {
            isAutomaticReconnect = true
            isCleanSession = true
            connectionTimeout = 10
            keepAliveInterval = 30
            if (cfg.username.isNotBlank()) {
                userName = cfg.username
                password = cfg.password.toCharArray()
            }
            setWill(cfg.statusTopic, "offline".toByteArray(), 1, true)
        }

        client = c
        runCatching { c.connect(opts) }
            .onFailure { Log.e(TAG, "connect fehlgeschlagen", it) }
    }

    fun publish(topic: String, payload: String, retained: Boolean = false, qos: Int = 0) {
        val c = client ?: return
        if (!c.isConnected) return
        runCatching {
            c.publish(topic, MqttMessage(payload.toByteArray(Charsets.UTF_8)).apply {
                this.qos = qos
                isRetained = retained
            })
        }.onFailure { Log.w(TAG, "publish auf $topic fehlgeschlagen", it) }
    }

    fun disconnect() {
        val c = client ?: return
        runCatching { if (c.isConnected) c.disconnectForcibly(500) }
        runCatching { c.close() }
        client = null
        onConnectionChanged(false)
    }

    companion object {
        private const val TAG = "MqttManager"
    }
}
