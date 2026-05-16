package com.quokkalabs.strangeplanet.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.quokkalabs.strangeplanet.data.model.MergePhase
import com.quokkalabs.strangeplanet.data.model.MergeState
import com.quokkalabs.strangeplanet.domain.MergeEngine
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class MergeViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs =
        application.getSharedPreferences("merge_prefs", Application.MODE_PRIVATE)

    private var engine: MergeEngine? = null
    private var highScore: Int = prefs.getInt(KEY_HIGH_SCORE, 0)

    private val _state = MutableStateFlow(MergeState(highScore = highScore))
    val state: StateFlow<MergeState> = _state.asStateFlow()

    @Volatile
    private var spoutX: Float = 0f

    @Volatile
    private var dropRequested = false

    private fun commitHighScore(score: Int) {
        if (score > highScore) {
            highScore = score
            prefs.edit().putInt(KEY_HIGH_SCORE, highScore).apply()
        }
    }

    fun initGame(screenWidth: Float, screenHeight: Float) {
        if (engine != null) return
        val eng = MergeEngine(screenWidth, screenHeight)
        engine = eng
        val initial = eng.createInitialState(highScore)
        spoutX = initial.spoutX
        _state.value = initial

        viewModelScope.launch {
            while (isActive) {
                delay(16)
                val e = engine ?: continue
                val drop = dropRequested
                dropRequested = false
                val updated = e.update(_state.value, spoutX, drop)
                _state.value = updated
                if (updated.phase == MergePhase.GAME_OVER) {
                    commitHighScore(updated.score)
                    _state.value = updated.copy(highScore = highScore)
                }
            }
        }
    }

    /** Re-aim the spout (called continuously while dragging). */
    fun onAim(x: Float) {
        spoutX = x
    }

    /** Pointer released — drop in play, or start/restart otherwise. */
    fun onRelease() {
        when (_state.value.phase) {
            MergePhase.PLAYING -> dropRequested = true
            MergePhase.READY, MergePhase.GAME_OVER -> startGame()
        }
    }

    private fun startGame() {
        val e = engine ?: return
        dropRequested = false
        _state.value = e.startGame(_state.value.copy(highScore = highScore))
    }

    fun resetGame() {
        val e = engine ?: return
        commitHighScore(_state.value.score)
        dropRequested = false
        val s = e.createInitialState(highScore)
        spoutX = s.spoutX
        _state.value = s
    }

    private companion object {
        const val KEY_HIGH_SCORE = "high_score"
    }
}
