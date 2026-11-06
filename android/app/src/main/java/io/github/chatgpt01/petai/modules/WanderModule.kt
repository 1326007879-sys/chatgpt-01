package io.github.chatgpt01.petai.modules

import io.github.chatgpt01.petai.ModuleIds

/**
 * Autonomous walking and turning. Switching it off parks every pet in its idle
 * loop: no clip switches at all, which also stops the decoder from chasing new
 * clips. The enable bit is the same `wander` preference the settings page shows.
 */
class WanderModule : HostBoundModule() {
    override val id = ModuleIds.WANDER

    override fun onEnable() {
        host.pets.forEach { it.engine.wander = true }
    }

    override fun onDisable() {
        host.pets.forEach { it.engine.stopWandering() }
    }

    override fun onEvent(event: PetEvent) {
        if (event is PetEvent.Spawned) event.pet.engine.wander = true
    }
}
