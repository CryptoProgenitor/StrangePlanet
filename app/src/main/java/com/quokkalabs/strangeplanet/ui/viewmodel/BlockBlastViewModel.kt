package com.quokkalabs.strangeplanet.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.quokkalabs.strangeplanet.data.model.BlockBlastPhase
import com.quokkalabs.strangeplanet.data.model.BlockBlastState
import com.quokkalabs.strangeplanet.domain.BlockBlastEngine
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class BlockBlastViewModel(application: Application) : AndroidViewModel(application) {

    private val engine = BlockBlastEngine()
    private val prefs = application.getSharedPreferences("block_blast_prefs", Application.MODE_PRIVATE)
    private var highScore = prefs.getInt("high_score", 0)

    private val _state = MutableStateFlow(BlockBlastState(highScore = highScore))
    val state: StateFlow<BlockBlastState> = _state.asStateFlow()

    fun startGame() {
        _state.value = engine.initial(highScore)
    }

    fun tryPlace(trayIndex: Int, row: Int, col: Int): Boolean {
        val s = _state.value
        if (s.phase != BlockBlastPhase.PLAYING) return false
        val piece = s.tray.getOrNull(trayIndex) ?: return false
        if (!engine.canPlace(s.grid, piece, row, col)) return false

        val placed = engine.place(s.grid, piece, row, col)
        val newTray = s.tray.toMutableList().apply { set(trayIndex, null) }
        val (clearedGrid, justCleared, lines) = engine.clearLines(placed)
        val newCombo = if (lines > 0) s.combo + 1 else 0
        val delta = engine.score(piece.cells.size, lines, s.combo)
        val newScore = s.score + delta
        // Replenish when all three pieces have been placed
        val finalTray = if (newTray.all { it == null }) engine.generateTray() else newTray

        if (newScore > highScore) {
            highScore = newScore
            prefs.edit().putInt("high_score", highScore).apply()
        }

        if (lines > 0) {
            // Show pre-clear flash, then apply cleared grid
            _state.value = s.copy(
                grid = placed,
                tray = finalTray,
                justCleared = justCleared,
                score = newScore,
                highScore = highScore,
                combo = newCombo,
            )
            viewModelScope.launch {
                delay(340)
                val isOver = !engine.canAnyFit(clearedGrid, _state.value.tray)
                _state.value = _state.value.copy(
                    grid = clearedGrid,
                    justCleared = emptySet(),
                    phase = if (isOver) BlockBlastPhase.GAME_OVER else BlockBlastPhase.PLAYING,
                )
            }
        } else {
            val isOver = !engine.canAnyFit(placed, finalTray)
            _state.value = s.copy(
                grid = placed,
                tray = finalTray,
                justCleared = emptySet(),
                score = newScore,
                highScore = highScore,
                combo = newCombo,
                phase = if (isOver) BlockBlastPhase.GAME_OVER else BlockBlastPhase.PLAYING,
            )
        }
        return true
    }

    fun canPlace(trayIndex: Int, row: Int, col: Int): Boolean {
        val s = _state.value
        val piece = s.tray.getOrNull(trayIndex) ?: return false
        return engine.canPlace(s.grid, piece, row, col)
    }
}
