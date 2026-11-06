package io.github.chatgpt01.petai.modules

import io.github.chatgpt01.petai.AndroidClickOutput
import io.github.chatgpt01.petai.ClickAudio
import io.github.chatgpt01.petai.ModuleIds
import io.github.chatgpt01.petai.PetRuntime

class ClickSoundModule : HostBoundModule() {
    override val id = ModuleIds.CLICK_SOUND
    private var audio: ClickAudio? = null

    override fun onEnable() {
        audio = ClickAudio({ uri, changed -> AndroidClickOutput(host.context, uri, changed) },
            host::now, { PetRuntime.audioError.value = it == ClickAudio.State.ERROR },
            { PetRuntime.audioPlays.incrementAndGet() })
        onOptionsChanged()
    }

    override fun onOptionsChanged() {
        audio?.configure(host.options.clickSoundUri, host.options.clickSoundVolume)
    }

    override fun onEvent(event: PetEvent) {
        when (event) {
            is PetEvent.Pressed -> audio?.press()
            is PetEvent.Clicked -> audio?.release()
            else -> Unit
        }
    }

    override fun onDisable() { audio?.close(); audio = null }

    override fun diagnostics() = audio?.diagnostics().orEmpty()
}
