package com.quokkalabs.strangeplanet.audio

import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Handler
import android.os.HandlerThread

/**
 * A [ToneGenerator] driven from a background thread.
 *
 * Creating a ToneGenerator and calling startTone() both block while the native audio
 * track starts — a few ms per beep, sometimes over 10 ms. Called from the game loop
 * that stalled the frame on the main thread for every shot, pellet and bounce.
 * Here every call is posted to one shared "sfx" thread and returns immediately.
 */
class ToneSfx(volume: Int) {
    private val handler = Handler(sfxThread.looper)

    // Only touched on the sfx thread.
    private var generator: ToneGenerator? = null
    private var released = false

    init {
        handler.post {
            generator = runCatching { ToneGenerator(AudioManager.STREAM_MUSIC, volume) }.getOrNull()
        }
    }

    fun play(tone: Int, durationMs: Int) {
        handler.post {
            if (!released) runCatching { generator?.startTone(tone, durationMs) }
        }
    }

    fun release() {
        handler.post {
            released = true
            generator?.release()
            generator = null
        }
    }

    private companion object {
        val sfxThread: HandlerThread by lazy { HandlerThread("sfx").apply { start() } }
    }
}
