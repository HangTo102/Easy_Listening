package com.easylistening.app.ui

import android.os.Handler
import android.speech.tts.TextToSpeech
import java.util.Locale

/**
 * 复读播放器：按配置把"单词 N 遍 + 释义 M 遍（各带间隔）"排成一串 TTS 朗读，
 * 全部读完调用 onAdvance 切下一个词。支持暂停/继续。
 */
class ListenPlayer(private val handler: Handler) {

    var tts: TextToSpeech? = null
    var wordRepeat = 3
    var wordIntervalMs = 1000
    var speakTranslation = true
    var transRepeat = 3
    var transIntervalMs = 1000
    var onAdvance: () -> Unit = {}

    private var word = ""
    private var translation = ""
    private var phase = 0 // 0 = 单词, 1 = 释义
    private var phaseLeft = 0
    private var active = false
    private var paused = false

    val isPaused: Boolean get() = paused

    /** 从头开始播一个词。 */
    fun play(w: String, t: String) {
        handler.removeCallbacksAndMessages(null)
        word = w
        translation = t
        phase = 0
        phaseLeft = wordRepeat.coerceAtLeast(1)
        active = true
        paused = false
        speakPhase()
    }

    fun pause() {
        if (!active || paused) {
            return
        }
        paused = true
        handler.removeCallbacksAndMessages(null)
        tts?.stop()
    }

    fun resume() {
        if (!active || !paused) {
            return
        }
        paused = false
        speakPhase()
    }

    fun stop() {
        active = false
        paused = false
        handler.removeCallbacksAndMessages(null)
        tts?.stop()
    }

    fun onUtteranceDone() {
        if (!active || paused) {
            return
        }
        val interval = if (phase == 0) wordIntervalMs else transIntervalMs
        handler.postDelayed({ step() }, interval.toLong())
    }

    private fun step() {
        if (!active || paused) {
            return
        }
        if (phaseLeft > 1) {
            phaseLeft--
            speakPhase()
        } else if (phase == 0 && speakTranslation && translation.isNotBlank()) {
            phase = 1
            phaseLeft = transRepeat.coerceAtLeast(1)
            speakPhase()
        } else {
            active = false
            onAdvance()
        }
    }

    private fun speakPhase() {
        val engine = tts ?: return
        if (phase == 0) {
            engine.language = Locale.US
            engine.speak(word, TextToSpeech.QUEUE_FLUSH, null, "es_${System.nanoTime()}")
        } else {
            engine.language = Locale.CHINA
            engine.speak(
                cleanForSpeech(translation),
                TextToSpeech.QUEUE_FLUSH,
                null,
                "es_${System.nanoTime()}"
            )
        }
    }
}
