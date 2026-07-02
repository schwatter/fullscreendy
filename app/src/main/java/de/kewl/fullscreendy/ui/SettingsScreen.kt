package de.kewl.fullscreendy.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import de.kewl.fullscreendy.data.Settings
import de.kewl.fullscreendy.i18n.LocalStrings
import de.kewl.fullscreendy.i18n.Strings

private enum class Section { Home, Connection, Display, Behavior, Sounds, System }

@Composable
fun SettingsScreen(
    initial: Settings,
    onPersist: (Settings) -> Unit,
    onExit: () -> Unit,
) {
    val s = LocalStrings.current
    var draft by remember { mutableStateOf(initial) }
    var section by remember { mutableStateOf(Section.Home) }

    val title = when (section) {
        Section.Home -> s.settings
        Section.Connection -> s.secConnection
        Section.Display -> s.secDisplay
        Section.Behavior -> s.secBehavior
        Section.Sounds -> s.secSounds
        Section.System -> s.secSystem
    }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(modifier = Modifier.fillMaxSize()) {
            TitleBar(
                title = title,
                onBack = {
                    if (section == Section.Home) { onPersist(draft); onExit() } else section = Section.Home
                }
            ) {
                TextButton(onClick = { onPersist(draft) }) { Text(s.save) }
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                when (section) {
                    Section.Home -> {
                        CategoryRow(s.secConnection, s.secConnectionDesc) { section = Section.Connection }
                        CategoryRow(s.secDisplay, s.secDisplayDesc) { section = Section.Display }
                        CategoryRow(s.secBehavior, s.secBehaviorDesc) { section = Section.Behavior }
                        CategoryRow(s.secSounds, s.secSoundsDesc) { section = Section.Sounds }
                        CategoryRow(s.secSystem, s.secSystemDesc) { section = Section.System }
                    }
                    Section.Connection -> ConnectionSection(draft, s) { draft = it }
                    Section.Display -> DisplaySection(draft, s) { draft = it }
                    Section.Behavior -> BehaviorSection(draft, s) { draft = it }
                    Section.Sounds -> SoundsSection(s)
                    Section.System -> SystemSection(draft, s) { draft = it }
                }
            }
        }
    }
}

@Composable
private fun ConnectionSection(draft: Settings, s: Strings, onChange: (Settings) -> Unit) {
    var portText by remember { mutableStateOf(draft.mqttPort.toString()) }

    OutlinedTextField(
        value = draft.dashboardUrl,
        onValueChange = { onChange(draft.copy(dashboardUrl = it)) },
        label = { Text(s.dashboardUrl) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth()
    )
    HorizontalDivider()
    Text(s.mqttBroker, style = MaterialTheme.typography.titleMedium)
    OutlinedTextField(
        value = draft.mqttHost,
        onValueChange = { onChange(draft.copy(mqttHost = it)) },
        label = { Text(s.host) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth()
    )
    OutlinedTextField(
        value = portText,
        onValueChange = {
            portText = it.filter(Char::isDigit)
            onChange(draft.copy(mqttPort = portText.toIntOrNull() ?: 1883))
        },
        label = { Text(s.port) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.fillMaxWidth()
    )
    SwitchRow(s.useTls, draft.mqttTls) { onChange(draft.copy(mqttTls = it)) }
    OutlinedTextField(
        value = draft.mqttUser,
        onValueChange = { onChange(draft.copy(mqttUser = it)) },
        label = { Text(s.username) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth()
    )
    OutlinedTextField(
        value = draft.mqttPass,
        onValueChange = { onChange(draft.copy(mqttPass = it)) },
        label = { Text(s.password) },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        modifier = Modifier.fillMaxWidth()
    )
    HorizontalDivider()
    Text(s.topics, style = MaterialTheme.typography.titleMedium)
    OutlinedTextField(
        value = draft.baseTopic,
        onValueChange = { onChange(draft.copy(baseTopic = it)) },
        label = { Text(s.baseTopic) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth()
    )
    OutlinedTextField(
        value = draft.deviceId,
        onValueChange = { onChange(draft.copy(deviceId = it)) },
        label = { Text(s.deviceId) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth()
    )
    Text(
        "${draft.baseTopic.trimEnd('/')}/${draft.deviceId}/…",
        style = MaterialTheme.typography.bodySmall
    )
}

@Composable
private fun DisplaySection(draft: Settings, s: Strings, onChange: (Settings) -> Unit) {
    SwitchRow(s.ignoreFontScale, draft.ignoreSystemFontScale) {
        onChange(draft.copy(ignoreSystemFontScale = it))
    }
    SwitchRow(s.allowZoom, draft.zoomEnabled) { onChange(draft.copy(zoomEnabled = it)) }
    SwitchRow(s.keepScreenOn, draft.keepScreenOn) { onChange(draft.copy(keepScreenOn = it)) }
}

@Composable
private fun BehaviorSection(draft: Settings, s: Strings, onChange: (Settings) -> Unit) {
    SwitchRow(s.motionDetection, draft.motionEnabled) { onChange(draft.copy(motionEnabled = it)) }
    SwitchRow(s.motionWakesScreen, draft.motionWakesScreen) {
        onChange(draft.copy(motionWakesScreen = it))
    }
    SwitchRow(s.pullToRefresh, draft.pullToRefresh) { onChange(draft.copy(pullToRefresh = it)) }
    SwitchRow(s.ttsEnabled, draft.ttsEnabled) { onChange(draft.copy(ttsEnabled = it)) }
    SwitchRow(s.mediaEnabled, draft.mediaEnabled) { onChange(draft.copy(mediaEnabled = it)) }
}

@Composable
private fun SoundsSection(s: Strings) {
    val context = LocalContext.current
    val soundsPath = remember(context) {
        context.getExternalFilesDir("sounds")?.absolutePath ?: "—"
    }
    Text(s.soundsHint, style = MaterialTheme.typography.bodyMedium)
    Text(soundsPath, style = MaterialTheme.typography.bodySmall)
}

@Composable
private fun SystemSection(draft: Settings, s: Strings, onChange: (Settings) -> Unit) {
    Text(s.language, style = MaterialTheme.typography.titleMedium)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(
            selected = draft.language == "en",
            onClick = { onChange(draft.copy(language = "en")) },
            label = { Text(s.languageEnglish) }
        )
        FilterChip(
            selected = draft.language == "de",
            onClick = { onChange(draft.copy(language = "de")) },
            label = { Text(s.languageGerman) }
        )
    }
    HorizontalDivider()
    SwitchRow(s.startOnBoot, draft.startOnBoot) { onChange(draft.copy(startOnBoot = it)) }
    OutlinedTextField(
        value = draft.adminPin,
        onValueChange = { onChange(draft.copy(adminPin = it.filter(Char::isDigit).take(8))) },
        label = { Text(s.adminPin) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
        visualTransformation = PasswordVisualTransformation(),
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun CategoryRow(title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(subtitle, style = MaterialTheme.typography.bodySmall)
        }
        Text("›", style = MaterialTheme.typography.titleLarge)
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
