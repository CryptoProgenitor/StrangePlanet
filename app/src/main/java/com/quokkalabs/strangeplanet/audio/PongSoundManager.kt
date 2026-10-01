package com.quokkalabs.strangeplanet.audio

import android.media.ToneGenerator

/**
 * Synthesized ping/pong sounds for Recreational Sphere Deflection.
 * Uses ToneGenerator DTMF tones at varying pitches.
 */
class PongSoundManager {

    // Beeps are played on a background thread (see ToneSfx).
    private val tones = ToneSfx(60)

    /** Player paddle hit — bright high ping. */
    fun playPlayerHit() {
        tones.play(ToneGenerator.TONE_DTMF_9, 50)
    }

    /** AI/opponent paddle hit — deeper pong. */
    fun playAiHit() {
        tones.play(ToneGenerator.TONE_DTMF_1, 50)
    }

    /** Ball bouncing off side wall — short blip. */
    fun playWallBounce() {
        tones.play(ToneGenerator.TONE_DTMF_D, 25)
    }

    /** Point scored — longer celebratory tone. */
    fun playScore() {
        tones.play(ToneGenerator.TONE_PROP_BEEP, 120)
    }

    fun release() {
        tones.release()
    }
}
