package com.quokkalabs.strangeplanet.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.quokkalabs.strangeplanet.audio.SpaceInvadersSoundManager
import com.quokkalabs.strangeplanet.data.model.DifficultyLevel
import com.quokkalabs.strangeplanet.data.model.SIPhase
import com.quokkalabs.strangeplanet.data.model.SISettings
import com.quokkalabs.strangeplanet.data.model.SpaceInvadersState
import com.quokkalabs.strangeplanet.domain.SpaceInvadersEngine
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class SpaceInvadersViewModel(application: Application) : AndroidViewModel(application) {

    private var engine: SpaceInvadersEngine? = null
    private val _state = MutableStateFlow(SpaceInvadersState())
    val state: StateFlow<SpaceInvadersState> = _state.asStateFlow()

    private val _siSettings = MutableStateFlow(SISettings())
    val siSettings: StateFlow<SISettings> = _siSettings.asStateFlow()

    private val soundManager = SpaceInvadersSoundManager()

    private var playerTouchX: Float? = null
    private var isTouching = false

    // Track previous state for sound triggers
    private var prevScore = 0
    private var prevLives = 3
    private var prevPhase = SIPhase.READY
    private var prevFireCounter = 0

    private var screenW = 0f
    private var screenH = 0f

    fun initGame(screenWidth: Float, screenHeight: Float) {
        if (engine != null) return
        screenW = screenWidth
        screenH = screenHeight
        val eng = SpaceInvadersEngine(screenWidth, screenHeight, _siSettings.value.difficulty)
        engine = eng
        _state.value = eng.createInitialState()

        viewModelScope.launch {
            while (isActive) {
                delay(16)
                val e = engine ?: continue
                val touchX = if (isTouching) playerTouchX else null
                val before = _state.value
                _state.update { e.update(it, touchX) }
                val after = _state.value

                // ── Sound triggers ─────────────────────────────────────
                if (_siSettings.value.soundEnabled) {
                    // Kill: score increased
                    if (after.score > before.score) {
                        soundManager.playKill()
                    }
                    // Player hit: lives decreased
                    if (after.lives < before.lives) {
                        soundManager.playPlayerHit()
                    }
                    // Shoot: fireCounter wrapped back to 0
                    if (before.fireCounter > 0 && after.fireCounter == 0 &&
                        after.phase == SIPhase.PLAYING
                    ) {
                        soundManager.playShoot()
                    }
                    // Wave clear
                    if (before.phase == SIPhase.PLAYING &&
                        after.phase == SIPhase.WAVE_CLEAR
                    ) {
                        soundManager.playWaveClear()
                    }
                    // Game over
                    if (before.phase != SIPhase.GAME_OVER &&
                        after.phase == SIPhase.GAME_OVER
                    ) {
                        soundManager.playGameOver()
                    }
                }

                // Auto-advance after wave clear
                if (_state.value.phase == SIPhase.WAVE_CLEAR) {
                    delay(1500)
                    val s = _state.value
                    _state.value = e.createInitialState(
                        wave = s.wave + 1,
                        score = s.score,
                        lives = s.lives,
                    ).copy(phase = SIPhase.PLAYING)
                }
            }
        }
    }

    fun onTouch(x: Float) {
        playerTouchX = x
        isTouching = true
    }

    fun onTouchUp() {
        isTouching = false
    }

    fun onTapToStart() {
        val e = engine ?: return
        val s = _state.value
        when (s.phase) {
            SIPhase.READY -> _state.value = e.startGame(s)
            SIPhase.GAME_OVER -> {
                _state.value = e.createInitialState()
                _state.value = e.startGame(_state.value)
            }
            else -> {}
        }
    }

    fun resetGame() {
        val e = engine ?: return
        _state.value = e.createInitialState()
        isTouching = false
        playerTouchX = null
    }

    fun pauseGame() {
        val e = engine ?: return
        _state.update { e.pauseGame(it) }
    }

    fun resumeGame() {
        val e = engine ?: return
        _state.update { e.resumeGame(it) }
    }

    // ── Settings setters ───────────────────────────────────────────────────

    fun setSoundEnabled(enabled: Boolean) {
        _siSettings.update { it.copy(soundEnabled = enabled) }
    }

    fun setShowSayings(show: Boolean) {
        _siSettings.update { it.copy(showSayings = show) }
    }

    fun setDifficulty(level: DifficultyLevel) {
        _siSettings.update { it.copy(difficulty = level) }
        // Recreate engine with new difficulty and reset game
        if (screenW > 0f) {
            val eng = SpaceInvadersEngine(screenW, screenH, level)
            engine = eng
            _state.value = eng.createInitialState()
        }
    }

    override fun onCleared() {
        super.onCleared()
        soundManager.release()
    }
}
