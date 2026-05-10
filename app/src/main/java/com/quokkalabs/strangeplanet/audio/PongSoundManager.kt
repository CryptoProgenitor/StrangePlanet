package com.quokkalabs.strangeplanet.audio

import android.media.AudioManager
import android.media.ToneGenerator

/**
 * Synthesized ping/pong sounds for Recreational Sphere Deflection.
 * Uses ToneGenerator DTMF tones at varying pitches.
 */
class PongSoundManager {

    private var toneGenerator: ToneGenerator? = try {
        ToneGenerator(AudioManager.STREAM_MUSIC, 60)
    } catch (_: Exception) {
        null
    }

    /** Player paddle hit — bright high ping. */
    fun playPlayerHit() {
        toneGenerator?.startTone(ToneGenerator.TONE_DTMF_9, 50)
    }

    /** AI/opponent paddle hit — deeper pong. */
    fun playAiHit() {
        toneGenerator?.startTone(ToneGenerator.TONE_DTMF_1, 50)
    }

    /** Ball bouncing off side wall — short blip. */
    fun playWallBounce() {
        toneGenerator?.startTone(ToneGenerator.TONE_DTMF_D, 25)
    }

    /** Point scored — longer celebratory tone. */
    fun playScore() {
        toneGenerator?.startTone(ToneGenerator.TONE_PROP_BEEP, 120)
    }

    fun release() {
        toneGenerator?.release()
        toneGenerator = null
    }
}
