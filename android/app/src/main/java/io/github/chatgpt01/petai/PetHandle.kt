package io.github.chatgpt01.petai

/**
 * Pure per-pet state: the behavior model plus identity. Everything Android
 * (window params, views, decoder cursors) belongs to the service, so modules can
 * be exercised without a device.
 */
class PetHandle(val id: Int, val engine: PetEngine) {
    val isCompanion get() = id != 0
}
