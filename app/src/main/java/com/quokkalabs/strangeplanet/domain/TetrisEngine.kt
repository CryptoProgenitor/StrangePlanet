package com.quokkalabs.strangeplanet.domain

import com.quokkalabs.strangeplanet.data.model.Tetromino
import com.quokkalabs.strangeplanet.data.model.TetrisInput
import com.quokkalabs.strangeplanet.data.model.TetrisPhase
import com.quokkalabs.strangeplanet.data.model.TetrisState
import com.quokkalabs.strangeplanet.data.model.TetroType
import kotlin.random.Random

class TetrisEngine {

    companion object {
        const val ROWS = 20
        const val COLS = 10
        private const val LOCK_FRAMES = 30   // ~500 ms at 16 ms/frame
        private const val DAS_INITIAL = 11   // frames before auto-shift starts
        private const val DAS_REPEAT = 2     // frames between auto-shifts

        // Spawn cols: I uses 4-wide box, O uses 2-wide, others 3-wide
        private val SPAWN_COL = mapOf(
            TetroType.I to 3,
            TetroType.O to 4,
            TetroType.T to 3,
            TetroType.S to 3,
            TetroType.Z to 3,
            TetroType.J to 3,
            TetroType.L to 3,
        )

        // Rotation 0 cell offsets (row, col) within bounding box
        private val SPAWN_CELLS = mapOf(
            TetroType.I to listOf(1 to 0, 1 to 1, 1 to 2, 1 to 3),
            TetroType.O to listOf(0 to 0, 0 to 1, 1 to 0, 1 to 1),
            TetroType.T to listOf(0 to 1, 1 to 0, 1 to 1, 1 to 2),
            TetroType.S to listOf(0 to 1, 0 to 2, 1 to 0, 1 to 1),
            TetroType.Z to listOf(0 to 0, 0 to 1, 1 to 1, 1 to 2),
            TetroType.J to listOf(0 to 0, 1 to 0, 1 to 1, 1 to 2),
            TetroType.L to listOf(0 to 2, 1 to 0, 1 to 1, 1 to 2),
        )

        // Bounding-box size for each type (N×N)
        private val BOX_SIZE = mapOf(
            TetroType.I to 4,
            TetroType.O to 2,
            TetroType.T to 3, TetroType.S to 3, TetroType.Z to 3,
            TetroType.J to 3, TetroType.L to 3,
        )

        // All rotations derived algorithmically (CW: (r,c) → (c, N-1-r))
        private val ALL_ROTATIONS: Map<TetroType, List<List<Pair<Int, Int>>>> =
            TetroType.entries.associateWith { type ->
                val n = BOX_SIZE[type]!!
                val rot0 = SPAWN_CELLS[type]!!
                val rot1 = rot0.map { (r, c) -> c to (n - 1 - r) }
                val rot2 = rot1.map { (r, c) -> c to (n - 1 - r) }
                val rot3 = rot2.map { (r, c) -> c to (n - 1 - r) }
                listOf(rot0, rot1, rot2, rot3)
            }

        // SRS wall-kick offsets for non-I pieces: (from-rotation, to-rotation) → list of (dr, dc) to try
        private val KICKS_JLSTZ = mapOf(
            (0 to 1) to listOf(0 to 0, 0 to -1, -1 to -1,  2 to  0,  2 to -1),
            (1 to 0) to listOf(0 to 0, 0 to  1, -1 to  1,  2 to  0,  2 to  1),
            (1 to 2) to listOf(0 to 0, 0 to  1, -1 to  1,  2 to  0,  2 to  1),
            (2 to 1) to listOf(0 to 0, 0 to -1, -1 to -1,  2 to  0,  2 to -1),
            (2 to 3) to listOf(0 to 0, 0 to  1, -1 to  1,  2 to  0,  2 to  1),
            (3 to 2) to listOf(0 to 0, 0 to -1, -1 to -1,  2 to  0,  2 to -1),
            (3 to 0) to listOf(0 to 0, 0 to -1, -1 to -1,  2 to  0,  2 to -1),
            (0 to 3) to listOf(0 to 0, 0 to  1, -1 to  1,  2 to  0,  2 to  1),
        )
        private val KICKS_I = mapOf(
            (0 to 1) to listOf(0 to 0, 0 to -2, 0 to  1, -1 to -2,  2 to  1),
            (1 to 0) to listOf(0 to 0, 0 to  2, 0 to -1,  2 to  2, -1 to -1),
            (1 to 2) to listOf(0 to 0, 0 to -1, 0 to  2, -2 to -1,  1 to  2),
            (2 to 1) to listOf(0 to 0, 0 to  1, 0 to -2,  1 to  1, -2 to -2),
            (2 to 3) to listOf(0 to 0, 0 to  2, 0 to -1, -1 to  2,  2 to -1),
            (3 to 2) to listOf(0 to 0, 0 to -2, 0 to  1,  1 to -2, -2 to  1),
            (3 to 0) to listOf(0 to 0, 0 to  1, 0 to -2,  2 to  1, -1 to -2),
            (0 to 3) to listOf(0 to 0, 0 to -1, 0 to  2,  1 to -1, -2 to  2),
        )

        // Gravity: frames between drops. Level 1 = 48, each level -5, min 1.
        fun gravityFrames(level: Int) = maxOf(48 - (level - 1) * 5, 1)
    }

    fun cells(piece: Tetromino): List<Pair<Int, Int>> =
        ALL_ROTATIONS[piece.type]!![piece.rotation].map { (dr, dc) ->
            (piece.row + dr) to (piece.col + dc)
        }

    fun canFit(grid: List<List<TetroType?>>, piece: Tetromino): Boolean =
        cells(piece).all { (r, c) ->
            r in 0 until ROWS && c in 0 until COLS && grid[r][c] == null
        }

    fun ghostRow(grid: List<List<TetroType?>>, piece: Tetromino): Int {
        var row = piece.row
        while (canFit(grid, piece.copy(row = row + 1))) row++
        return row
    }

    fun tryMove(grid: List<List<TetroType?>>, piece: Tetromino, dr: Int, dc: Int): Tetromino? {
        val moved = piece.copy(row = piece.row + dr, col = piece.col + dc)
        return if (canFit(grid, moved)) moved else null
    }

    fun tryRotate(grid: List<List<TetroType?>>, piece: Tetromino, dir: Int): Tetromino? {
        val newRot = ((piece.rotation + dir) + 4) % 4
        val rotated = piece.copy(rotation = newRot)
        val kicks = if (piece.type == TetroType.I) KICKS_I else KICKS_JLSTZ
        val kickList = kicks[piece.rotation to newRot] ?: listOf(0 to 0)
        for ((dr, dc) in kickList) {
            val candidate = rotated.copy(row = rotated.row + dr, col = rotated.col + dc)
            if (canFit(grid, candidate)) return candidate
        }
        return null
    }

    fun lockPiece(grid: List<List<TetroType?>>, piece: Tetromino): List<List<TetroType?>> {
        val g = grid.map { it.toMutableList() }
        cells(piece).forEach { (r, c) -> if (r in 0 until ROWS && c in 0 until COLS) g[r][c] = piece.type }
        return g.map { it.toList() }
    }

    /** Returns (newGrid, clearedRowIndices). Cleared rows are removed and empty rows prepended. */
    fun clearLines(grid: List<List<TetroType?>>): Pair<List<List<TetroType?>>, List<Int>> {
        val clearedRows = (0 until ROWS).filter { r -> grid[r].all { it != null } }
        if (clearedRows.isEmpty()) return grid to emptyList()
        val kept = grid.filterIndexed { r, _ -> r !in clearedRows }
        val newRows = List(clearedRows.size) { List(COLS) { null } }
        return (newRows + kept) to clearedRows
    }

    fun scoreForClear(count: Int, level: Int, backToBack: Boolean): Int {
        val base = when (count) {
            1 -> 100; 2 -> 300; 3 -> 500; 4 -> 800; else -> 0
        }
        val multiplier = if (backToBack && count == 4) 1.5f else 1f
        return (base * level * multiplier).toInt()
    }

    fun spawn(type: TetroType): Tetromino =
        Tetromino(type, 0, 0, SPAWN_COL[type]!!)

    fun randomType(): TetroType = TetroType.entries[Random.nextInt(TetroType.entries.size)]

    fun initial(highScore: Int): TetrisState = TetrisState(
        grid = List(ROWS) { List(COLS) { null } },
        active = spawn(randomType()),
        next = randomType(),
        score = 0,
        highScore = highScore,
        level = 1,
        lines = 0,
        phase = TetrisPhase.PLAYING,
    )

    /** One 16 ms frame of game logic. */
    fun update(state: TetrisState, input: TetrisInput): TetrisState {
        if (state.phase != TetrisPhase.PLAYING && state.phase != TetrisPhase.LOCKING) return state
        val active = state.active ?: return state
        var s = state

        // ── Rotate (one-shot) ──────────────────────────────────────────────
        if (input.rotate) {
            val rotated = tryRotate(s.grid, s.active!!, 1)
            if (rotated != null) {
                s = s.copy(active = rotated, lockFrames = 0, phase = TetrisPhase.PLAYING)
            }
        }

        // ── Hard drop (one-shot) ───────────────────────────────────────────
        if (input.hardDrop) {
            val dropRow = ghostRow(s.grid, s.active!!)
            val landed = s.active!!.copy(row = dropRow)
            val newGrid = lockPiece(s.grid, landed)
            val (clearedGrid, clearedRows) = clearLines(newGrid)
            return finishLock(s, clearedGrid, clearedRows)
        }

        // ── Lateral DAS ───────────────────────────────────────────────────
        val newLeft  = if (input.leftDown)  s.leftHeld  + 1 else 0
        val newRight = if (input.rightDown) s.rightHeld + 1 else 0
        s = s.copy(leftHeld = newLeft, rightHeld = newRight)

        fun dasMove(held: Int, dc: Int): TetrisState {
            if (held == 1 || (held > DAS_INITIAL && (held - DAS_INITIAL) % DAS_REPEAT == 0)) {
                val moved = tryMove(s.grid, s.active!!, 0, dc)
                if (moved != null) return s.copy(active = moved, lockFrames = 0)
            }
            return s
        }
        if (newLeft > 0)  s = dasMove(newLeft, -1)
        if (newRight > 0) s = dasMove(newRight, 1)

        // ── Gravity ───────────────────────────────────────────────────────
        val gFrames  = gravityFrames(s.level)
        val dropEvery = if (input.softDrop) 1 else gFrames
        val newGrav  = s.gravityFrames + 1
        s = if (newGrav >= dropEvery) {
            val fallen = tryMove(s.grid, s.active!!, 1, 0)
            if (fallen != null) {
                s.copy(active = fallen, gravityFrames = 0, phase = TetrisPhase.PLAYING, lockFrames = 0)
            } else {
                s.copy(gravityFrames = 0, phase = TetrisPhase.LOCKING)
            }
        } else {
            s.copy(gravityFrames = newGrav)
        }

        // ── Lock delay ────────────────────────────────────────────────────
        if (s.phase == TetrisPhase.LOCKING) {
            val newLock = s.lockFrames + 1
            s = if (newLock >= LOCK_FRAMES) {
                val newGrid = lockPiece(s.grid, s.active!!)
                val (clearedGrid, clearedRows) = clearLines(newGrid)
                finishLock(s, clearedGrid, clearedRows)
            } else {
                s.copy(lockFrames = newLock)
            }
        }

        return s
    }

    private fun finishLock(
        s: TetrisState,
        clearedGrid: List<List<TetroType?>>,
        clearedRows: List<Int>,
    ): TetrisState {
        val isB2B = s.backToBack && clearedRows.size == 4
        val gained = scoreForClear(clearedRows.size, s.level, isB2B)
        val newScore = s.score + gained
        val newLines = s.lines + clearedRows.size
        val newLevel = (newLines / 10) + 1
        val newB2B = clearedRows.size == 4

        val spawnedActive = spawn(s.next)
        val gameOver = !canFit(clearedGrid, spawnedActive)

        return s.copy(
            grid = if (clearedRows.isEmpty()) clearedGrid else s.grid,  // keep locked grid until CLEARING phase applies
            clearingRows = clearedRows,
            active = if (clearedRows.isEmpty()) spawnedActive else null,
            next = if (clearedRows.isEmpty()) randomType() else s.next,
            score = newScore,
            highScore = maxOf(s.highScore, newScore),
            level = newLevel,
            lines = newLines,
            backToBack = newB2B,
            phase = when {
                gameOver && clearedRows.isEmpty() -> TetrisPhase.GAME_OVER
                clearedRows.isNotEmpty() -> TetrisPhase.CLEARING
                else -> TetrisPhase.PLAYING
            },
            lockFrames = 0,
            gravityFrames = 0,
        )
    }

    /** Called after the CLEARING animation to actually remove the rows and spawn next piece. */
    fun applyClear(state: TetrisState): TetrisState {
        val (clearedGrid, _) = clearLines(lockPiece(state.grid, state.active
            ?: return state.copy(phase = TetrisPhase.PLAYING, clearingRows = emptyList())))
        // Actually grid was already locked before CLEARING — just apply the stored cleared-rows removal
        val kept = state.grid.filterIndexed { r, _ -> r !in state.clearingRows }
        val empties = List(state.clearingRows.size) { List(COLS) { null } }
        val newGrid = empties + kept

        val spawnedActive = spawn(state.next)
        val gameOver = !canFit(newGrid, spawnedActive)
        return state.copy(
            grid = newGrid,
            active = if (gameOver) null else spawnedActive,
            next = if (gameOver) state.next else randomType(),
            clearingRows = emptyList(),
            phase = if (gameOver) TetrisPhase.GAME_OVER else TetrisPhase.PLAYING,
            gravityFrames = 0,
            lockFrames = 0,
        )
    }
}
