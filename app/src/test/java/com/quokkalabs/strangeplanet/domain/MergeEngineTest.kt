package com.quokkalabs.strangeplanet.domain

import com.quokkalabs.strangeplanet.data.model.MergePhase
import com.quokkalabs.strangeplanet.data.model.MergeState
import com.quokkalabs.strangeplanet.data.model.MergeTier
import com.quokkalabs.strangeplanet.data.model.Orb
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class MergeEngineTest {

    private val w = 1080f
    private val h = 2340f

    // Test orbs get negative ids so they can never collide with ids the engine
    // hands out from the global nextOrbId() counter (merge bookkeeping is by id).
    private var testId = 0L
    private fun orb(
        tier: MergeTier, x: Float, y: Float,
        vx: Float = 0f, vy: Float = 0f, omega: Float = 0f,
    ) = Orb(id = --testId, tier = tier, x = x, y = y, vx = vx, vy = vy, omega = omega)

    private fun newGame(): Pair<MergeEngine, MergeState> {
        val e = MergeEngine(w, h)
        return e to e.startGame(e.createInitialState(highScore = 0))
    }

    private fun MergeState.centreX() = (vesselLeft + vesselRight) / 2f

    @Test
    fun touchingSameTierOrbs_mergeIntoNextTier() {
        val (e, s0) = newGame()
        val r = e.radiusOf(MergeTier.PEBBLE)
        val cx = s0.centreX()
        val y = s0.vesselBottom - r
        var s = s0.copy(orbs = listOf(orb(MergeTier.PEBBLE, cx - r, y), orb(MergeTier.PEBBLE, cx + r, y)))

        var ticks = 0
        while (s.orbs.size == 2 && ticks < 10) {
            s = e.update(s, cx, drop = false)
            ticks++
        }
        assertEquals(1, s.orbs.size)
        val merged = s.orbs.single()
        assertEquals(MergeTier.BOULDER, merged.tier)
        assertEquals((MergeTier.BOULDER.ordinal + 1) * 5, s.score)
        assertEquals(MergeTier.BOULDER.displayName, s.lastMergeName)
        assertEquals("formed between its parents", cx, merged.x, r)
        assertTrue(s.pops.isNotEmpty())
    }

    @Test
    fun touchingDifferentTierOrbs_doNotMerge() {
        val (e, s0) = newGame()
        val rp = e.radiusOf(MergeTier.PEBBLE)
        val rb = e.radiusOf(MergeTier.BOULDER)
        val cx = s0.centreX()
        var s = s0.copy(
            orbs = listOf(
                orb(MergeTier.PEBBLE, cx - rp, s0.vesselBottom - rp),
                orb(MergeTier.BOULDER, cx + rb, s0.vesselBottom - rb),
            ),
        )
        repeat(60) { s = e.update(s, cx, drop = false) }
        assertEquals(2, s.orbs.size)
        assertEquals(0, s.score)
    }

    /**
     * 25 orbs on a 5 x 5 lattice (seeded tiers, velocities and spins), stepped for
     * 600 ticks with drop = false. [check] sees every orb after every tick.
     */
    private fun runCrowdedVessel(check: (tick: Int, s: MergeState, o: Orb, r: Float) -> Unit): MergeState {
        val (e, s0) = newGame()
        val rnd = Random(2024)
        val orbs = ArrayList<Orb>()
        // Spaced wide enough apart that nothing starts overlapping.
        for (row in 0 until 5) for (col in 0 until 5) {
            val tier = MergeTier.DROPPABLE[rnd.nextInt(MergeTier.DROPPABLE.size)]
            orbs += orb(
                tier,
                x = s0.vesselLeft + 100f + col * (s0.vesselRight - s0.vesselLeft - 200f) / 4f,
                y = s0.vesselTop + 150f + row * (s0.vesselBottom - s0.vesselTop - 250f) / 4f,
                vx = rnd.nextFloat() * 1200f - 600f,
                vy = rnd.nextFloat() * 1200f - 600f,
                omega = rnd.nextFloat() * 20f - 10f,
            )
        }
        var s = s0.copy(orbs = orbs)
        for (tick in 0 until 600) {
            s = e.update(s, s0.centreX(), drop = false)
            assertEquals("tick $tick", MergePhase.PLAYING, s.phase)
            for (o in s.orbs) check(tick, s, o, e.radiusOf(o.tier))
        }
        return s
    }

    @Test
    fun crowdedVessel_noNaN_andOrbsNeverEscapeTheVessel() {
        val end = runCrowdedVessel { tick, s, o, r ->
            val values = listOf(o.x, o.y, o.vx, o.vy, o.angle, o.omega)
            assertTrue("tick $tick: non-finite $o", values.all { it.isFinite() })
            // Centres always inside the vessel...
            assertTrue("tick $tick: escaped left $o", o.x >= s.vesselLeft)
            assertTrue("tick $tick: escaped right $o", o.x <= s.vesselRight)
            assertTrue("tick $tick: fell through floor $o", o.y <= s.vesselBottom)
            // ...and never sunk more than half a radius into a wall (see the strict test below).
            assertTrue("tick $tick: deep in left wall $o", o.x - r >= s.vesselLeft - r * 0.5f)
            assertTrue("tick $tick: deep in right wall $o", o.x + r <= s.vesselRight + r * 0.5f)
            assertTrue("tick $tick: deep in floor $o", o.y + r <= s.vesselBottom + r * 0.5f)
        }
        assertTrue("some merges should have happened", end.orbs.size < 25 && end.score > 0)
    }

    @Test
    fun crowdedVessel_orbsStayInsideWallsWithin1px() {
        runCrowdedVessel { tick, s, o, r ->
            assertTrue("tick $tick: left wall breached by $o", o.x - r >= s.vesselLeft - 1f)
            assertTrue("tick $tick: right wall breached by $o", o.x + r <= s.vesselRight + 1f)
            assertTrue("tick $tick: floor breached by $o", o.y + r <= s.vesselBottom + 1f)
        }
    }
}
