package de.kewl.fullscreendy.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.platform.LocalContext
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

                val context = LocalContext.current
                Text(
                    s.githubRepo,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .padding(top = 8.dp)
                        .clickable {
                            runCatching {
                                context.startActivity(
                                    Intent(Intent.ACTION_VIEW, Uri.parse(REPO_URL))
                                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                )
                            }
                        }
                )
                Text(REPO_URL, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

private const val REPO_URL = "https://github.com/Glenn-Dandy/fullscreendy"

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
