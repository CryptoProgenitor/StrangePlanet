package com.quokkalabs.strangeplanet.ui.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.quokkalabs.strangeplanet.data.model.*
import com.quokkalabs.strangeplanet.domain.StrangeMatchEngine
import org.json.JSONArray
import org.json.JSONObject
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

    /**
     * The ViewModel is Activity-scoped, so a preserved game stays live in
     * memory. On screen (re)entry, drop back to a READY board when a
     * snapshot exists so the resume prompt can appear instead of the stale
     * in-progress board (which would otherwise orphan the snapshot).
     */
    fun onEnterScreen() {
        if (hasSavedSession() && _state.value.phase != StrangeMatchPhase.READY) {
            _state.value = StrangeMatchState(highScore = prefs.getInt("high_score", 0))
        }
    }

    /** Run a swap so a thrown step can't strand the board in ANIMATING. */
    private fun launchSwap(block: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                block()
            } catch (t: Throwable) {
                if (t is kotlinx.coroutines.CancellationException) throw t
                Log.e("StrangeMatchViewModel", "swap failed", t)
                _state.update {
                    val safeGrid = if (it.grid.any { row -> row.any { cell -> cell == null } })
                        StrangeMatchEngine.refill(it.grid) else it.grid
                    it.copy(
                        phase = StrangeMatchPhase.PLAYING,
                        grid = safeGrid,
                        matchedCells = emptySet(),
                        bombExplosionCells = emptySet(),
                        selectedCell = null,
                    )
                }
            }
        }
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
                launchSwap { doSwap(selected, tapped) }
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
        launchSwap { doSwap(from, to) }
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
        if (cascade > 20) {
            _state.update { it.copy(phase = if (it.movesLeft <= 0) StrangeMatchPhase.GAME_OVER else StrangeMatchPhase.PLAYING) }
            return
        }
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

    // ── Session preservation ────────────────────────────────────────────────

    fun hasSavedSession(): Boolean = prefs.contains(KEY_SESSION)

    fun saveSession() {
        val s = _state.value
        if (s.phase != StrangeMatchPhase.PLAYING) return
        commitHighScore(s.score)
        val root = JSONObject().apply {
            put("score", s.score)
            put("movesLeft", s.movesLeft)
            put("scoreTarget", s.scoreTarget)
            put("level", s.level)
        }
        val rows = JSONArray()
        for (row in s.grid) {
            val rArr = JSONArray()
            for (cell in row) {
                if (cell == null) {
                    rArr.put(JSONObject.NULL)
                } else {
                    rArr.put(JSONObject().apply {
                        put("t", cell.type.name)
                        put("k", cell.kind.name)
                    })
                }
            }
            rows.put(rArr)
        }
        root.put("grid", rows)
        prefs.edit().putString(KEY_SESSION, root.toString()).apply()
    }

    fun resumeSession() {
        val raw = prefs.getString(KEY_SESSION, null) ?: return
        val restored = runCatching {
            val root = JSONObject(raw)
            val rows = root.getJSONArray("grid")
            val grid = ArrayList<List<Tile?>>()
            for (r in 0 until rows.length()) {
                val rArr = rows.getJSONArray(r)
                val rowList = ArrayList<Tile?>()
                for (c in 0 until rArr.length()) {
                    if (rArr.isNull(c)) {
                        rowList.add(null)
                    } else {
                        val o = rArr.getJSONObject(c)
                        rowList.add(
                            Tile(
                                type = TileType.valueOf(o.getString("t")),
                                kind = TileKind.valueOf(o.getString("k")),
                            ),
                        )
                    }
                }
                grid.add(rowList)
            }
            _state.value = StrangeMatchState(
                grid = grid,
                score = root.getInt("score"),
                highScore = prefs.getInt("high_score", 0),
                movesLeft = root.getInt("movesLeft"),
                scoreTarget = root.getInt("scoreTarget"),
                level = root.getInt("level"),
                phase = StrangeMatchPhase.PLAYING,
            )
        }.isSuccess
        // Only drop the snapshot if it actually restored.
        if (restored) discardSavedSession()
    }

    fun discardSavedSession() {
        prefs.edit().remove(KEY_SESSION).apply()
    }

    private companion object {
        const val KEY_SESSION = "saved_session"
    }
}
