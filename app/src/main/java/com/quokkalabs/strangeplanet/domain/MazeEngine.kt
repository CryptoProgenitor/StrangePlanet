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
 *   'o' sock (power pellet)
 *   ' ' exterior — treated as wall (only 'T' rows are open at the edge)
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

        // Ticks a regenerated seeker dwells in the pen before re-entering
        // the maze (≈1.4 s at 16ms/tick).
        private const val PEN_DWELL_TICKS = 90

        // Hand-authored, vertically/horizontally near-symmetric mobile maze.
        // Socks ('o') sit near the four corners.
        private val AUTHORED = listOf(
            "###################",
            "#o.......#.......o#",
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
            "#o...............o#",
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

    val tileSize: Float = run {
        val byWidth = screenWidth / cols
        val byHeight = (screenHeight * 0.88f) / rows
        minOf(byWidth, byHeight)
    }
    val originX: Float = (screenWidth - tileSize * cols) / 2f
    val originY: Float = (screenHeight - tileSize * rows) / 2f

    // Reachable pen target that EATEN seekers return to (the being spawn).
    private val penCol: Int
    private val penRow: Int

    init {
        var pc = cols / 2
        var pr = rows / 2
        for (r in 0 until rows) for (c in 0 until cols) {
            if (grid[r][c] == 'P') { pc = c; pr = r }
        }
        penCol = pc
        penRow = pr
    }

    private fun isWall(col: Int, row: Int): Boolean {
        if (row < 0 || row >= rows) return true
        if (col < 0 || col >= cols) {
            // Off the side is only legal on a tunnel row (wrap handles it).
            return row !in tunnelRows
        }
        val ch = grid[row][col]
        return ch == '#' || ch == ' '
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

    // Frightened window shrinks as the tier climbs (≈6.7 s → floor ≈1.9 s).
    private fun frightenedDuration(level: Int): Int =
        (420 - (level - 1) * 40).coerceAtLeast(120)

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
        SeekerMode.EATEN -> Pair(penCol, penRow)
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

    // Human-steered seeker (adversary being). EXACT mirror of the being's
    // control law in updatePlaying(): no input (PacDir.NONE) halts instantly
    // exactly as haltBeing() zeroes being.dir; queued turn adopted at tile
    // centres; mid-tile reversal always legal; a held wall-ward input stops
    // at the wall — never any autopilot. `want` is the persisted client
    // heading (the transport keeps it set, mirroring being.queuedDir).
    private fun movePlayerSeeker(
        s: SeekerEntity,
        want: PacDir,
        speed: Float,
        mode: SeekerMode,
    ): SeekerEntity {
        // Finger lifted / dpad dead-zone: full stop, like haltBeing().
        if (want == PacDir.NONE) {
            return s.copy(dir = PacDir.NONE, progress = 0f, mode = mode)
        }

        var col = s.col
        var row = s.row
        var dir = s.dir
        var queued = want
        var progress = s.progress

        // Standing still: launch straight into the wanted direction.
        if (dir == PacDir.NONE) {
            if (!isWall(wrapCol(col + queued.dc, row), row + queued.dr)) {
                dir = queued
                queued = PacDir.NONE
            } else {
                return s.copy(dir = PacDir.NONE, progress = 0f, mode = mode)
            }
        }

        // Mid-tile reversal is always legal (no wall check needed).
        if (queued != PacDir.NONE && queued == dir.opposite()) {
            dir = queued
            queued = PacDir.NONE
            progress = 1f - progress
        }

        progress += speed
        while (progress >= 1f) {
            col = wrapCol(col + dir.dc, row)
            row = (row + dir.dr).coerceIn(0, rows - 1)
            progress -= 1f

            // At the centre: prefer the wanted turn, else continue, else stop.
            if (queued != PacDir.NONE &&
                !isWall(wrapCol(col + queued.dc, row), row + queued.dr)
            ) {
                dir = queued
                queued = PacDir.NONE
            } else if (isWall(wrapCol(col + dir.dc, row), row + dir.dr)) {
                dir = PacDir.NONE
                progress = 0f
                break
            }
        }
        return s.copy(col = col, row = row, dir = dir, progress = progress, mode = mode)
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
        humanControlled: Boolean = false,
        humanDir: PacDir = PacDir.NONE,
    ): SeekerEntity {
        // Regenerated seeker dwelling in the pen: frozen, body shown, no
        // collision (handled by caller), counting down to re-entry.
        if (s.penTimer > 0) {
            return s.copy(penTimer = s.penTimer - 1, progress = 0f, dir = PacDir.NONE)
        }

        // Resolve effective mode.
        var effectiveMode = when {
            s.mode == SeekerMode.EATEN -> SeekerMode.EATEN
            s.mode == SeekerMode.FRIGHTENED && newFrightenedTick > 0 -> SeekerMode.FRIGHTENED
            else -> globalMode(newWaveTick)
        }

        val speed = when (effectiveMode) {
            SeekerMode.FRIGHTENED -> BASE_SPEED * 0.50f * (1f + (level - 1) * 0.03f)
            SeekerMode.EATEN -> BASE_SPEED * 2.0f
            else -> BASE_SPEED * 0.80f * (1f + (level - 1) * 0.05f)
        }

        // Adversary-steered seeker: player has the being's exact control law
        // whenever it can act (SCATTER/CHASE/FRIGHTENED). EATEN is a recovery
        // state with no being equivalent — eyes auto-return to the pen.
        if (humanControlled && effectiveMode != SeekerMode.EATEN) {
            return movePlayerSeeker(s, humanDir, speed, effectiveMode)
        }

        var col = s.col
        var row = s.row
        var dir = s.dir
        var progress = s.progress

        // Standing still: pick initial direction immediately.
        if (dir == PacDir.NONE) {
            val seed0 = newWaveTick + col * 7 + row * 13
            val probe = s.copy(col = col, row = row, dir = PacDir.NONE, mode = effectiveMode)
            val (tc, tr) = seekerTarget(probe, being, allSeekers)
            dir = if (effectiveMode == SeekerMode.FRIGHTENED)
                seekerFrightenedDir(probe, seed0)
            else
                seekerChooseDir(probe, tc, tr)
            if (dir == PacDir.NONE) return s.copy(mode = effectiveMode)
        }

        progress += speed

        while (progress >= 1f) {
            col = wrapCol(col + dir.dc, row)
            row = (row + dir.dr).coerceIn(0, rows - 1)
            progress -= 1f

            // EATEN eyes that reach the home tile: body instantly restored,
            // then the seeker dwells in the pen before re-entering the maze.
            if (effectiveMode == SeekerMode.EATEN && col == penCol && row == penRow) {
                return s.copy(
                    col = col,
                    row = row,
                    dir = PacDir.NONE,
                    progress = 0f,
                    mode = globalMode(newWaveTick),
                    penTimer = PEN_DWELL_TICKS,
                )
            }

            val midS = s.copy(col = col, row = row, dir = dir, mode = effectiveMode)
            val (tc, tr) = seekerTarget(midS, being, allSeekers)
            val nextDir = if (effectiveMode == SeekerMode.FRIGHTENED)
                seekerFrightenedDir(midS, newWaveTick + col * 7 + row * 13)
            else
                seekerChooseDir(midS, tc, tr)
            if (nextDir != PacDir.NONE) dir = nextDir
            else if (isWall(wrapCol(col + dir.dc, row), row + dir.dr)) {
                dir = PacDir.NONE
                progress = 0f
                break
            }
        }

        return s.copy(col = col, row = row, dir = dir, progress = progress, mode = effectiveMode)
    }

    // ── Public API ────────────────────────────────────────────────────────────

    fun createInitialState(
        level: Int = 1,
        score: Int = 0,
        lives: Int = 3,
        highScore: Int = 0,
        mode: com.quokkalabs.strangeplanet.data.model.PacMode =
            com.quokkalabs.strangeplanet.data.model.PacMode.SOLO,
        controlledSeekerType: SeekerType? = null,
    ): PacGameState {
        var spawnCol = cols / 2
        var spawnRow = rows / 2
        val pellets = mutableSetOf<Int>()
        val socks = mutableSetOf<Int>()
        val walls = mutableSetOf<Int>()
        for (r in 0 until rows) {
            for (c in 0 until cols) {
                when (grid[r][c]) {
                    '.' -> pellets.add(key(c, r))
                    'o' -> socks.add(key(c, r))
                    'P' -> { spawnCol = c; spawnRow = r }
                    '#', ' ' -> walls.add(key(c, r))
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
            socks = socks,
            walls = walls,
            score = score,
            highScore = highScore,
            lives = lives,
            level = level,
            phase = PacPhase.READY,
            screenWidth = screenWidth,
            screenHeight = screenHeight,
            seekers = seekers,
            waveTick = 0,
            frightenedTick = 0,
            frightenedCombo = 0,
            mode = mode,
            controlledSeekerType = controlledSeekerType,
        )
    }

    /**
     * Resets entity positions after a mid-level death without touching the maze.
     * Pellets, socks, score, and level are preserved; only the being, seekers,
     * wave timer, and frightened state are returned to their level-start values.
     */
    fun respawnEntities(state: PacGameState): PacGameState = state.copy(
        being = PacEntity(col = penCol, row = penRow),
        seekers = listOf(
            SeekerEntity(type = SeekerType.MINUTE_REMINDER, col = 16, row = 3),
            SeekerEntity(type = SeekerType.SOCIAL_ANXIETY, col = 2, row = 3),
            SeekerEntity(type = SeekerType.LOGICAL_DEBATER, col = 16, row = 13),
            SeekerEntity(type = SeekerType.OPTIONAL_OBLIGATION, col = 2, row = 13),
        ),
        waveTick = 0,
        frightenedTick = 0,
        frightenedCombo = 0,
        activeSaying = null,
        phase = PacPhase.PLAYING,
    )

    fun startGame(state: PacGameState): PacGameState =
        if (state.phase == PacPhase.READY) state.copy(phase = PacPhase.PLAYING) else state

    fun pauseGame(state: PacGameState): PacGameState =
        if (state.phase == PacPhase.PLAYING) state.copy(phase = PacPhase.PAUSED) else state

    fun resumeGame(state: PacGameState): PacGameState =
        if (state.phase == PacPhase.PAUSED) state.copy(phase = PacPhase.PLAYING) else state

    /**
     * One simulation tick. [requestedDir], if non-null, becomes the being's
     * queued turn (the input queue). Movement, turning, eating and seeker
     * resolution all happen here; rendering reads the resulting state.
     */
    fun update(
        state: PacGameState,
        requestedDir: PacDir?,
        speedFactor: Float = 1f,
        seekerDir: PacDir? = null,
    ): PacGameState {
        val queued = requestedDir ?: PacDir.NONE
        return when (state.phase) {
            PacPhase.PLAYING -> updatePlaying(
                if (queued != PacDir.NONE)
                    state.copy(being = state.being.copy(queuedDir = queued))
                else state,
                speedFactor = speedFactor.coerceIn(0f, 1f),
                seekerDir = seekerDir ?: PacDir.NONE,
            )
            else -> state
        }
    }

    private fun updatePlaying(
        state: PacGameState,
        speedFactor: Float = 1f,
        seekerDir: PacDir = PacDir.NONE,
    ): PacGameState {
        var b = state.being
        var col = b.col
        var row = b.row
        var dir = b.dir
        var queued = b.queuedDir
        var progress = b.progress

        var pellets = state.pellets
        var socks = state.socks
        var score = state.score
        var sockEaten = false

        // Standing still: try to launch straight into the queued direction.
        if (dir == PacDir.NONE &&
            queued != PacDir.NONE &&
            !isWall(col + queued.dc, row + queued.dr)
        ) {
            dir = queued
            queued = PacDir.NONE
        }

        // The being moves only when it has a direction — but the rest of the
        // world (wave timer, seekers, collisions) advances every tick
        // regardless, so seekers never freeze while the player is stopped.
        if (dir != PacDir.NONE) {
            // Mid-tile reversal is always legal (no wall check needed).
            if (queued != PacDir.NONE && queued == dir.opposite()) {
                dir = queued
                queued = PacDir.NONE
                progress = 1f - progress
            }

            val speed = BASE_SPEED * speedFactor * (1f + (state.level - 1) * 0.06f)
            progress += speed

            // Resolve every whole tile crossed this tick (speed < 1, so ≤ 1).
            while (progress >= 1f) {
                col = wrapCol(col + dir.dc, row)
                row += dir.dr
                progress -= 1f

                val k = key(col, row)
                if (pellets.contains(k)) {
                    pellets = pellets - k
                    score += 10
                }
                if (socks.contains(k)) {
                    socks = socks - k
                    score += 50
                    sockEaten = true
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
        } else {
            progress = 0f
        }

        // Wave timer + frightened countdown (a fresh sock resets the window).
        val newWaveTick = state.waveTick + 1
        val newFrightenedTick = if (sockEaten)
            frightenedDuration(state.level)
        else
            (state.frightenedTick - 1).coerceAtLeast(0)
        var combo = if (sockEaten) 0 else state.frightenedCombo

        // On a fresh sock, flip every active seeker to FRIGHTENED + reverse.
        val seedSeekers = if (sockEaten) state.seekers.map { s ->
            if ((s.mode == SeekerMode.SCATTER || s.mode == SeekerMode.CHASE) &&
                s.penTimer == 0
            )
                s.copy(mode = SeekerMode.FRIGHTENED, dir = s.dir.opposite())
            else s
        } else state.seekers

        val controlled = state.controlledSeekerType
        var movedSeekers = seedSeekers.map { s ->
            updateSeeker(
                s = s,
                being = state.being,
                allSeekers = seedSeekers,
                newWaveTick = newWaveTick,
                newFrightenedTick = newFrightenedTick,
                level = state.level,
                humanControlled = controlled != null && s.type == controlled,
                humanDir = seekerDir,
            )
        }

        // Collision resolution against the being's new tile.
        var caught = false
        movedSeekers = movedSeekers.map { s ->
            if (s.col == col && s.row == row && s.penTimer == 0) {
                when (s.mode) {
                    SeekerMode.FRIGHTENED -> {
                        score += 200 * (1 shl combo.coerceAtMost(3))
                        combo = (combo + 1).coerceAtMost(3)
                        s.copy(mode = SeekerMode.EATEN)
                    }
                    SeekerMode.SCATTER, SeekerMode.CHASE -> {
                        caught = true
                        s
                    }
                    SeekerMode.EATEN -> s
                }
            } else s
        }

        val newLives = if (caught) state.lives - 1 else state.lives
        val cleared = pellets.isEmpty() && socks.isEmpty()

        val phase = when {
            caught -> PacPhase.DYING
            cleared -> PacPhase.LEVEL_CLEARED
            else -> PacPhase.PLAYING
        }

        val saying = when {
            phase == PacPhase.LEVEL_CLEARED -> "ALL STARS CONSUMED. VIBRATION EMOTION."
            sockEaten -> "THE PERISHED BEINGS ARE VULNERABLE."
            newFrightenedTick == 0 && state.frightenedTick > 0 -> null
            else -> state.activeSaying
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
            socks = socks,
            score = score,
            lives = newLives,
            phase = phase,
            seekers = movedSeekers,
            waveTick = newWaveTick,
            frightenedTick = newFrightenedTick,
            frightenedCombo = combo,
            activeSaying = saying,
        )
    }
}
