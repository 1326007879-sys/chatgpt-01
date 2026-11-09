package io.github.chatgpt01.petai.ui

import android.os.Bundle
import io.github.chatgpt01.petai.LocalizedActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.chatgpt01.petai.R

/** Placeholder only: no discovery, file access, network calls or third-party code loading. */
class PluginManagementActivity : LocalizedActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme(colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    Column(
                        Modifier.fillMaxSize().safeDrawingPadding()
                            .verticalScroll(rememberScrollState()).padding(24.dp)
                            .testTag("plugins_page"),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        TextButton(onClick = { finish() }) {
                            Text(stringResource(R.string.plugins_back))
                        }
                        Text(stringResource(R.string.plugins_title),
                            style = MaterialTheme.typography.headlineSmall)
                        Text(stringResource(R.string.plugins_pending),
                            style = MaterialTheme.typography.titleMedium)
                        Text(stringResource(R.string.plugins_empty),
                            modifier = Modifier.testTag("plugins_empty"))
                        Text(stringResource(R.string.plugins_scope),
                            style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
}
