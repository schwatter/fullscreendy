package de.kewl.fullscreendy.device

import android.os.Build
import de.kewl.fullscreendy.BuildConfig
import java.net.Inet4Address
import java.net.NetworkInterface
import java.util.Collections

/** Statische Geräteinformationen für MQTT-Readings und die Über-Seite. */
object DeviceInfo {

    val appVersion: String
        get() = BuildConfig.VERSION_NAME

    val androidVersion: String
        get() = "${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})"

    /** Erste nicht-lokale IPv4-Adresse (z. B. WLAN), sonst "n/a". */
    fun ipv4(): String {
        return runCatching {
            Collections.list(NetworkInterface.getNetworkInterfaces())
                .filter { it.isUp && !it.isLoopback }
                .flatMap { Collections.list(it.inetAddresses) }
                .filterIsInstance<Inet4Address>()
                .firstOrNull { !it.isLoopbackAddress }
                ?.hostAddress
        }.getOrNull() ?: "n/a"
    }
}
