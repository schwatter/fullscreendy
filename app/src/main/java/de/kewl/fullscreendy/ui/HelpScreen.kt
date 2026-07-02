package de.kewl.fullscreendy.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
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
import de.kewl.fullscreendy.i18n.LocalStrings

@Composable
fun HelpScreen(onBack: () -> Unit) {
    val s = LocalStrings.current
    val context = LocalContext.current
    val text = remember {
        runCatching {
            context.assets.open("README.md").bufferedReader().use { it.readText() }
        }.getOrDefault("README not found.")
    }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(modifier = Modifier.fillMaxSize()) {
            TitleBar(title = s.help, onBack = onBack)
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 8.dp)
            ) {
                Text(text, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
