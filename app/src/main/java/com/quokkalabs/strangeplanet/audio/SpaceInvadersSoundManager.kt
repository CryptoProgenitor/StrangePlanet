package com.quokkalabs.strangeplanet.audio

import android.media.AudioManager
import android.media.ToneGenerator

/**
 * Synthesized sound effects for Descending Entity Defence.
 */
class SpaceInvadersSoundManager {

    private var toneGenerator: ToneGenerator? = try {
        ToneGenerator(AudioManager.STREAM_MUSIC, 50)
    } catch (_: Exception) {
        null
    }

    /** Player fires a projectile — short high blip. */
    fun playShoot() {
        toneGenerator?.startTone(ToneGenerator.TONE_DTMF_A, 30)
    }

    /** Invader destroyed — satisfying mid-tone pop. */
    fun playKill() {
        toneGenerator?.startTone(ToneGenerator.TONE_DTMF_9, 50)
    }

    /** Player hit by enemy fire — low thud. */
    fun playPlayerHit() {
        toneGenerator?.startTone(ToneGenerator.TONE_DTMF_0, 120)
    }

    /** Wave cleared — celebratory double beep. */
    fun playWaveClear() {
        toneGenerator?.startTone(ToneGenerator.TONE_PROP_BEEP2, 200)
    }

    /** Game over — descending error tone. */
    fun playGameOver() {
        toneGenerator?.startTone(ToneGenerator.TONE_SUP_ERROR, 300)
    }

    fun release() {
        toneGenerator?.release()
        toneGenerator = null
    }
}
