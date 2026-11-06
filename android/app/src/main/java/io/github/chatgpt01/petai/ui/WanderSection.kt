package io.github.chatgpt01.petai.ui

import androidx.compose.runtime.Composable
import io.github.chatgpt01.petai.PetOptions
import io.github.chatgpt01.petai.R

/** The wander module's one switch; switching it off parks every pet in its idle loop. */
@Composable
fun WanderSection(options: PetOptions, onChanged: (PetOptions) -> Unit) {
    SettingSwitch(R.string.wander, options.wander) { onChanged(options.copy(wander = it)) }
}
