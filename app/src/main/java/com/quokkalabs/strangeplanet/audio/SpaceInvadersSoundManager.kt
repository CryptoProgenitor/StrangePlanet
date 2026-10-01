package com.quokkalabs.strangeplanet.audio

import android.media.ToneGenerator

/**
 * Synthesized sound effects for Descending Entity Defence.
 */
class SpaceInvadersSoundManager {

    // Beeps are played on a background thread (see ToneSfx).
    private val tones = ToneSfx(50)

    /** Player fires a projectile — short high blip. */
    fun playShoot() {
        tones.play(ToneGenerator.TONE_DTMF_A, 30)
    }

    /** Invader destroyed — satisfying mid-tone pop. */
    fun playKill() {
        tones.play(ToneGenerator.TONE_DTMF_9, 50)
    }

    /** Player hit by enemy fire — low thud. */
    fun playPlayerHit() {
        tones.play(ToneGenerator.TONE_DTMF_0, 120)
    }

    /** Wave cleared — celebratory double beep. */
    fun playWaveClear() {
        tones.play(ToneGenerator.TONE_PROP_BEEP2, 200)
    }

    /** Game over — descending error tone. */
    fun playGameOver() {
        tones.play(ToneGenerator.TONE_SUP_ERROR, 300)
    }

    fun release() {
        tones.release()
    }
}
