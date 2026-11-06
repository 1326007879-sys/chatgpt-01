package io.github.chatgpt01.petai.ui

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import io.github.chatgpt01.petai.CompanionCodec
import io.github.chatgpt01.petai.PetOptions
import io.github.chatgpt01.petai.R

/**
 * The companion module's switch. Spawning and removing baby whales stays on the
 * long-press menu: those are commands, not preferences.
 */
@Composable
fun CompanionSection(options: PetOptions, companionCount: Int, onChanged: (PetOptions) -> Unit) {
    SettingSwitch(R.string.companions, options.companions) {
        onChanged(options.copy(companions = it))
    }
    Text(stringResource(R.string.companions_hint, companionCount, CompanionCodec.MAX_COMPANIONS))
}
