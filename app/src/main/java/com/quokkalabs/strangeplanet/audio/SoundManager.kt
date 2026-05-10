package com.quokkalabs.strangeplanet.audio

import android.content.Context
import android.media.SoundPool
import com.quokkalabs.strangeplanet.R
import com.quokkalabs.strangeplanet.data.model.CreatureType

class SoundManager(context: Context) {

    private var ready = false
    private val soundPool = SoundPool.Builder().setMaxStreams(4).build().apply {
        setOnLoadCompleteListener { _, _, status -> if (status == 0) ready = true }
    }

    private val soundIds: Map<Int, Int> = mapOf(
        R.raw.sp_beep_boop to soundPool.load(context, R.raw.sp_beep_boop, 1),
        R.raw.sp_meow to soundPool.load(context, R.raw.sp_meow, 1),
        R.raw.sp_woof to soundPool.load(context, R.raw.sp_woof, 1),
        R.raw.sp_neigh to soundPool.load(context, R.raw.sp_neigh, 1),
    )

    fun play(type: CreatureType) {
        if (!ready) return
        val resId = type.soundRes ?: return
        val poolId = soundIds[resId] ?: return
        soundPool.play(poolId, 0.7f, 0.7f, 1, 0, 1f)
    }

    fun playRandom() {
        if (!ready) return
        val ids = soundIds.values.toList()
        if (ids.isNotEmpty()) {
            soundPool.play(ids.random(), 0.7f, 0.7f, 1, 0, 1f)
        }
    }

    fun release() {
        soundPool.release()
    }
}
