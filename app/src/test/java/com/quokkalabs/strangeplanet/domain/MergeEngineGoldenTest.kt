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
 * The expected numbers were recorded from the engine as of commit 0c26b57. Any
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
            G(MOONLET, 919.9519f, 2102.2168f, 27.190765f, 13.839883f, 0.004601891f, 0.08470127f),
            G(PEBBLE, 459.29007f, 1987.5732f, 3.3703616f, -13.155343f, -6.115925f, -2.3147225f),
            G(MOON, 645.0019f, 2081.993f, 20.49169f, 10.496643f, -0.1349154f, -0.15497482f),
            G(DUST_MOTE, 537.9643f, 2017.235f, 4.002268f, 40.32528f, -2.8590767f, -0.7946967f),
            G(BOULDER, 584.65735f, 1943.803f, -33.49061f, 2.331953f, -5.0680985f, -0.6544087f),
            G(PEBBLE, 952.0249f, 1990.6587f, 18.304312f, 3.4412007f, 7.442785f, 0.56422603f),
            G(DUST_MOTE, 225.45578f, 2145.87f, -3.8084145f, 30.790405f, 4.1469593f, 1.2282071f),
            G(MOONLET, 477.6615f, 2103.4775f, 21.93203f, 53.403587f, 1.7011285f, 0.31356832f),
            G(BOULDER, 142.34042f, 2119.8594f, 3.9599504f, -19.024178f, -0.6176841f, 0.041170016f),
            G(STRANGE_PLANET, 305.24075f, 2022.4471f, 34.102154f, 18.515968f, 0.30729932f, 0.033504896f),
            G(BOULDER, 790.65393f, 2120.2605f, 24.557827f, 2.8073616f, 0.38657892f, 0.10936811f),
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
