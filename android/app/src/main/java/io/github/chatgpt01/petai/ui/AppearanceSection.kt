package io.github.chatgpt01.petai.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import io.github.chatgpt01.petai.PetOptions
import io.github.chatgpt01.petai.R

/** Size is committed when the finger leaves the slider (commit_policy: on_finish). */
@Composable
fun AppearanceSection(options: PetOptions, onChanged: (PetOptions) -> Unit) {
    var draft by remember(options.size) { mutableStateOf(options.size) }
    SettingSlider(label = R.string.pet_size,
        valueText = "${(draft * 100).toInt()}%",
        value = draft,
        range = .6f..1.6f,
        changed = { draft = it },
        onFinished = { onChanged(options.copy(size = draft)) })
}
