package io.github.chatgpt01.petai.chat.voice

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.selection.selectable
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.chatgpt01.petai.R

@Composable
fun VoiceSettingsScreen(state: SpeechUiState, onAutoRead: (Boolean) -> Unit, onSelect: (String?) -> Unit,
                        onPreview: () -> Unit, onStop: () -> Unit, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    LazyColumn(Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 20.dp)
        .testTag("voice_settings"), contentPadding = PaddingValues(vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { TextButton(onClick = onBack) { Text(stringResource(R.string.voice_back)) } }
        item { Text(stringResource(R.string.voice_settings), style = MaterialTheme.typography.headlineSmall) }
        item { Text(stringResource(R.string.voice_privacy)) }
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.voice_auto_read), Modifier.weight(1f))
                Switch(checked = state.autoRead, onCheckedChange = onAutoRead,
                    enabled = !state.loading && (state.ready || state.autoRead), modifier = Modifier.testTag("voice_auto_read"))
            }
        }
        item { Text(stringResource(R.string.voice_auto_hint), style = MaterialTheme.typography.bodySmall) }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = onPreview, enabled = state.canSpeak) { Text(stringResource(R.string.voice_preview)) }
                OutlinedButton(onClick = onStop, enabled = state.speaking) { Text(stringResource(R.string.voice_stop)) }
            }
        }
        if (state.loading) item { Text(stringResource(R.string.voice_loading)) }
        state.notice?.let { notice -> item { Text(notice, color = MaterialTheme.colorScheme.error) } }
        item { Text(stringResource(R.string.voice_selection_hint)) }
        item {
            VoiceRow(stringResource(R.string.voice_default), state.selected == null, state.ready) { onSelect(null) }
        }
        if (!state.loading && state.voices.isEmpty()) item { Text(stringResource(R.string.voice_no_voices)) }
        items(state.voices, key = { it.name }) { voice ->
            val network = stringResource(if (voice.network) R.string.voice_network else R.string.voice_offline)
            VoiceRow("${voice.name}\n${voice.language} · $network", state.selected == voice.name, state.ready) {
                onSelect(voice.name)
            }
        }
        item { Text(stringResource(R.string.voice_engine_help), style = MaterialTheme.typography.bodySmall) }
    }
}

@Composable
private fun VoiceRow(label: String, selected: Boolean, enabled: Boolean, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().selectable(selected = selected, enabled = enabled, role = Role.RadioButton, onClick = onClick).padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically) {
        RadioButton(selected, onClick = null, enabled = enabled)
        Spacer(Modifier.width(12.dp))
        Text(label, modifier = Modifier.weight(1f))
    }
}
