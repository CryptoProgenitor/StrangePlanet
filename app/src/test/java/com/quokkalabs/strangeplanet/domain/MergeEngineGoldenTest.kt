package com.quokkalabs.strangeplanet.domain

import com.quokkalabs.strangeplanet.data.model.MergePhase
import com.quokkalabs.strangeplanet.data.model.MergeState
import com.quokkalabs.strangeplanet.data.model.MergeTier
import com.quokkalabs.strangeplanet.data.model.MergeTier.BOULDER
import com.quokkalabs.strangeplanet.data.model.MergeTier.DUST_MOTE
import com.quokkalabs.strangeplanet.data.model.MergeTier.MOON
import com.quokkalabs.strangeplanet.data.model.MergeTier.MOONLET
import com.quokkalabs.strangeplanet.data.model.MergeTier.PEBBLE
import com.quokkalabs.strangeplanet.data.model.MergeTier.STRANGE_PLANET
import com.quokkalabs.strangeplanet.data.model.Orb
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * GOLDEN / characterization test for MergeEngine.update physics (integration
 * substeps, resolveWalls, resolveCollisions, merge pass, merge cooldown).
 *
 * The expected numbers were recorded from commit 0c26b57 and re-recorded on
 * purpose when orbs were clamped back inside the vessel after collisions
 * (they used to poke through walls for a frame). Any
 * refactor of the physics that is meant to be behaviour-preserving must keep
 * this test green. If the physics is changed ON PURPOSE, re-record the
 * constants (print runScenario()'s orbs/score) and say so in the commit.
 *
 * Scenario: 1080 x 2340 screen, game started via startGame(), then 21 hand-placed
 * orbs (DUST_MOTE..MOON) at fixed positions with some initial velocities/spins,
 * including touching same-tier pairs on the floor and a DUST pair flying at each
 * other. 400 ticks with drop = false (so the engine's Random is never consulted).
 * Cascading merges leave 11 orbs, up to a STRANGE_PLANET, score 185.
 * Orb ids come from a global counter, so they're not asserted.
 */
class MergeEngineGoldenTest {

    private data class G(
        val tier: MergeTier,
        val x: Float, val y: Float,
        val vx: Float, val vy: Float,
        val angle: Float, val omega: Float,
    )

    companion object {
        private const val TICKS = 400
        private const val TOL = 1e-3f

        private fun scenario(): List<Orb> {
            var id = -1000L // negative: never collides with engine-issued ids
            fun o(t: MergeTier, x: Float, y: Float, vx: Float = 0f, vy: Float = 0f, w: Float = 0f) =
                Orb(id = id--, tier = t, x = x, y = y, vx = vx, vy = vy, omega = w)
            return listOf(
                // Floor row (vesselBottom = 2176.2): touching BOULDER and PEBBLE pairs.
                o(BOULDER, 150f, 2119f),
                o(BOULDER, 265f, 2119f),
                o(MOONLET, 420f, 2101f, w = 1.5f),
                o(PEBBLE, 560f, 2134f),
                o(PEBBLE, 650f, 2134f),
                o(DUST_MOTE, 760f, 2145f, vx = 40f),
                o(MOONLET, 900f, 2101f),
                // Second row: a DUST pair flying at each other.
                o(DUST_MOTE, 200f, 1900f, vx = 120f),
                o(DUST_MOTE, 300f, 1900f, vx = -80f),
                o(PEBBLE, 450f, 1880f, w = 5f),
                o(BOULDER, 600f, 1870f, vx = 200f, vy = -300f),
                o(MOON, 800f, 1850f),
                // Third row.
                o(PEBBLE, 180f, 1500f, w = -8f),
                o(MOONLET, 380f, 1500f),
                o(DUST_MOTE, 520f, 1500f, vx = 300f),
                o(BOULDER, 700f, 1500f, w = 3f),
                o(PEBBLE, 900f, 1500f, vx = -250f),
                // Top row, well below the capacity line (vesselTop = 608.4).
                o(DUST_MOTE, 250f, 1100f),
                o(MOONLET, 500f, 1100f, vx = 100f, w = 2f),
                o(PEBBLE, 750f, 1100f, vy = 200f),
                o(DUST_MOTE, 850f, 1100f, vx = -150f),
            )
        }

        private fun runScenario(): MergeState {
            val e = MergeEngine(1080f, 2340f)
            var s = e.startGame(e.createInitialState(highScore = 0)).copy(orbs = scenario())
            val spout = (s.vesselLeft + s.vesselRight) / 2f
            repeat(TICKS) { s = e.update(s, spout, drop = false) }
            return s
        }

        private const val EXPECTED_SCORE = 185

        // Final orbs, in list order, after 400 ticks.
        private val EXPECTED = listOf(
            G(MOONLET, 919.2096f, 2101.8096f, 18.722227f, 14.1646385f, 0.16619356f, 0.10243611f),
            G(PEBBLE, 424.11456f, 1987.4021f, 8.565763f, 25.309237f, 4.816498f, -0.35199472f),
            G(MOON, 645.6665f, 2081.851f, 15.238136f, -6.502435f, -0.3769825f, -0.10955724f),
            G(DUST_MOTE, 537.6858f, 2145.3552f, 20.094393f, 8.613855f, 1.9780204f, -0.4767406f),
            G(BOULDER, 520.94354f, 1998.1626f, 5.7111597f, 4.4241757f, -4.740001f, -0.10079982f),
            G(PEBBLE, 951.8688f, 1990.4692f, 18.62275f, 1.789938f, 7.317105f, 0.47455359f),
            G(DUST_MOTE, 229.36508f, 2145.324f, 3.215316f, -3.7418675f, 5.903451f, 0.10351115f),
            G(MOONLET, 441.6685f, 2101.8096f, -1.1173196f, 35.750195f, -0.6638303f, 0.09401982f),
            G(BOULDER, 142.64642f, 2119.9536f, -12.946549f, 23.893131f, 1.3713385f, 1.009284f),
            G(BOULDER, 791.30414f, 2119.9536f, 17.16014f, -3.8467221f, 0.9502179f, 0.22073925f),
            G(STRANGE_PLANET, 267.05518f, 2002.3655f, 7.631667f, 44.41968f, -0.0101422155f, -0.0365602f),
        )
    }

    @Test
    fun physicsMatchesRecordedGolden() {
        val s = runScenario()

        assertEquals(MergePhase.PLAYING, s.phase)
        assertEquals("score", EXPECTED_SCORE, s.score)
        assertEquals("orb count", EXPECTED.size, s.orbs.size)
        s.orbs.zip(EXPECTED).forEachIndexed { i, (o, g) ->
            assertEquals("orb[$i].tier", g.tier, o.tier)
            assertEquals("orb[$i].x", g.x, o.x, TOL)
            assertEquals("orb[$i].y", g.y, o.y, TOL)
            assertEquals("orb[$i].vx", g.vx, o.vx, TOL)
            assertEquals("orb[$i].vy", g.vy, o.vy, TOL)
            assertEquals("orb[$i].angle", g.angle, o.angle, TOL)
            assertEquals("orb[$i].omega", g.omega, o.omega, TOL)
        }
    }

    @Test
    fun scenarioIsDeterministicAcrossFreshEngines() {
        val a = runScenario()
        val b = runScenario()
        assertEquals(a.score, b.score)
        assertEquals(a.orbs.map { it.copy(id = 0) }, b.orbs.map { it.copy(id = 0) })
    }
}
