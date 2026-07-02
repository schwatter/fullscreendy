package de.kewl.fullscreendy.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import de.kewl.fullscreendy.data.Settings
import de.kewl.fullscreendy.device.DeviceInfo
import de.kewl.fullscreendy.i18n.LocalStrings

@Composable
fun AboutScreen(settings: Settings, onBack: () -> Unit) {
    val s = LocalStrings.current
    val ip = remember { DeviceInfo.ipv4() }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(modifier = Modifier.fillMaxSize()) {
            TitleBar(title = s.about, onBack = onBack)
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text("FullScreendy", style = MaterialTheme.typography.headlineSmall)
                InfoRow(s.appVersionLabel, DeviceInfo.appVersion)
                InfoRow(s.androidVersionLabel, DeviceInfo.androidVersion)
                InfoRow(s.ipAddressLabel, ip)
                InfoRow(s.deviceIdLabel, settings.deviceId)
                InfoRow(s.urlLabel, settings.dashboardUrl.ifBlank { "—" })
            }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}
