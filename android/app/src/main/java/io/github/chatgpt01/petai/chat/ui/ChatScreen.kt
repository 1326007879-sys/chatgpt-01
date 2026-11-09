package io.github.chatgpt01.petai.chat.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import io.github.chatgpt01.petai.chat.*
import io.github.chatgpt01.petai.UiMessages
import io.github.chatgpt01.petai.R
import io.github.chatgpt01.petai.chat.voice.SpeechUiState
import androidx.compose.ui.res.stringResource

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ChatScreen(state: ConversationState, input: ChatInputState, onEdit: (String) -> Unit,
               onSend: () -> Unit, onStop: () -> Unit, onRetry: (String) -> Unit,
               onSettings: () -> Unit, onBack: () -> Unit,
               speech: SpeechUiState = SpeechUiState(), onVoiceSettings: () -> Unit = {},
               onRead: (String) -> Unit = {}, onStopRead: () -> Unit = {}) {
    val list = rememberLazyListState()
    val follow = !list.canScrollForward
    LaunchedEffect(state.session.messages.size, state.session.messages.lastOrNull()?.content) {
        if (follow && state.session.messages.isNotEmpty()) list.scrollToItem(state.session.messages.lastIndex)
    }
    Column(Modifier.fillMaxSize().safeDrawingPadding().imePadding().padding(horizontal = 16.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text(stringResource(R.string.back)) }
            Text(stringResource(R.string.chat_title), Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
            TextButton(onClick = onSettings, enabled = !state.generation.isRunning && !input.submitting) { Text(stringResource(R.string.model_settings)) }
        }
        if (state.storageError) Text(stringResource(R.string.history_failed), color = MaterialTheme.colorScheme.error)
        if (state.session.messages.isEmpty()) {
            Column(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.Center) {
                Text(stringResource(R.string.chat_welcome), style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.height(12.dp))
                Text(stringResource(R.string.chat_privacy))
            }
        } else LazyColumn(Modifier.weight(1f).testTag("chat_messages"), state = list,
            contentPadding = PaddingValues(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(state.session.messages, key = { it.id }) { message ->
                Column(Modifier.fillMaxWidth(), horizontalAlignment = if (message.role == MessageRole.USER)
                    Alignment.End else Alignment.Start) {
                    Text(if (message.role == MessageRole.USER) stringResource(R.string.role_user) else stringResource(R.string.role_pet), style = MaterialTheme.typography.labelMedium)
                    Surface(color = if (message.role == MessageRole.USER) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surfaceVariant, shape = MaterialTheme.shapes.medium) {
                        SelectionContainer { Text(message.content.ifEmpty { "…" }, Modifier.padding(14.dp)) }
                    }
                    if (message.role == MessageRole.ASSISTANT && message.status == MessageStatus.COMPLETED)
                        TextButton(onClick = { onRead(message.content) }, enabled = speech.canSpeak && !state.generation.isRunning) {
                            Text(stringResource(R.string.voice_read_reply))
                        }
                    val status = statusText(message)
                    if (status.isNotEmpty()) Text(UiMessages.text(status), style = MaterialTheme.typography.bodySmall,
                        color = if (message.status == MessageStatus.FAILED) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
                    if (canRetry(message, state.session.messages.lastOrNull()?.id, state.generation.isRunning))
                        TextButton(onClick = { onRetry(message.id) }, enabled = !input.submitting) { Text(stringResource(R.string.retry)) }
                }
            }
        }
        speech.notice?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
        FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            TextButton(onClick = onVoiceSettings) { Text(stringResource(R.string.voice_settings)) }
            if (speech.speaking) TextButton(onClick = onStopRead) { Text(stringResource(R.string.voice_stop)) }
        }
        input.notice?.let { Text(UiMessages.text(it), color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("chat_notice")) }
        Text(stringResource(R.string.keyboard_voice_hint), style = MaterialTheme.typography.bodySmall)
        OutlinedTextField(value = input.draft, onValueChange = onEdit, label = { Text(stringResource(R.string.message)) },
            modifier = Modifier.fillMaxWidth().onFocusChanged { if (it.isFocused) onStopRead() }.testTag("chat_input"), minLines = 2, maxLines = 5,
            supportingText = { Text("${input.draft.length}/12,000") })
        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.End) {
            if (state.generation.isRunning) OutlinedButton(onClick = onStop, modifier = Modifier.testTag("chat_stop")) { Text(stringResource(R.string.stop_generation)) }
            else Button(onClick = onSend, enabled = !input.submitting && input.draft.isNotBlank() && input.draft.length <= 12000,
                modifier = Modifier.testTag("chat_send")) { Text(if (input.submitting) stringResource(R.string.sending) else stringResource(R.string.send)) }
        }
    }
}

@Composable
fun ModelSettingsDialog(controller: ModelSettingsController, onDismiss: () -> Unit) {
    val state by controller.state.collectAsState()
    var editor by remember(state.editor) { mutableStateOf(state.editor) }
    var key by remember { mutableStateOf("") } // Intentionally never rememberSaveable.
    val savedAtOpen = remember { state.saved }
    LaunchedEffect(state.saved) { if (state.saved != savedAtOpen) { key = ""; onDismiss() } }
    val enabled = !state.loading && !state.busy
    AlertDialog(onDismissRequest = { if (!state.busy) { key = ""; onDismiss() } },
        title = { Text(stringResource(R.string.model_settings)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.model_privacy))
                if (state.loading) Text(stringResource(R.string.config_loading))
                OutlinedTextField(editor.url, { editor = editor.copy(url = it) }, label = { Text(stringResource(R.string.api_url)) },
                    supportingText = { Text(stringResource(R.string.api_url_hint)) }, enabled = enabled, singleLine = true)
                OutlinedTextField(editor.model, { editor = editor.copy(model = it) }, label = { Text(stringResource(R.string.model_name)) }, enabled = enabled, singleLine = true)
                OutlinedTextField(key, { key = it }, label = { Text("API Key") }, enabled = enabled, singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    supportingText = { Text(if (state.hasKey && editor.url == state.editor.url)
                        stringResource(R.string.key_saved) else stringResource(R.string.key_needed)) })
                OutlinedTextField(editor.temperature, { editor = editor.copy(temperature = it) }, label = { Text(stringResource(R.string.temperature)) }, enabled = enabled, singleLine = true)
                OutlinedTextField(editor.tokens, { editor = editor.copy(tokens = it) }, label = { Text(stringResource(R.string.max_tokens)) }, enabled = enabled, singleLine = true)
                OutlinedTextField(editor.timeout, { editor = editor.copy(timeout = it) }, label = { Text(stringResource(R.string.timeout)) }, enabled = enabled, singleLine = true)
                state.error?.let { Text(UiMessages.text(it), color = MaterialTheme.colorScheme.error) }
                TextButton(onClick = { key = ""; controller.clearKey(editor) },
                    enabled = enabled && state.hasKey && editor.url == state.editor.url) { Text(stringResource(R.string.delete_key)) }
            }
        },
        confirmButton = { Button(onClick = { controller.save(editor, key) }, enabled = enabled) { Text(if (state.busy) stringResource(R.string.saving) else stringResource(R.string.save)) } },
        dismissButton = { TextButton(onClick = { key = ""; onDismiss() }, enabled = !state.busy) { Text("取消") } })
}

@Composable
fun ChatTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()) {
        Surface(Modifier.fillMaxSize(), content = content)
    }
}
