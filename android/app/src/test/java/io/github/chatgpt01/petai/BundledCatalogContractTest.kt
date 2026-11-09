package io.github.chatgpt01.petai

import com.google.gson.JsonParser
import java.io.File
import org.junit.Assert.*
import org.junit.Test

/** Exercises the real generated catalog in every selected build profile, without a device. */
class BundledCatalogContractTest {
    @Test fun everyPackagedAnimationHasAFrameAndCompletesManualPlayback() {
        val root = File("build/generated/petAssets")
        val json = JsonParser.parseString(File(root, "pet/manifest.json").readText()).asJsonObject
        val entries = json.getAsJsonArray("clips").map { it.asJsonObject }
        val clips = entries.map { entry ->
            ClipSpec(entry["id"].asString, entry["group"].asString, entry["fps"].asDouble,
                entry["frames"].asInt, path = entry["path"].asString,
                englishName = entry["name_en"].asString, batch = entry["batch"].asInt,
                noMirror = entry["no_mirror"].asBoolean)
        }
        assertTrue(clips.size in setOf(11, 23, 100, 106))
        assertEquals(clips.size, clips.map { it.id }.distinct().size)
        assertEquals(11, clips.count { it.batch == 0 })
        for (clip in clips) {
            assertTrue(clip.englishName.isNotBlank())
            assertTrue(File(root, clip.framePath(0)).isFile)
            assertTrue(File(root, clip.framePath(clip.frames - 1)).isFile)
            val engine = PetEngine(clips, kotlin.random.Random(7))
            engine.resize(300.0, 600.0)
            engine.wander = false
            val x = engine.x; val y = engine.y
            assertTrue(engine.playAction(clip.id))
            assertEquals(clip.id, engine.clip.id)
            repeat(kotlin.math.ceil(clip.duration / .05).toInt() + 1) { engine.advance(.05) }
            assertEquals(PetMode.IDLE, engine.mode)
            assertEquals(x, engine.x, 0.0)
            assertEquals(y, engine.y, 0.0)
        }
    }
}
