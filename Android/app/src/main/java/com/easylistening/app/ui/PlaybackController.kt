package com.easylistening.app.ui

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.easylistening.app.data.RepeatConfig
import com.easylistening.app.data.Repo
import com.easylistening.app.data.Settings
import com.easylistening.app.data.Word
import java.util.Locale

/**
 * 应用级播放控制器：持有 TTS 与播放器，界面与前台服务共用同一实例，
 * 因此离开页面 / 进入后台仍持续播放。
 */
class PlaybackController(
    private val context: Context,
    private val handler: Handler
) {
    val player = ListenPlayer(handler)
    private var tts: TextToSpeech? = null
    private var initStarted = false
    private var pendingPlay = false

    var cfg by mutableStateOf(RepeatConfig())
    var autoPlay by mutableStateOf(true)
    var active by mutableStateOf(false)
    var current by mutableStateOf<Word?>(null)
    var total by mutableStateOf(0)
    var remaining by mutableStateOf(0)
    var paused by mutableStateOf(false)
    var ready by mutableStateOf(false)
    var needsInstall by mutableStateOf(false)

    /** 状态变化回调（供前台服务刷新通知）。 */
    var onChanged: (() -> Unit)? = null

    fun init() {
        if (initStarted) return
        initStarted = true
        player.onAdvance = { if (autoPlay) next() }

        var ref: TextToSpeech? = null
        val created = TextToSpeech(context.applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val engine = ref
                val en = engine?.isLanguageAvailable(Locale.US)
                    ?: TextToSpeech.LANG_NOT_SUPPORTED
                val zh = engine?.isLanguageAvailable(Locale.CHINA)
                    ?: TextToSpeech.LANG_NOT_SUPPORTED
                if (en < TextToSpeech.LANG_AVAILABLE || zh < TextToSpeech.LANG_AVAILABLE) {
                    needsInstall = true
                }
                engine?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {}
                    override fun onDone(utteranceId: String?) {
                        handler.post { player.onUtteranceDone() }
                    }

                    override fun onError(utteranceId: String?) {
                        handler.post { player.onUtteranceDone() }
                    }
                })
                player.tts = engine
                ready = true
                if (active && autoPlay && pendingPlay) {
                    pendingPlay = false
                    playCurrent()
                }
            } else {
                needsInstall = true
            }
        }
        ref = created
        tts = created
    }

    fun loadCfg(context: Context) {
        cfg = Settings.load(context)
    }

    private fun applyCfg() {
        player.wordRepeat = cfg.wordRepeat
        player.wordIntervalMs = cfg.wordIntervalMs
        player.speakTranslation = cfg.speakTranslation
        player.transRepeat = cfg.transRepeat
        player.transIntervalMs = cfg.transIntervalMs
    }

    private fun changed() {
        onChanged?.invoke()
    }

    fun start(bookId: Long, daily: Int): Boolean {
        Repo.scheduleConfig(bookId, daily)
        if (Repo.scheduleStart() != 0) {
            return false
        }
        total = Repo.scheduleTotal()
        remaining = Repo.scheduleRemaining()
        current = Repo.scheduleCurrentWord()
        active = true
        paused = false
        if (autoPlay) {
            if (ready) playCurrent() else pendingPlay = true
        } else {
            player.stop()
        }
        changed()
        return true
    }

    fun playCurrent() {
        val w = current ?: return
        applyCfg()
        player.play(w.word, w.translation)
    }

    fun next() {
        if (!active) return
        Repo.scheduleCommit()
        remaining = Repo.scheduleRemaining()
        current = Repo.scheduleCurrentWord()
        paused = false
        if (current != null && autoPlay) playCurrent() else player.stop()
        changed()
    }

    fun prev() {
        if (!active) return
        if (Repo.schedulePrev() < 0) return
        remaining = Repo.scheduleRemaining()
        current = Repo.scheduleCurrentWord()
        paused = false
        if (current != null) playCurrent()
        changed()
    }

    fun togglePause() {
        if (!active) return
        if (paused) {
            player.resume()
            paused = false
        } else {
            player.pause()
            paused = true
        }
        changed()
    }

    fun replay() {
        if (!active) return
        val w = current
        if (w != null) {
            paused = false
            playCurrent()
        } else if (Repo.scheduleRestart() == 0) {
            total = Repo.scheduleTotal()
            remaining = Repo.scheduleRemaining()
            current = Repo.scheduleCurrentWord()
            paused = false
            if (current != null) playCurrent()
        }
        changed()
    }

    fun stop() {
        player.stop()
        active = false
        paused = false
        pendingPlay = false
        current = null
        changed()
    }
}

/** 进程级单例持有者，在 Application 中初始化。 */
object PlaybackHolder {
    lateinit var controller: PlaybackController
        private set

    fun init(context: Context) {
        if (::controller.isInitialized) return
        controller = PlaybackController(
            context.applicationContext,
            Handler(Looper.getMainLooper())
        )
        controller.init()
        controller.loadCfg(context.applicationContext)
    }
}
