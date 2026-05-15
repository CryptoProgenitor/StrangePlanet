package com.quokkalabs.strangeplanet.domain

import com.quokkalabs.strangeplanet.data.model.PacDir
import com.quokkalabs.strangeplanet.data.model.PacEntity
import com.quokkalabs.strangeplanet.data.model.PacGameState
import com.quokkalabs.strangeplanet.data.model.PacPhase
import com.quokkalabs.strangeplanet.data.model.SeekerEntity
import com.quokkalabs.strangeplanet.data.model.SeekerMode
import com.quokkalabs.strangeplanet.data.model.SeekerType
import kotlin.math.abs

/**
 * Pure, Android-free Pac-Man-style maze logic.
 *
 * Grid legend (authored maze):
 *   '#' wall
 *   '.' star (pellet)
 *   ' ' empty path (no star)
 *   'P' being spawn (treated as empty path)
 *   'T' tunnel mouth (empty path; row is flagged as a wrap row)
 *
 * The authored rows are normalised to a uniform rectangle so a miscounted
 * row can never produce a ragged grid: short rows are right-padded with wall,
 * long rows truncated. Any unknown character becomes wall.
 */
class MazeEngine(
    private val screenWidth: Float,
    private val screenHeight: Float,
) {
    companion object {
        // Logical fraction of one tile travelled per 16ms tick at level 1.
        private const val BASE_SPEED = 0.085f

        // Hand-authored, vertically/horizontally near-symmetric mobile maze.
        private val AUTHORED = listOf(
            "###################",
            "#........#........#",
            "#.##.###.#.###.##.#",
            "#.................#",
            "#.##.#.#####.#.##.#",
            "#....#...#...#....#",
            "####.###.#.###.####",
            "   #.#...#...#.#   ",
            "####.#.#####.#.####",
            "T......#####......T",
            "####.#.#####.#.####",
            "   #.#.......#.#   ",
            "####.#.#####.#.####",
            "#........#........#",
            "#.##.###.#.###.##.#",
            "#..#.....P.....#..#",
            "##.#.#.#####.#.#.##",
            "#....#...#...#....#",
            "#.######.#.######.#",
            "#.................#",
            "###################",
        )
    }

    private val rows = AUTHORED.size
    private val cols = AUTHORED.maxOf { it.length }

    // Normalised grid: every row exactly [cols] wide.
    private val grid: List<CharArray> = AUTHORED.map { raw ->
        CharArray(cols) { c -> if (c < raw.length) raw[c] else '#' }
    }

    // Only rows explicitly marked with a 'T' mouth wrap horizontally.
    private val tunnelRows: Set<Int> = buildSet {
        for (r in 0 until rows) {
            if (grid[r][0] == 'T' || grid[r][cols - 1] == 'T') add(r)
        }
    }

    private val tileSize: Float = run {
        val byWidth = screenWidth / cols
        val byHeight = (screenHeight * 0.88f) / rows
        minOf(byWidth, byHeight)
    }
    private val originX: Float = (screenWidth - tileSize * cols) / 2f
    private val originY: Float = (screenHeight - tileSize * rows) / 2f

    private fun isWall(col: Int, row: Int): Boolean {
        if (row < 0 || row >= rows) return true
        if (col < 0 || col >= cols) {
            return row !in tunnelRows
        }
        return grid[row][col] == '#'
    }

    private fun wrapCol(col: Int, row: Int): Int = when {
        col < 0 && row in tunnelRows -> cols - 1
        col >= cols && row in tunnelRows -> 0
        else -> col
    }

    private fun key(col: Int, row: Int) = row * cols + col

    // ── Seeker wave timer ────────────────────────────────────────────────────

    private fun globalMode(waveTick: Int): SeekerMode = when {
        waveTick < 420 -> SeekerMode.SCATTER    // ~7 s
        waveTick < 1620 -> SeekerMode.CHASE     // ~20 s
        waveTick < 2040 -> SeekerMode.SCATTER   // ~7 s
        waveTick < 3240 -> SeekerMode.CHASE     // ~20 s
        waveTick < 3555 -> SeekerMode.SCATTER   // ~5 s
        else -> SeekerMode.CHASE                // forever
    }

    // ── Seeker targeting ─────────────────────────────────────────────────────

    private fun scatterTarget(type: SeekerType): Pair<Int, Int> = when (type) {
        SeekerType.MINUTE_REMINDER -> Pair(cols - 2, 1)
        SeekerType.SOCIAL_ANXIETY -> Pair(1, 1)
        SeekerType.LOGICAL_DEBATER -> Pair(cols - 2, rows - 2)
        SeekerType.OPTIONAL_OBLIGATION -> Pair(1, rows - 2)
    }

    private fun chaseTarget(
        s: SeekerEntity,
        being: PacEntity,
        allSeekers: List<SeekerEntity>,
    ): Pair<Int, Int> = when (s.type) {
        SeekerType.MINUTE_REMINDER ->
            Pair(being.col, being.row)

        SeekerType.SOCIAL_ANXIETY -> {
            val targetCol = (being.col + being.dir.dc * 4).coerceIn(0, cols - 1)
            val targetRow = (being.row + being.dir.dr * 4).coerceIn(0, rows - 1)
            Pair(targetCol, targetRow)
        }

        SeekerType.LOGICAL_DEBATER -> {
            val pivotCol = being.col + being.dir.dc * 2
            val pivotRow = being.row + being.dir.dr * 2
            val blinky = allSeekers.find { it.type == SeekerType.MINUTE_REMINDER }
            if (blinky != null) {
                val tc = (pivotCol * 2 - blinky.col).coerceIn(0, cols - 1)
                val tr = (pivotRow * 2 - blinky.row).coerceIn(0, rows - 1)
                Pair(tc, tr)
            } else {
                Pair(being.col, being.row)
            }
        }

        SeekerType.OPTIONAL_OBLIGATION -> {
            val dist = abs(being.col - s.col) + abs(being.row - s.row)
            if (dist > 8) Pair(being.col, being.row) else Pair(1, rows - 2)
        }
    }

    private fun seekerTarget(
        s: SeekerEntity,
        being: PacEntity,
        allSeekers: List<SeekerEntity>,
    ): Pair<Int, Int> = when (s.mode) {
        SeekerMode.SCATTER -> scatterTarget(s.type)
        SeekerMode.CHASE -> chaseTarget(s, being, allSeekers)
        SeekerMode.FRIGHTENED -> Pair(s.col, s.row) // direction chosen randomly
        SeekerMode.EATEN -> Pair(cols / 2, rows / 2)
    }

    // Greedy direction: choose legal non-reversing move closest to target.
    // Classic priority: UP > LEFT > DOWN > RIGHT (tiebreak).
    private fun seekerChooseDir(s: SeekerEntity, targetCol: Int, targetRow: Int): PacDir {
        val opposite = s.dir.opposite()
        return listOf(PacDir.UP, PacDir.LEFT, PacDir.DOWN, PacDir.RIGHT)
            .filter { d ->
                d != opposite &&
                    !isWall(wrapCol(s.col + d.dc, s.row), s.row + d.dr)
            }
            .minByOrNull { d ->
                val nc = wrapCol(s.col + d.dc, s.row)
                val nr = s.row + d.dr
                abs(nc - targetCol) + abs(nr - targetRow)
            } ?: PacDir.NONE
    }

    // Pseudo-random direction for frightened mode (deterministic, no Random state).
    private fun seekerFrightenedDir(s: SeekerEntity, seed: Int): PacDir {
        val opposite = s.dir.opposite()
        val candidates = listOf(PacDir.UP, PacDir.LEFT, PacDir.DOWN, PacDir.RIGHT)
            .filter { d -> d != opposite && !isWall(wrapCol(s.col + d.dc, s.row), s.row + d.dr) }
        if (candidates.isEmpty()) return PacDir.NONE
        val hash = (seed * 1664525 + 1013904223) and 0x7FFFFFFF
        return candidates[hash % candidates.size]
    }

    private fun updateSeeker(
        s: SeekerEntity,
        being: PacEntity,
        allSeekers: List<SeekerEntity>,
        newWaveTick: Int,
        newFrightenedTick: Int,
        level: Int,
    ): SeekerEntity {
        // Resolve effective mode
        val effectiveMode = when {
            s.mode == SeekerMode.EATEN -> SeekerMode.EATEN
            s.mode == SeekerMode.FRIGHTENED && newFrightenedTick > 0 -> SeekerMode.FRIGHTENED
            else -> globalMode(newWaveTick)
        }

        val speed = when (effectiveMode) {
            SeekerMode.FRIGHTENED -> BASE_SPEED * 0.50f * (1f + (level - 1) * 0.03f)
            SeekerMode.EATEN -> BASE_SPEED * 2.0f
            else -> BASE_SPEED * 0.80f * (1f + (level - 1) * 0.05f)
        }

        var col = s.col
        var row = s.row
        var dir = s.dir
        var progress = s.progress

        // Standing still: pick initial direction immediately.
        if (dir == PacDir.NONE) {
            val (tc, tr) = seekerTarget(s.copy(mode = effectiveMode), being, allSeekers)
            dir = if (effectiveMode == SeekerMode.FRIGHTENED)
                seekerFrightenedDir(s, newWaveTick + col * 7 + row * 13)
            else
                seekerChooseDir(s.copy(col = col, row = row, dir = PacDir.NONE), tc, tr)
            if (dir == PacDir.NONE) return s.copy(mode = effectiveMode)
        }

        progress += speed

        while (progress >= 1f) {
            col = wrapCol(col + dir.dc, row)
            row += dir.dr
            progress -= 1f

            // At tile centre: choose next direction.
            val midS = s.copy(col = col, row = row, dir = dir, mode = effectiveMode)
            val (tc, tr) = seekerTarget(midS, being, allSeekers)
            val nextDir = if (effectiveMode == SeekerMode.FRIGHTENED)
                seekerFrightenedDir(midS, newWaveTick + col * 7 + row * 13)
            else
                seekerChooseDir(midS, tc, tr)
            if (nextDir != PacDir.NONE) dir = nextDir
        }

        return s.copy(col = col, row = row, dir = dir, progress = progress, mode = effectiveMode)
    }

    // ── Public API ────────────────────────────────────────────────────────────

    fun createInitialState(
        level: Int = 1,
        score: Int = 0,
        lives: Int = 3,
    ): PacGameState {
        var spawnCol = cols / 2
        var spawnRow = rows / 2
        val pellets = mutableSetOf<Int>()
        val walls = mutableSetOf<Int>()
        for (r in 0 until rows) {
            for (c in 0 until cols) {
                when (grid[r][c]) {
                    '.' -> pellets.add(key(c, r))
                    'P' -> { spawnCol = c; spawnRow = r }
                    '#' -> walls.add(key(c, r))
                }
            }
        }

        val seekers = listOf(
            SeekerEntity(type = SeekerType.MINUTE_REMINDER, col = 16, row = 3),
            SeekerEntity(type = SeekerType.SOCIAL_ANXIETY, col = 2, row = 3),
            SeekerEntity(type = SeekerType.LOGICAL_DEBATER, col = 16, row = 13),
            SeekerEntity(type = SeekerType.OPTIONAL_OBLIGATION, col = 2, row = 13),
        )

        return PacGameState(
            being = PacEntity(col = spawnCol, row = spawnRow),
            cols = cols,
            rows = rows,
            tileSize = tileSize,
            originX = originX,
            originY = originY,
            pellets = pellets,
            totalPellets = pellets.size,
            walls = walls,
            score = score,
            lives = lives,
            level = level,
            phase = PacPhase.READY,
            screenWidth = screenWidth,
            screenHeight = screenHeight,
            seekers = seekers,
            waveTick = 0,
            frightenedTick = 0,
        )
    }

    fun startGame(state: PacGameState): PacGameState =
        if (state.phase == PacPhase.READY) state.copy(phase = PacPhase.PLAYING) else state

    fun pauseGame(state: PacGameState): PacGameState =
        if (state.phase == PacPhase.PLAYING) state.copy(phase = PacPhase.PAUSED) else state

    fun resumeGame(state: PacGameState): PacGameState =
        if (state.phase == PacPhase.PAUSED) state.copy(phase = PacPhase.PLAYING) else state

    /**
     * One simulation tick. [requestedDir], if non-null, becomes the being's
     * queued turn (the input queue). Movement, turning and pellet eating all
     * happen here; rendering reads the resulting state.
     */
    fun update(state: PacGameState, requestedDir: PacDir?): PacGameState {
        val queued = requestedDir ?: PacDir.NONE
        return when (state.phase) {
            PacPhase.PLAYING -> updatePlaying(
                if (queued != PacDir.NONE)
                    state.copy(being = state.being.copy(queuedDir = queued))
                else state,
            )
            else -> state
        }
    }

    private fun updatePlaying(state: PacGameState): PacGameState {
        var b = state.being
        var col = b.col
        var row = b.row
        var dir = b.dir
        var queued = b.queuedDir
        var progress = b.progress

        // Standing still: try to launch straight into the queued direction.
        if (dir == PacDir.NONE) {
            if (queued != PacDir.NONE && !isWall(col + queued.dc, row + queued.dr)) {
                dir = queued
                queued = PacDir.NONE
            } else {
                return state.copy(
                    being = b.copy(progress = 0f, queuedDir = queued),
                )
            }
        }

        // Mid-tile reversal is always legal (no wall check needed).
        if (queued != PacDir.NONE && queued == dir.opposite()) {
            dir = queued
            queued = PacDir.NONE
            progress = 1f - progress
        }

        val speed = BASE_SPEED * (1f + (state.level - 1) * 0.06f)
        progress += speed

        var pellets = state.pellets
        var score = state.score

        // Resolve every whole tile crossed this tick (speed < 1, so ≤ 1).
        while (progress >= 1f) {
            col = wrapCol(col + dir.dc, row)
            row += dir.dr
            progress -= 1f

            // Eat a star on arrival at the tile centre.
            val k = key(col, row)
            if (pellets.contains(k)) {
                pellets = pellets - k
                score += 10
            }

            // At the centre: prefer the queued turn, else continue, else stop.
            if (queued != PacDir.NONE && !isWall(col + queued.dc, row + queued.dr)) {
                dir = queued
                queued = PacDir.NONE
            } else if (isWall(col + dir.dc, row + dir.dr)) {
                dir = PacDir.NONE
                progress = 0f
                break
            }
        }

        // Update wave timer and frightened countdown.
        val newWaveTick = state.waveTick + 1
        val newFrightenedTick = (state.frightenedTick - 1).coerceAtLeast(0)

        // Update all seekers (use original being position as target reference).
        val updatedSeekers = state.seekers.map { s ->
            updateSeeker(
                s = s,
                being = state.being,
                allSeekers = state.seekers,
                newWaveTick = newWaveTick,
                newFrightenedTick = newFrightenedTick,
                level = state.level,
            )
        }

        // Collision: being's new tile vs each seeker's tile (SCATTER or CHASE only).
        val caught = updatedSeekers.any { s ->
            s.col == col && s.row == row &&
                (s.mode == SeekerMode.SCATTER || s.mode == SeekerMode.CHASE)
        }

        val newLives = if (caught) state.lives - 1 else state.lives

        val phase = when {
            caught -> PacPhase.DYING
            pellets.isEmpty() -> PacPhase.LEVEL_CLEARED
            else -> PacPhase.PLAYING
        }

        return state.copy(
            being = b.copy(
                col = col,
                row = row,
                dir = dir,
                queuedDir = queued,
                progress = progress,
            ),
            pellets = pellets,
            score = score,
            lives = newLives,
            phase = phase,
            seekers = updatedSeekers,
            waveTick = newWaveTick,
            frightenedTick = newFrightenedTick,
            activeSaying = if (phase == PacPhase.LEVEL_CLEARED)
                "ALL STARS CONSUMED. VIBRATION EMOTION." else state.activeSaying,
        )
    }
}
