package com.quokkalabs.strangeplanet.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.quokkalabs.strangeplanet.data.model.*
import com.quokkalabs.strangeplanet.domain.StrangeMatchEngine
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class StrangeMatchViewModel(app: Application) : AndroidViewModel(app) {

    private val prefs = app.getSharedPreferences("strange_match_prefs", 0)

    private val _state = MutableStateFlow(
        StrangeMatchState(highScore = prefs.getInt("high_score", 0))
    )
    val state: StateFlow<StrangeMatchState> = _state.asStateFlow()

    fun startGame() {
        _state.value = StrangeMatchState(
            grid = StrangeMatchEngine.createGrid(),
            highScore = prefs.getInt("high_score", 0),
            phase = StrangeMatchPhase.PLAYING,
            movesLeft = 30,
            scoreTarget = 5000,
            level = 1,
        )
    }

    fun resetGame() {
        _state.value = StrangeMatchState(highScore = prefs.getInt("high_score", 0))
    }

    fun onCellTapped(row: Int, col: Int) {
        val s = _state.value
        if (s.phase != StrangeMatchPhase.PLAYING) return
        val tapped = row to col
        val selected = s.selectedCell

        when {
            selected == null -> _state.update { it.copy(selectedCell = tapped) }
            selected == tapped -> _state.update { it.copy(selectedCell = null) }
            !StrangeMatchEngine.isAdjacent(selected, tapped) ->
                _state.update { it.copy(selectedCell = tapped) }
            else -> {
                _state.update { it.copy(selectedCell = null, phase = StrangeMatchPhase.ANIMATING) }
                viewModelScope.launch { doSwap(selected, tapped) }
            }
        }
    }

    fun onSwipeTo(fromRow: Int, fromCol: Int, toRow: Int, toCol: Int) {
        val s = _state.value
        if (s.phase != StrangeMatchPhase.PLAYING) return
        if (toRow !in 0 until SM_ROWS || toCol !in 0 until SM_COLS) return
        val from = fromRow to fromCol
        val to = toRow to toCol
        if (!StrangeMatchEngine.isAdjacent(from, to)) return
        _state.update { it.copy(selectedCell = null, phase = StrangeMatchPhase.ANIMATING) }
        viewModelScope.launch { doSwap(from, to) }
    }

    private suspend fun doSwap(a: Pair<Int, Int>, b: Pair<Int, Int>) {
        val s = _state.value
        val swapped = StrangeMatchEngine.swap(s.grid, a, b)
        val result = StrangeMatchEngine.findMatchResult(swapped)

        if (result.matched.isEmpty()) {
            _state.update { it.copy(grid = swapped) }
            delay(160)
            _state.update { it.copy(grid = s.grid, phase = StrangeMatchPhase.PLAYING) }
            return
        }

        _state.update { it.copy(grid = swapped, movesLeft = s.movesLeft - 1) }
        delay(80)
        resolveMatches(cascade = 1)
    }

    private suspend fun resolveMatches(cascade: Int) {
        val s = _state.value
        val result = StrangeMatchEngine.findMatchResult(s.grid)
        if (result.matched.isEmpty()) {
            val over = s.movesLeft <= 0
            if (over) commitHighScore(s.score)
            _state.update { it.copy(phase = if (over) StrangeMatchPhase.GAME_OVER else StrangeMatchPhase.PLAYING) }
            return
        }

        val expanded = StrangeMatchEngine.expandWithBombs(s.grid, result.matched)
        val bombBonus = expanded.size - result.matched.size

        _state.update { it.copy(
            matchedCells = result.matched,
            bombExplosionCells = expanded - result.matched,
        )}
        delay(340)

        val gained = StrangeMatchEngine.scoreForMatch(result.matched.size, bombBonus, cascade)
        val newScore = s.score + gained
        commitHighScore(newScore)

        var newGrid = StrangeMatchEngine.clearCells(s.grid, expanded)
        val bombsToPlant = result.bombs.filterKeys { it in result.matched }
        newGrid = StrangeMatchEngine.plantBombs(newGrid, bombsToPlant)
        newGrid = StrangeMatchEngine.applyGravity(newGrid)

        _state.update { it.copy(
            grid = newGrid,
            score = newScore,
            highScore = prefs.getInt("high_score", 0),
            matchedCells = emptySet(),
            bombExplosionCells = emptySet(),
        )}
        delay(220)

        newGrid = StrangeMatchEngine.refill(newGrid)
        _state.update { it.copy(grid = newGrid) }
        delay(180)

        // Level up?
        val cur = _state.value
        if (cur.score >= cur.scoreTarget) {
            val newLevel = cur.level + 1
            _state.update { it.copy(
                grid = StrangeMatchEngine.createGrid(),
                level = newLevel,
                scoreTarget = 5000 * newLevel,
                movesLeft = 30,
            )}
            delay(300)
        }

        if (_state.value.movesLeft <= 0) {
            commitHighScore(_state.value.score)
            _state.update { it.copy(phase = StrangeMatchPhase.GAME_OVER) }
            return
        }

        resolveMatches(cascade + 1)
    }

    private fun commitHighScore(score: Int) {
        val best = prefs.getInt("high_score", 0)
        if (score > best) {
            prefs.edit().putInt("high_score", score).apply()
            _state.update { it.copy(highScore = score) }
        }
    }
}
