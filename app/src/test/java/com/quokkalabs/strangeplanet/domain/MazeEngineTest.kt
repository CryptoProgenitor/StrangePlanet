package com.quokkalabs.strangeplanet.domain

import com.quokkalabs.strangeplanet.data.model.PacDir
import com.quokkalabs.strangeplanet.data.model.PacEntity
import com.quokkalabs.strangeplanet.data.model.PacGameState
import com.quokkalabs.strangeplanet.data.model.PacPhase
import com.quokkalabs.strangeplanet.data.model.SeekerEntity
import com.quokkalabs.strangeplanet.data.model.SeekerMode
import com.quokkalabs.strangeplanet.data.model.SeekerType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.random.Random

/** Pac-Man style maze (MazeEngine). */
class MazeEngineTest {

    private val engine = MazeEngine(1080f, 2340f)
    private val dirs = listOf(PacDir.UP, PacDir.DOWN, PacDir.LEFT, PacDir.RIGHT)

    private fun playing(): PacGameState = engine.startGame(engine.createInitialState())

    private fun PacGameState.key(col: Int, row: Int) = row * cols + col

    private fun PacGameState.isWallTile(col: Int, row: Int): Boolean {
        if (row !in 0 until rows) return true
        val c = when {
            col < 0 -> col + cols // tunnel wrap
            col >= cols -> col - cols
            else -> col
        }
        return key(c, row) in walls
    }

    /** Asserts the being sits on an open tile and, if mid-move, is heading into an open tile. */
    private fun assertBeingInOpenSpace(s: PacGameState, tick: Int) {
        val b = s.being
        assertTrue("tick $tick: being off-grid at (${b.col},${b.row})", b.col in 0 until s.cols && b.row in 0 until s.rows)
        assertFalse("tick $tick: being inside wall at (${b.col},${b.row})", s.isWallTile(b.col, b.row))
        if (b.dir != PacDir.NONE && b.progress > 0f) {
            assertFalse(
                "tick $tick: being moving from (${b.col},${b.row}) ${b.dir} into a wall",
                s.isWallTile(b.col + b.dir.dc, b.row + b.dir.dr),
            )
        }
    }

    @Test
    fun movingOntoPellet_eatsItAndScores() {
        val s0 = playing()
        val start = s0.being
        val target = s0.key(start.col + 1, start.row)
        assertTrue("maze should have a pellet right of the spawn", target in s0.pellets)

        var s = s0
        var ticks = 0
        while (s.being.col == start.col && ticks < 40) {
            s = engine.update(s, PacDir.RIGHT)
            ticks++
        }
        assertEquals(start.col + 1, s.being.col)
        assertFalse(target in s.pellets)
        assertEquals(s0.pellets.size - 1, s.pellets.size)
        assertEquals(10, s.score)
        assertEquals(PacPhase.PLAYING, s.phase)
    }

    @Test
    fun eatingSock_scores50AndFrightensSeekers() {
        val s0 = playing()
        val sock = s0.key(1, 1)
        assertTrue(sock in s0.socks)
        var s = s0.copy(being = PacEntity(col = 2, row = 1))
        var ticks = 0
        while (sock in s.socks && ticks < 40) {
            s = engine.update(s, PacDir.LEFT)
            ticks++
        }
        assertFalse(sock in s.socks)
        assertEquals(50, s.score)
        assertTrue(s.frightenedTick > 0)
        assertTrue(s.seekers.all { it.mode == SeekerMode.FRIGHTENED })
    }

    @Test
    fun eatingTheLastPellet_clearsTheLevel() {
        val s0 = playing()
        val b = s0.being
        val last = s0.key(b.col + 1, b.row)
        var s = s0.copy(pellets = setOf(last), socks = emptySet(), seekers = emptyList())
        var ticks = 0
        while (s.phase == PacPhase.PLAYING && ticks < 40) {
            s = engine.update(s, PacDir.RIGHT)
            ticks++
        }
        assertEquals(PacPhase.LEVEL_CLEARED, s.phase)
        assertTrue(s.pellets.isEmpty())
        assertEquals(10, s.score)
    }

    @Test
    fun randomSteeringWithoutReversals_neverEntersAWall() {
        // Seekers removed so the being can't die and the walk runs the full length.
        var s = playing().copy(seekers = emptyList())
        val rnd = Random(1234)
        for (tick in 0 until 6000) {
            val req = if (rnd.nextInt(8) == 0) {
                dirs.filter { it != s.being.dir.opposite() }.random(rnd)
            } else null
            s = engine.update(s, req)
            assertEquals(PacPhase.PLAYING, s.phase)
            assertBeingInOpenSpace(s, tick)
        }
    }

    @Test
    fun randomSteeringIncludingReversals_neverEntersAWall() {
        var s = playing().copy(seekers = emptyList())
        val rnd = Random(1234)
        for (tick in 0 until 6000) {
            val req = if (rnd.nextInt(8) == 0) dirs.random(rnd) else null
            s = engine.update(s, req)
            assertBeingInOpenSpace(s, tick)
        }
    }

    @Test
    fun midTileReversal_isContinuousAndStaysOutOfWalls() {
        // Being has just left the corner tile (1,1) heading right; the tile behind
        // it, (0,1), is the outer wall.
        val s0 = playing().copy(
            seekers = emptyList(),
            being = PacEntity(col = 1, row = 1, progress = 0.3f, dir = PacDir.RIGHT),
        )
        fun renderedX(b: PacEntity) = b.col + b.dir.dc * b.progress // as PacScreen draws it

        val s1 = engine.update(s0, PacDir.LEFT)
        assertEquals(PacDir.LEFT, s1.being.dir)
        var s = s1
        repeat(30) { i ->
            s = engine.update(s, null)
            assertBeingInOpenSpace(s, i)
        }
        assertTrue(
            "reversal teleported the being from x=${renderedX(s0.being)} to x=${renderedX(s1.being)}",
            abs(renderedX(s1.being) - renderedX(s0.being)) < 0.2f,
        )
        assertEquals(1, s.being.col)
        assertEquals(1, s.being.row)
    }

    @Test
    fun sockReversesSeekers_withoutTeleportingThemIntoWalls() {
        // A seeker that has just left (1,3) heading right — the tile behind it, (0,3),
        // is the outer wall — when the being eats the sock at (1,1).
        val seeker = SeekerEntity(
            type = SeekerType.MINUTE_REMINDER, col = 1, row = 3,
            progress = 0.4f, dir = PacDir.RIGHT, mode = SeekerMode.SCATTER,
        )
        val s0 = playing().copy(
            seekers = listOf(seeker),
            being = PacEntity(col = 2, row = 1, progress = 0.95f, dir = PacDir.LEFT),
        )
        fun renderedX(k: SeekerEntity) = k.col + k.dir.dc * k.progress // as PacScreen draws it

        val s1 = engine.update(s0, null)
        assertTrue("sock eaten this tick", s1.key(1, 1) !in s1.socks)
        val flipped = s1.seekers.single()
        assertEquals(SeekerMode.FRIGHTENED, flipped.mode)
        assertEquals(PacDir.LEFT, flipped.dir)

        var s = s1
        repeat(40) { i ->
            s = engine.update(s, null)
            val k = s.seekers.single()
            assertFalse("tick $i: seeker inside wall at (${k.col},${k.row})", s.isWallTile(k.col, k.row))
        }
        assertTrue(
            "reversal moved the seeker from x=${renderedX(seeker)} to x=${renderedX(flipped)}",
            abs(renderedX(flipped) - renderedX(seeker)) < 0.2f,
        )
    }
}
