package com.quokkalabs.strangeplanet.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.quokkalabs.strangeplanet.data.model.PacAvatar
import com.quokkalabs.strangeplanet.data.model.PacDir
import com.quokkalabs.strangeplanet.data.model.PacGameState
import com.quokkalabs.strangeplanet.data.model.PacPhase
import com.quokkalabs.strangeplanet.data.model.PacSettings
import com.quokkalabs.strangeplanet.domain.MazeEngine
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class PacViewModel(application: Application) : AndroidViewModel(application) {

    private var engine: MazeEngine? = null

    private val _state = MutableStateFlow(PacGameState())
    val state: StateFlow<PacGameState> = _state.asStateFlow()

    private val _pacSettings = MutableStateFlow(PacSettings())
    val pacSettings: StateFlow<PacSettings> = _pacSettings.asStateFlow()

    // Latest swipe-derived intent, consumed once by the next tick.
    private var pendingDir: PacDir? = null

    private var screenW = 0f
    private var screenH = 0f

    fun initGame(screenWidth: Float, screenHeight: Float) {
        if (engine != null) return
        screenW = screenWidth
        screenH = screenHeight
        val eng = MazeEngine(screenWidth, screenHeight)
        engine = eng
        _state.value = eng.createInitialState()

        viewModelScope.launch {
            while (isActive) {
                delay(16)
                val e = engine ?: continue
                val dir = pendingDir
                pendingDir = null
                _state.update { e.update(it, dir) }

                if (_state.value.phase == PacPhase.LEVEL_CLEARED) {
                    delay(1800)
                    val s = _state.value
                    _state.value = e.createInitialState(
                        level = s.level + 1,
                        score = s.score,
                        lives = s.lives,
                    ).copy(phase = PacPhase.PLAYING)
                }
            }
        }
    }

    /** A screen-wide swipe resolved to a cardinal direction → input queue. */
    fun onSwipe(dir: PacDir) {
        if (dir == PacDir.NONE) return
        pendingDir = dir
        // First swipe also kicks a READY game into motion.
        if (_state.value.phase == PacPhase.READY) onTapToStart()
    }

    fun onTapToStart() {
        val e = engine ?: return
        when (_state.value.phase) {
            PacPhase.READY -> _state.value = e.startGame(_state.value)
            PacPhase.GAME_OVER -> {
                _state.value = e.createInitialState()
                _state.value = e.startGame(_state.value)
            }
            else -> {}
        }
    }

    fun resetGame() {
        val e = engine ?: return
        _state.value = e.createInitialState()
        pendingDir = null
    }

    fun pauseGame() {
        val e = engine ?: return
        _state.update { e.pauseGame(it) }
    }

    fun resumeGame() {
        val e = engine ?: return
        _state.update { e.resumeGame(it) }
    }

    fun setSoundEnabled(enabled: Boolean) {
        _pacSettings.update { it.copy(soundEnabled = enabled) }
    }

    fun setShowSayings(show: Boolean) {
        _pacSettings.update { it.copy(showSayings = show) }
    }

    fun setAvatar(avatar: PacAvatar) {
        _pacSettings.update { it.copy(avatar = avatar) }
    }
}
