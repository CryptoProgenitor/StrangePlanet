package com.quokkalabs.strangeplanet.domain

import com.quokkalabs.strangeplanet.data.model.TetrisInput
import com.quokkalabs.strangeplanet.data.model.TetrisPhase
import com.quokkalabs.strangeplanet.data.model.TetrisState
import com.quokkalabs.strangeplanet.data.model.TetroType
import com.quokkalabs.strangeplanet.data.model.Tetromino
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class TetrisEngineTest {

    private val engine = TetrisEngine()
    private val rows = TetrisEngine.ROWS
    private val cols = TetrisEngine.COLS
    private val noInput = TetrisInput()

    private fun emptyGrid(): List<List<TetroType?>> = List(rows) { List(cols) { null } }

    /** Empty grid except the given rows, which are filled apart from the listed gap columns. */
    private fun gridWithRows(
        vararg filled: Pair<Int, Set<Int>>,
        fill: TetroType = TetroType.Z,
    ): List<List<TetroType?>> {
        val g = emptyGrid().map { it.toMutableList() }
        for ((r, gaps) in filled) for (c in 0 until cols) if (c !in gaps) g[r][c] = fill
        return g
    }

    /** One frame exactly as TetrisViewModel drives it: update while PLAYING/LOCKING, applyClear once CLEARING. */
    private fun frame(s: TetrisState, input: TetrisInput = noInput): TetrisState = when (s.phase) {
        TetrisPhase.PLAYING, TetrisPhase.LOCKING -> engine.update(s, input)
        TetrisPhase.CLEARING -> engine.applyClear(s)
        else -> s
    }

    private fun filledCount(grid: List<List<TetroType?>>) = grid.sumOf { row -> row.count { it != null } }

    // ── Regression: the game must continue after a line clear ────────────────

    @Test
    fun hardDropLineClear_thenApplyClear_gameContinues() {
        val start = engine.initial(highScore = 0).copy(
            grid = gridWithRows(19 to setOf(3, 4, 5, 6)),
            active = engine.spawn(TetroType.I), // horizontal I on row 1, cols 3..6
            next = TetroType.T,
        )

        val clearing = engine.update(start, TetrisInput(hardDrop = true))
        assertEquals(TetrisPhase.CLEARING, clearing.phase)
        assertEquals(listOf(19), clearing.clearingRows)
        assertNull(clearing.active)
        assertEquals(100, clearing.score)
        assertEquals(1, clearing.lines)

        val resumed = engine.applyClear(clearing)
        assertEquals(TetrisPhase.PLAYING, resumed.phase)
        val active = resumed.active
        assertNotNull("a new piece must be spawned after the clear", active)
        assertEquals(TetroType.T, active!!.type)
        assertTrue(resumed.clearingRows.isEmpty())
        assertEquals("the only full row was removed", 0, filledCount(resumed.grid))
        assertEquals(100, resumed.score)
        assertTrue(engine.canFit(resumed.grid, active))

        // And the game keeps ticking: the new piece falls under gravity.
        var s = resumed
        repeat(TetrisEngine.gravityFrames(s.level) * 3) { s = frame(s) }
        assertEquals(TetrisPhase.PLAYING, s.phase)
        assertEquals(3, s.active!!.row)
    }

    @Test
    fun lockDelayLineClear_drivenLikeViewModel_returnsToPlaying() {
        var s = engine.initial(highScore = 0).copy(
            grid = gridWithRows(19 to setOf(3, 4, 5, 6)),
            active = Tetromino(TetroType.I, rotation = 0, row = 18, col = 3), // already resting in the gap
            next = TetroType.O,
        )
        var sawLocking = false
        var sawClearing = false
        for (i in 0 until 300) {
            s = frame(s)
            if (s.phase == TetrisPhase.LOCKING) sawLocking = true
            if (s.phase == TetrisPhase.CLEARING) sawClearing = true
            if (sawClearing && s.phase != TetrisPhase.CLEARING) break
        }
        assertTrue("piece should enter lock delay", sawLocking)
        assertTrue("full row should trigger CLEARING", sawClearing)
        assertEquals(TetrisPhase.PLAYING, s.phase)
        assertEquals(TetroType.O, s.active?.type)
        assertEquals(0, filledCount(s.grid))
        assertEquals(100, s.score)

        // Not frozen: a soft drop moves the new piece every frame.
        val row0 = s.active!!.row
        repeat(5) { s = frame(s, TetrisInput(softDrop = true)) }
        assertEquals(row0 + 5, s.active!!.row)
    }

    @Test
    fun lineClear_keepsLockedPieceCellsAboveClearedRows() {
        // Rows 18 and 19 are full except column 0; a vertical I in column 0 fills
        // rows 16..19, clearing two lines and leaving two I cells that must drop
        // to rows 18..19.
        val start = engine.initial(highScore = 0).copy(
            grid = gridWithRows(18 to setOf(0), 19 to setOf(0)),
            active = Tetromino(TetroType.I, rotation = 1, row = 0, col = -2), // vertical, column 0
            next = TetroType.T,
        )
        val clearing = engine.update(start, TetrisInput(hardDrop = true))
        assertEquals(TetrisPhase.CLEARING, clearing.phase)
        assertEquals(listOf(18, 19), clearing.clearingRows)
        assertEquals(300, clearing.score)

        val resumed = engine.applyClear(clearing)
        assertEquals(TetrisPhase.PLAYING, resumed.phase)
        assertEquals(TetroType.I, resumed.grid[18][0])
        assertEquals(TetroType.I, resumed.grid[19][0])
        assertEquals(2, filledCount(resumed.grid))
    }

    // ── Gravity / drops ──────────────────────────────────────────────────────

    @Test
    fun pieceFallsOneRowPerGravityPeriod() {
        var s = engine.initial(highScore = 0).copy(active = engine.spawn(TetroType.T), next = TetroType.O)
        val g = TetrisEngine.gravityFrames(1)
        repeat(g - 1) { s = engine.update(s, noInput) }
        assertEquals(0, s.active!!.row)
        s = engine.update(s, noInput)
        assertEquals(1, s.active!!.row)
        repeat(g * 5) { s = engine.update(s, noInput) }
        assertEquals(6, s.active!!.row)
        assertEquals(TetrisPhase.PLAYING, s.phase)
    }

    @Test
    fun hardDrop_locksPieceAtBottomAndSpawnsNext() {
        val start = engine.initial(highScore = 0).copy(active = engine.spawn(TetroType.O), next = TetroType.L)
        val s = engine.update(start, TetrisInput(hardDrop = true))

        assertEquals(TetrisPhase.PLAYING, s.phase)
        for ((r, c) in listOf(18 to 4, 18 to 5, 19 to 4, 19 to 5)) {
            assertEquals("O cell at ($r,$c)", TetroType.O, s.grid[r][c])
        }
        assertEquals(4, filledCount(s.grid))
        assertEquals(TetroType.L, s.active!!.type)
        assertEquals(0, s.active!!.row)
        assertEquals(0, s.score)
    }

    // ── Rotation ─────────────────────────────────────────────────────────────

    @Test
    fun rotateVerticalIAgainstRightWall_kicksInsideBoard() {
        val piece = Tetromino(TetroType.I, rotation = 1, row = 5, col = 7) // vertical in column 9
        assertTrue(engine.cells(piece).all { (_, c) -> c == 9 })
        val start = engine.initial(highScore = 0).copy(active = piece, next = TetroType.O)

        val s = engine.update(start, TetrisInput(rotate = true))
        val rotated = s.active!!
        assertEquals(2, rotated.rotation)
        assertTrue(engine.canFit(s.grid, rotated))
        assertTrue(engine.cells(rotated).all { (r, c) -> r in 0 until rows && c in 0 until cols })
    }

    @Test
    fun rotationNearWallsAndBlocks_neverOverlaps() {
        val rnd = Random(42)
        repeat(8) {
            // Rugged stack in the lower half plus a few floating blocks hugging the walls.
            val g = emptyGrid().map { it.toMutableList() }
            for (r in 8 until rows) for (c in 0 until cols) if (rnd.nextFloat() < 0.45f) g[r][c] = TetroType.S
            for (r in 0 until 8) {
                if (rnd.nextFloat() < 0.3f) g[r][1] = TetroType.J
                if (rnd.nextFloat() < 0.3f) g[r][cols - 2] = TetroType.L
            }
            val grid = g.map { it.toList() }

            for (type in TetroType.entries) for (rot in 0..3) for (col in -3..cols) for (row in -1..rows) {
                val piece = Tetromino(type, rot, row, col)
                if (!engine.canFit(grid, piece)) continue
                for (dir in listOf(1, -1)) {
                    val r = engine.tryRotate(grid, piece, dir) ?: continue
                    assertEquals((rot + dir + 4) % 4, r.rotation)
                    for ((cr, cc) in engine.cells(r)) {
                        assertTrue("$r out of bounds", cr in 0 until rows && cc in 0 until cols)
                        assertNull("$r overlaps a block at ($cr,$cc)", grid[cr][cc])
                    }
                }
                // Through the public update path too.
                val s = engine.update(
                    engine.initial(0).copy(grid = grid, active = piece, next = TetroType.O),
                    TetrisInput(rotate = true),
                )
                if (s.phase == TetrisPhase.PLAYING || s.phase == TetrisPhase.LOCKING) {
                    assertTrue(engine.canFit(s.grid, s.active!!))
                }
            }
        }
    }

    // ── Game over ────────────────────────────────────────────────────────────

    @Test
    fun stackingToTheTop_givesGameOver() {
        var s = engine.initial(highScore = 0)
        var drops = 0
        while (s.phase != TetrisPhase.GAME_OVER && drops < 100) {
            s = engine.update(s, TetrisInput(hardDrop = true))
            // Pieces only ever land in columns 3..6, so no line can complete.
            assertNotEquals(TetrisPhase.CLEARING, s.phase)
            drops++
        }
        assertEquals(TetrisPhase.GAME_OVER, s.phase)
        assertTrue("took $drops drops", drops in 5..25)

        // Once over, further input does nothing.
        val after = engine.update(s, TetrisInput(hardDrop = true, moveLeft = true, rotate = true))
        assertEquals(s, after)
    }

    // ── clearLines ───────────────────────────────────────────────────────────

    @Test
    fun clearLines_removesExactlyTheFullRows() {
        val g = emptyGrid().map { it.toMutableList() }
        for (c in 0 until cols) {
            g[5][c] = TetroType.I
            g[17][c] = TetroType.J
            g[19][c] = TetroType.L
        }
        for (c in 0 until cols - 1) g[18][c] = TetroType.T // one gap: must survive
        g[16][0] = TetroType.S
        g[4][2] = TetroType.O
        val grid = g.map { it.toList() }

        val (newGrid, cleared) = engine.clearLines(grid)

        assertEquals(listOf(5, 17, 19), cleared)
        assertEquals(rows, newGrid.size)
        assertTrue(newGrid.all { it.size == cols })
        for (r in 0..2) assertTrue("row $r should be a fresh empty row", newGrid[r].all { it == null })
        assertEquals(grid[18], newGrid[19])
        assertEquals(grid[16], newGrid[18])
        assertEquals(grid[4], newGrid[7]) // rows above row 5 drop by 3
        assertTrue(newGrid.none { row -> row.all { it != null } })
        assertEquals(filledCount(grid) - 3 * cols, filledCount(newGrid))

        // A grid with no full rows is returned untouched.
        val (same, none) = engine.clearLines(newGrid)
        assertTrue(none.isEmpty())
        assertEquals(newGrid, same)
    }
}
