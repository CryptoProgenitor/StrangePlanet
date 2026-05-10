package com.quokkalabs.strangeplanet.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.quokkalabs.strangeplanet.StrangePlanetApp
import com.quokkalabs.strangeplanet.data.model.GamePhase
import com.quokkalabs.strangeplanet.data.model.PongGameState
import com.quokkalabs.strangeplanet.domain.PongEngine
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class PongViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as StrangePlanetApp
    private val soundManager = app.soundManager

    private var engine: PongEngine? = null
    private val _gameState = MutableStateFlow(PongGameState())
    val gameState: StateFlow<PongGameState> = _gameState.asStateFlow()

    private var playerTouchX: Float? = null
    private var lastPlayerHit = false
    private var lastAiHit = false

    fun initGame(screenWidth: Float, screenHeight: Float) {
        if (engine != null) return

        val eng = PongEngine(screenWidth, screenHeight)
        engine = eng
        _gameState.value = eng.createInitialState()

        viewModelScope.launch {
            while (isActive) {
                delay(16)
                val e = engine ?: continue
                val prevState = _gameState.value
                _gameState.update { e.update(it, playerTouchX) }

                val newState = _gameState.value
                if (newState.playerHitPulse > prevState.playerHitPulse) {
                    soundManager.playRandom()
                }
                if (newState.aiHitPulse > prevState.aiHitPulse) {
                    soundManager.playRandom()
                }
            }
        }
    }

    fun onTouch(x: Float) {
        playerTouchX = x
    }

    fun onTapToStart() {
        val e = engine ?: return
        val state = _gameState.value
        when (state.phase) {
            GamePhase.READY -> _gameState.value = e.startServe(state)
            GamePhase.GAME_OVER -> {
                _gameState.value = e.reset()
                _gameState.value = e.startServe(_gameState.value)
            }
            else -> {}
        }
    }
}
