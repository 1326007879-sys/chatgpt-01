package io.github.chatgpt01.petai.chat.voice

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import io.github.chatgpt01.petai.R
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

/** UI-owned, non-secret settings. The selected voice is scoped to the current system engine. */
data class SystemVoiceOption(val name: String, val language: String, val network: Boolean)
data class SpeechUiState(
    val loading: Boolean = true,
    val ready: Boolean = false,
    val canSpeak: Boolean = false,
    val autoRead: Boolean = false,
    val selected: String? = null,
    val voices: List<SystemVoiceOption> = emptyList(),
    val speaking: Boolean = false,
    val notice: String? = null,
    val enginePackage: String? = null,
    val neuralAvailable: Boolean = false,
)

/** Owned by a ChatActivity; all platform callbacks are marshalled onto its main thread. */
class SystemSpeechOutput(context: Context, useSavedEngine: Boolean = true, enginePackage: String? = null,
                         private val requestedVoice: String? = null) : AutoCloseable {
    private val app = context.applicationContext
    private val handler = Handler(Looper.getMainLooper())
    private val prefs = app.getSharedPreferences("chat_voice", Context.MODE_PRIVATE)
    private val audio = app.getSystemService(AudioManager::class.java)
    private val attributes = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build()
    private val playback = SpeechPlayback()
    private var closed = false
    private var defaultSupported = false
    private var focus: AudioFocusRequest? = null
    private var tts: TextToSpeech? = null
    private var selectedEngine = if (useSavedEngine) prefs.getString("engine_package",null) else enginePackage
    private var initTicket = 0L
    private var timeout: Runnable? = null
    private var initTimeout: Runnable? = null
    private val mutable = MutableStateFlow(SpeechUiState(autoRead = prefs.getBoolean("auto_read", false)))
    val state = mutable.asStateFlow()

    init { initialize() }
    private fun initialize() {
        initTimeout?.let(handler::removeCallbacks); initTimeout=null
        val ticket = ++initTicket
        stop(); tts?.shutdown(); tts=null
        mutable.value = mutable.value.copy(loading=true,ready=false,canSpeak=false,voices=emptyList(),selected=null,
            enginePackage=selectedEngine,neuralAvailable=NeuralEngine.trusted(app),notice=null)
        if (selectedEngine != null && (selectedEngine != NeuralEngine.PACKAGE || !NeuralEngine.trusted(app))) {
            mutable.value=mutable.value.copy(loading=false,notice=text(R.string.voice_neural_unavailable)); return
        }
        initTimeout = Runnable {
            if (!closed && ticket==initTicket && mutable.value.loading) {
                initTicket++; tts?.shutdown(); tts=null
                mutable.value=mutable.value.copy(loading=false,ready=false,canSpeak=false,
                    notice=text(if(selectedEngine==NeuralEngine.PACKAGE) R.string.voice_neural_unavailable else R.string.voice_engine_missing))
            }
        }.also { handler.postDelayed(it,20000L) }
        try {
            tts = TextToSpeech(app, { status -> handler.post { if (ticket==initTicket) initialized(status) } }, selectedEngine)
        } catch (_: RuntimeException) {
            initTimeout?.let(handler::removeCallbacks); initTimeout=null
            mutable.value = mutable.value.copy(loading = false, notice = text(R.string.voice_engine_missing))
        }
    }

    fun selectEngine(name: String?) {
        if (closed || (name != null && (name!=NeuralEngine.PACKAGE || !NeuralEngine.trusted(app)))) return
        if (!prefs.edit().putString("engine_package",name).remove("voice_name").remove("voice_engine").commit()) {
            mutable.value=mutable.value.copy(notice=text(R.string.voice_save_failed)); return
        }
        selectedEngine=name
        initialize()
    }

    fun refresh() { if (!closed) initialize() }

    private fun text(id: Int) = io.github.chatgpt01.petai.AppLanguage.wrap(app).getString(id)
    private fun initialized(status: Int) {
        if (closed) return
        initTimeout?.let(handler::removeCallbacks); initTimeout=null
        val engine = tts
        if (status != TextToSpeech.SUCCESS || engine == null) {
            mutable.value = mutable.value.copy(loading = false, notice = text(R.string.voice_engine_missing))
            return
        }
        try {
            engine.setAudioAttributes(attributes)
            defaultSupported = engine.setLanguage(Locale.SIMPLIFIED_CHINESE) >= TextToSpeech.LANG_AVAILABLE
            val voices = engine.voices.orEmpty().filter {
                !it.features.orEmpty().contains(TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED) && NeuralEngine.accepts(selectedEngine,it.name)
            }.sortedWith(compareBy({ it.locale.language != "zh" }, { it.locale.toLanguageTag() }, { it.name }))
                .map { SystemVoiceOption(it.name, it.locale.toLanguageTag(), it.isNetworkConnectionRequired) }
                .distinctBy { it.name }
            val currentEngine = selectedEngine ?: engine.defaultEngine
            val saved = requestedVoice ?: prefs.getString("voice_name", null)
            val savedEngine = if (requestedVoice!=null) currentEngine else prefs.getString("voice_engine", null)
            val selected = VoiceSelection.resolve(saved, savedEngine,
                currentEngine, voices.map { it.name }.toSet())
            if (!VoiceSelection.engineReady(selectedEngine,saved,savedEngine,currentEngine,voices.map { it.name }.toSet())) {
                mutable.value=mutable.value.copy(loading=false,notice=text(R.string.voice_neural_unavailable)); return
            }
            mutable.value = mutable.value.copy(loading = false, ready = true, voices = voices,
                selected = selected, canSpeak = (requestedVoice==null || selected!=null) && (selected != null || defaultSupported),
                notice = when {
                    saved != null && selected == null && (selectedEngine!=NeuralEngine.PACKAGE || savedEngine==currentEngine) -> text(R.string.voice_missing_fallback)
                    !defaultSupported && selected == null -> text(R.string.voice_chinese_missing)
                    else -> null
                })
            engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) = Unit
                override fun onStop(utteranceId: String?, interrupted: Boolean) {
                    handler.post {
                        if (!closed && playback.finish(utteranceId, true)) {
                            releaseFocus()
                            mutable.value = mutable.value.copy(speaking = false)
                        }
                    }
                }
                override fun onDone(utteranceId: String?) { handler.post { finished(utteranceId, false) } }
                @Deprecated("Platform callback")
                override fun onError(utteranceId: String?) { handler.post { finished(utteranceId, true) } }
                override fun onError(utteranceId: String?, errorCode: Int) { handler.post { finished(utteranceId, true) } }
            })
        } catch (_: RuntimeException) {
            mutable.value = mutable.value.copy(loading = false, ready = false, canSpeak = false,
                notice = text(R.string.voice_engine_missing))
        }
    }

    private fun finished(id: String?, error: Boolean) {
        if (closed || !playback.finish(id, error)) return
        if (error) {
            stop()
            mutable.value = mutable.value.copy(notice = text(R.string.voice_play_failed))
        } else {
            releaseFocus()
            mutable.value = mutable.value.copy(speaking = false)
        }
    }

    fun autoRead(enabled: Boolean) {
        if (closed) return
        // Keep the commit result: KTX edit(commit=true) cannot report persistence failure.
        if (!prefs.edit().putBoolean("auto_read", enabled).commit()) {
            mutable.value = mutable.value.copy(notice = text(R.string.voice_save_failed)); return
        }
        if (!enabled) stop()
        mutable.value = mutable.value.copy(autoRead = enabled)
    }

    fun select(name: String?) {
        if (closed || !mutable.value.ready) return
        if (name != null && mutable.value.voices.none { it.name == name }) return
        // A failed write must leave the current voice selection unchanged.
        if (!prefs.edit().putString("voice_name", name).putString("voice_engine", selectedEngine ?: tts?.defaultEngine).commit()) {
            mutable.value = mutable.value.copy(notice = text(R.string.voice_save_failed)); return
        }
        stop()
        mutable.value = mutable.value.copy(selected = name, canSpeak = name != null || defaultSupported,
            notice = if (name == null && !defaultSupported) text(R.string.voice_chinese_missing) else null)
    }

    fun speak(text: String) {
        if (closed) return
        if (!mutable.value.ready) {
            mutable.value = mutable.value.copy(notice = this.text(R.string.voice_engine_missing)); return
        }
        if (!mutable.value.canSpeak || text.isBlank()) {
            mutable.value = mutable.value.copy(notice = this.text(R.string.voice_chinese_missing)); return
        }
        stop()
        val engine = tts ?: return
        try {
            val selected = mutable.value.selected
            if (selectedEngine==NeuralEngine.PACKAGE) {
                check(NeuralEngine.trusted(app))
                check(engine.voices.orEmpty().any { NeuralEngine.accepts(selectedEngine,it.name) })
            }
            val applied = if (selected == null && selectedEngine==NeuralEngine.PACKAGE)
                engine.defaultVoice?.takeIf { NeuralEngine.accepts(selectedEngine,it.name) }?.let(engine::setVoice) ?: TextToSpeech.ERROR
                else if (selected == null) engine.setLanguage(Locale.SIMPLIFIED_CHINESE)
                else engine.voices.orEmpty().firstOrNull { it.name == selected }?.let(engine::setVoice)
                    ?: TextToSpeech.ERROR
            if (applied < 0) { mutable.value = mutable.value.copy(notice = this.text(R.string.voice_play_failed)); return }
            val chunks = VoiceText.chunks(text, if(selectedEngine==NeuralEngine.PACKAGE) 200 else TextToSpeech.getMaxSpeechInputLength())
            val batch = playback.begin(chunks.size)
            val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                .setAudioAttributes(attributes)
                .setOnAudioFocusChangeListener({ change -> if (change < 0 && playback.current(batch.ticket)) stop() }, handler).build()
            if (audio.requestAudioFocus(request) != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                playback.cancel()
                mutable.value = mutable.value.copy(notice = this.text(R.string.voice_focus_failed)); return
            }
            focus = request
            mutable.value = mutable.value.copy(speaking = true, notice = null)
            timeout = Runnable {
                if (playback.current(batch.ticket)) { stop(); mutable.value=mutable.value.copy(notice=this.text(R.string.voice_play_failed)) }
            }.also { handler.postDelayed(it, (text.length*500L+30000L).coerceIn(60000L,240000L)) }
            for ((index, chunk) in chunks.withIndex()) {
                if (!playback.current(batch.ticket)) return
                val result = engine.speak(chunk, if (index == 0) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD,
                    null, batch.utteranceIds[index])
                if (result == TextToSpeech.ERROR) {
                    stop(); mutable.value = mutable.value.copy(notice = this.text(R.string.voice_play_failed)); return
                }
            }
        } catch (_: RuntimeException) {
            stop(); mutable.value = mutable.value.copy(notice = this.text(R.string.voice_play_failed))
        }
    }

    private fun releaseFocus() {
        timeout?.let(handler::removeCallbacks); timeout=null
        focus?.let { runCatching { audio.abandonAudioFocusRequest(it) } }
        focus = null
    }
    fun stop() {
        timeout?.let(handler::removeCallbacks); timeout=null
        playback.cancel()
        runCatching { tts?.stop() }
        releaseFocus()
        mutable.value = mutable.value.copy(speaking = false)
    }
    override fun close() {
        if (closed) return
        closed = true
        initTimeout?.let(handler::removeCallbacks); initTimeout=null
        initTicket++
        stop()
        runCatching { tts?.shutdown() }
        tts = null
    }
}
