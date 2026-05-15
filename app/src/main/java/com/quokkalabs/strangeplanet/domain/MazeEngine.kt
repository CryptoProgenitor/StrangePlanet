package com.quokkalabs.strangeplanet.domain

import com.quokkalabs.strangeplanet.data.model.PacDir
import com.quokkalabs.strangeplanet.data.model.PacEntity
import com.quokkalabs.strangeplanet.data.model.PacGameState
import com.quokkalabs.strangeplanet.data.model.PacPhase

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
            // Off the side is only legal on a tunnel row (wrap handles it).
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

        val phase = if (pellets.isEmpty())
            PacPhase.LEVEL_CLEARED else PacPhase.PLAYING

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
            phase = phase,
            activeSaying = if (phase == PacPhase.LEVEL_CLEARED)
                "ALL STARS CONSUMED. VIBRATION EMOTION." else state.activeSaying,
        )
    }
}
