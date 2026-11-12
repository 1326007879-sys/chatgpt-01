package io.github.chatgpt01.voice

import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Assume
import org.junit.Test
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/** Dedicated test device. Models are pushed separately; no model is bundled in the APK. */
class NeuralEngineDeviceTest {
    @Test fun unseenTextSynthesizesThroughAndroidTtsAndStopsWhenDisabled() {
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val context=instrumentation.targetContext
        val external=requireNotNull(context.getExternalFilesDir(null))
        val model=File(external,"shared-model.petmodel")
        val voice=File(external,"qwen-candidate.petvoice")
        Assume.assumeTrue("Research resources must be supplied explicitly",model.isFile && voice.isFile)
        val state=EngineState(context.noBackupFilesDir.toPath())
        val previous=state.read()
        if (!java.nio.file.Files.exists(state.packs.directory("model",VoicePacks.MODEL))) model.inputStream().use(state.packs::importPack)
        if (state.packs.listVoices().none { it.id=="qwen-candidate-v1" }) voice.inputStream().use(state.packs::importPack)
        val initialized=CountDownLatch(1)
        val initStatus=AtomicInteger(TextToSpeech.ERROR)
        var tts: TextToSpeech?=null
        val output=File(context.cacheDir,"unseen-test.wav")
        try {
            state.write(EngineChoice(true,"qwen-candidate-v1"))
            instrumentation.runOnMainSync {
                tts=TextToSpeech(context,{ initStatus.set(it); initialized.countDown() },context.packageName)
            }
            assertTrue("Engine bind timeout",initialized.await(60,TimeUnit.SECONDS))
            assertEquals(TextToSpeech.SUCCESS,initStatus.get())
            val engine=requireNotNull(tts)
            val selected=engine.voices.single { it.name==NeuralVoiceService.PREFIX+"qwen-candidate-v1" }
            assertFalse(selected.isNetworkConnectionRequired)
            assertEquals(TextToSpeech.SUCCESS,engine.setVoice(selected))
            val done=CountDownLatch(1)
            val error=AtomicInteger(0)
            engine.setOnUtteranceProgressListener(object: UtteranceProgressListener() {
                override fun onStart(id: String?) = Unit
                override fun onDone(id: String?) { if(id=="unseen") done.countDown() }
                @Deprecated("Platform callback") override fun onError(id: String?) { error.set(1); done.countDown() }
            })
            assertEquals(TextToSpeech.SUCCESS,engine.synthesizeToFile("现在是下午一点。",android.os.Bundle(),output,"unseen"))
            assertTrue("Native synthesis did not complete",done.await(210,TimeUnit.SECONDS))
            assertEquals("Synthesis failed",0,error.get())
            assertTrue("PCM output missing",output.length()>48)
            state.write(EngineChoice(false,"qwen-candidate-v1"))
            assertTrue(engine.voices.isEmpty())
            assertEquals(TextToSpeech.ERROR,engine.setVoice(selected))
            engine.stop()
        } finally {
            instrumentation.runOnMainSync { tts?.stop(); tts?.shutdown() }
            state.write(previous)
            output.delete()
        }
    }
}
