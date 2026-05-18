package com.quokkalabs.strangeplanet.ui.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.quokkalabs.strangeplanet.data.model.TetrisInput
import com.quokkalabs.strangeplanet.data.model.TetrisPhase
import com.quokkalabs.strangeplanet.data.model.TetrisState
import com.quokkalabs.strangeplanet.domain.TetrisEngine
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class TetrisViewModel(application: Application) : AndroidViewModel(application) {

    private val engine = TetrisEngine()
    private val prefs = application.getSharedPreferences("tetris_prefs", Application.MODE_PRIVATE)
    private var highScore = prefs.getInt("high_score", 0)

    private val _state = MutableStateFlow(TetrisState(highScore = highScore))
    val state: StateFlow<TetrisState> = _state.asStateFlow()

    @Volatile private var input = TetrisInput()
    private var loopJob: Job? = null

    fun startGame() {
        input = TetrisInput()
        _state.value = engine.initial(highScore)
        startLoop()
    }

    fun stopLoop() {
        loopJob?.cancel()
        loopJob = null
    }

    private fun startLoop() {
        if (loopJob?.isActive == true) return
        loopJob = viewModelScope.launch {
            while (isActive) {
                delay(16)
                try {
                    val s = _state.value
                    when (s.phase) {
                        TetrisPhase.PLAYING, TetrisPhase.LOCKING -> {
                            val currentInput = input
                            // Consume one-shot inputs
                            if (currentInput.rotate || currentInput.hardDrop ||
                                currentInput.moveLeft || currentInput.moveRight) {
                                input = currentInput.copy(
                                    rotate = false, hardDrop = false,
                                    moveLeft = false, moveRight = false,
                                )
                            }
                            val next = engine.update(s, currentInput)
                            if (next.score > highScore) {
                                highScore = next.score
                                prefs.edit().putInt("high_score", highScore).apply()
                            }
                            _state.value = next
                        }
                        TetrisPhase.CLEARING -> {
                            delay(380)  // clear flash animation time
                            if (_state.value.phase == TetrisPhase.CLEARING) {
                                val cleared = engine.applyClear(_state.value)
                                if (cleared.score > highScore) {
                                    highScore = cleared.score
                                    prefs.edit().putInt("high_score", highScore).apply()
                                }
                                _state.value = cleared
                            }
                        }
                        TetrisPhase.GAME_OVER -> {
                            if (_state.value.score > highScore) {
                                highScore = _state.value.score
                                prefs.edit().putInt("high_score", highScore).apply()
                            }
                        }
                        else -> {}
                    }
                } catch (t: Throwable) {
                    Log.e("TetrisViewModel", "frame failed", t)
                }
            }
        }
    }

    // ── Button inputs ──────────────────────────────────────────────────────

    fun setLeftDown(down: Boolean)  { input = input.copy(leftDown = down) }
    fun setRightDown(down: Boolean) { input = input.copy(rightDown = down) }
    fun setSoftDrop(down: Boolean)  { input = input.copy(softDrop = down) }

    fun onRotate()   { input = input.copy(rotate = true) }
    fun onHardDrop() { input = input.copy(hardDrop = true) }

    // ── Swipe inputs (one-shot single-cell moves) ──────────────────────────

    fun onSwipeLeft()  { input = input.copy(moveLeft = true) }
    fun onSwipeRight() { input = input.copy(moveRight = true) }
}
