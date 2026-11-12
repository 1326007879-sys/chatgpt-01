package io.github.chatgpt01.voice

import android.content.pm.PackageManager
import android.media.AudioFormat
import android.os.Process
import android.speech.tts.*
import com.k2fsa.sherpa.onnx.*
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/** Native inference runs in this separate APK, never in the host. No network permission. */
class NeuralVoiceService : TextToSpeechService() {
    companion object {
        const val PREFIX = "petai.neural."
        private val HOSTS = setOf("io.github.chatgpt01.petai", "io.github.chatgpt01.petai.development",
            "io.github.chatgpt01.petai.preview5", "io.github.chatgpt01.voice")
    }
    private val serial = AtomicLong()
    private val running = AtomicLong()
    private val destroyed = AtomicBoolean()
    private val nativeLock = ReentrantLock()
    private val watchdog = Executors.newSingleThreadScheduledExecutor()
    private var engine: OfflineTts? = null
    private fun state() = EngineState(noBackupFilesDir.toPath())
    private fun voices(): List<PackInfo> = runCatching { val s = state(); if (s.read().enabled) s.available() else emptyList() }.getOrDefault(emptyList())
    override fun onGetLanguage() = arrayOf("zho", "CHN", "")
    override fun onIsLanguageAvailable(lang: String, country: String, variant: String): Int =
        if (lang in setOf("zh", "chi", "zho", "en", "eng") && voices().isNotEmpty()) TextToSpeech.LANG_AVAILABLE else TextToSpeech.LANG_NOT_SUPPORTED
    override fun onLoadLanguage(lang: String, country: String, variant: String) = onIsLanguageAvailable(lang,country,variant)
    override fun onGetVoices(): List<Voice> = voices().map {
        Voice(PREFIX+it.id, Locale.SIMPLIFIED_CHINESE, Voice.QUALITY_NORMAL, Voice.LATENCY_VERY_HIGH, false, emptySet())
    }
    override fun onGetDefaultVoiceNameFor(lang: String, country: String, variant: String): String? =
        runCatching { state().read().voice.takeIf { id -> voices().any { it.id == id } }?.let { PREFIX+it } }.getOrNull()
    override fun onIsValidVoiceName(name: String): Int = if (voices().any { PREFIX+it.id == name }) TextToSpeech.SUCCESS else TextToSpeech.ERROR
    override fun onLoadVoice(name: String) = onIsValidVoiceName(name)
    override fun onStop() { serial.incrementAndGet() }

    override fun onSynthesizeText(request: SynthesisRequest, callback: SynthesisCallback): Unit = nativeLock.withLock {
        if (destroyed.get()) { callback.error(TextToSpeech.ERROR_SYNTHESIS); return@withLock }
        val ticket = serial.incrementAndGet()
        running.set(ticket)
        val timeout = watchdog.schedule({ if (running.get() == ticket) Process.killProcess(Process.myPid()) }, 180, TimeUnit.SECONDS)
        try {
            val caller = packageManager.getPackagesForUid(request.callerUid).orEmpty()
            check(caller.any { it in HOSTS && packageManager.checkSignatures(packageName,it) == PackageManager.SIGNATURE_MATCH })
            val text = request.charSequenceText.toString()
            require(text.isNotBlank() && text.length <= 200)
            val s = state()
            check(s.read().enabled && !destroyed.get())
            val voiceName = request.voiceName ?: onGetDefaultVoiceNameFor(request.language,request.country,request.variant)
            val voice = s.available().first { PREFIX+it.id == voiceName }
            val dir = s.packs.directory("model", VoicePacks.MODEL)
            if (engine == null) {
                fun path(name: String) = dir.resolve(name).toString()
                engine = OfflineTts(config = OfflineTtsConfig(model = OfflineTtsModelConfig(
                    zipvoice = OfflineTtsZipVoiceModelConfig(tokens=path("tokens.txt"),encoder=path("encoder.int8.onnx"),
                        decoder=path("decoder.int8.onnx"),vocoder=path("vocos_24khz.onnx"),
                        lexicon=path("lexicon.txt")+","+path("lexicon-en-us.txt")), numThreads=2, provider="cpu"), maxNumSentences=1))
            }
            val tts = requireNotNull(engine)
            val samples = s.packs.readReference(s.packs.directory("voice",voice.id).resolve("reference.wav"))
            val config = GenerationConfig(referenceAudio=samples, referenceSampleRate=24000, referenceText=voice.referenceText)
            check(serial.get() == ticket && callback.start(tts.sampleRate(),AudioFormat.ENCODING_PCM_16BIT,1) == TextToSpeech.SUCCESS)
            var emitted = 0
            var failed = false
            fun emit(wave: FloatArray): Int {
                if (serial.get() != ticket || destroyed.get() || !s.read().enabled) return 0
                if (emitted + wave.size > 24000 * 120) { failed=true; return 0 }
                val capacity = minOf(callback.maxBufferSize,8192) / 2 * 2
                check(capacity >= 2)
                val bytes = ByteArray(capacity)
                var index = 0
                while (index < wave.size) {
                    if (serial.get() != ticket) return 0
                    val count = minOf(capacity/2,wave.size-index)
                    for (i in 0 until count) {
                        val value = wave[index+i]
                        check(value.isFinite())
                        val pcm = (value.coerceIn(-1f,1f)*32767).toInt()
                        bytes[i*2] = pcm.toByte(); bytes[i*2+1] = (pcm shr 8).toByte()
                    }
                    if (callback.audioAvailable(bytes,0,count*2) != TextToSpeech.SUCCESS) { failed=true; return 0 }
                    index += count
                }
                emitted += wave.size
                return 1
            }
            val result = tts.generateWithConfigAndCallback(text,config,::emit)
            if (emitted == 0 && serial.get() == ticket) emit(result.samples)
            if (emitted > 0 && !failed && serial.get() == ticket && s.read().enabled) callback.done() else callback.error(TextToSpeech.ERROR_SYNTHESIS)
        } catch (_: Exception) { callback.error(TextToSpeech.ERROR_SYNTHESIS) }
        finally {
            timeout.cancel(false)
            running.compareAndSet(ticket,0)
            serial.compareAndSet(ticket,ticket+1)
            if (destroyed.get()) { engine?.release(); engine = null; watchdog.shutdownNow() }
        }
    }
    override fun onDestroy() {
        destroyed.set(true); onStop()
        if (nativeLock.tryLock()) try { engine?.release(); engine=null; watchdog.shutdownNow() } finally { nativeLock.unlock() }
        super.onDestroy()
    }
}
