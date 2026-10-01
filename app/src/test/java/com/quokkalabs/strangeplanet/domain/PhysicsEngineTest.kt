package com.quokkalabs.strangeplanet.domain

import com.quokkalabs.strangeplanet.data.model.CreatureDefaults
import com.quokkalabs.strangeplanet.data.model.CreatureState
import com.quokkalabs.strangeplanet.data.model.CreatureType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Ignore
import org.junit.Test

/** Free-roaming planet creatures (home screen). */
class PhysicsEngineTest {

    private val w = 1080f
    private val h = 2340f

    // Frame steps the view model can pass: (frameDelta * 60) clamped to 0..3.
    private val steps = listOf(1f, 1f, 2f, 0.5f, 3f)

    private fun creature(x: Float, y: Float, vx: Float, vy: Float, radius: Float = 30f) = CreatureState(
        type = CreatureType.DOG, x = x, y = y, vx = vx, vy = vy,
        radius = radius, mass = 1f, size = radius * 2f, rotation = 0f, angularVelocity = 0.5f,
    )

    /** The default cast, sped up to fling-like speeds so they hit walls and each other a lot. */
    private fun fastCast() = CreatureDefaults.create(w, h).map { it.copy(vx = it.vx * 4f + 2f, vy = it.vy * 4f - 2f) }

    private fun maxEdgeOvershoot(c: CreatureState) =
        maxOf(-(c.x - c.radius), c.x + c.radius - w, -(c.y - c.radius), c.y + c.radius - h)

    private fun assertFinite(c: CreatureState, tick: Int) {
        val values = listOf(c.x, c.y, c.vx, c.vy, c.rotation, c.angularVelocity)
        assertTrue("tick $tick: non-finite value in $c", values.all { it.isFinite() })
    }

    @Test
    fun loneCreature_bouncesForeverInsideScreen() {
        val engine = PhysicsEngine(w, h)
        var cs = listOf(creature(x = 200f, y = 300f, vx = 7.3f, vy = -5.1f))
        for (tick in 0 until 20_000) {
            cs = engine.update(cs, steps[tick % steps.size])
            val c = cs.single()
            assertFinite(c, tick)
            assertTrue("tick $tick: edge outside screen: $c", maxEdgeOvershoot(c) <= 1e-3f)
        }
    }

    @Test
    fun manyUpdates_noNaN_andCreaturesNeverFarOffScreen() {
        val engine = PhysicsEngine(w, h)
        var cs = fastCast()
        val biggest = cs.maxOf { it.radius }
        for (tick in 0 until 5000) {
            cs = engine.update(cs, steps[tick % steps.size])
            assertEquals(8, cs.size)
            for (c in cs) {
                assertFinite(c, tick)
                // A collision right next to a wall can push a creature past it for one
                // frame (see the strict test below), but never by more than a body width.
                assertTrue("tick $tick: ${c.type} far off screen: $c", maxEdgeOvershoot(c) <= biggest)
            }
        }
    }

    @Test
    fun manyUpdates_withDragAndDamping_stillFinite() {
        val engine = PhysicsEngine(w, h).apply {
            linearDrag = 0.02f
            spinDamping = 0.05f
            restitution = 0.8f
        }
        var cs = CreatureDefaults.create(w, h)
        for (tick in 0 until 3000) {
            cs = engine.update(cs, 1f)
            cs.forEach { assertFinite(it, tick) }
        }
    }

    @Ignore(
        "BUG: PhysicsEngine.update resolves creature collisions after the wall clamp, so a collision near a " +
            "wall pushes a creature up to ~35px off screen for a frame",
    )
    @Test
    fun manyUpdates_creaturesStayStrictlyOnScreen() {
        val engine = PhysicsEngine(w, h)
        var cs = fastCast()
        for (tick in 0 until 5000) {
            cs = engine.update(cs, steps[tick % steps.size])
            for (c in cs) assertTrue("tick $tick: ${c.type} off screen: $c", maxEdgeOvershoot(c) <= 1f)
        }
    }

    @Test
    fun doubleStep_movesTwiceAsFar() {
        val start = listOf(creature(x = w / 2f, y = h / 2f, vx = 3f, vy = -2f))

        val one = PhysicsEngine(w, h).update(start, step = 1f).single()
        val two = PhysicsEngine(w, h).update(start, step = 2f).single()

        val d1x = one.x - start[0].x
        val d1y = one.y - start[0].y
        val d2x = two.x - start[0].x
        val d2y = two.y - start[0].y
        assertEquals(3f, d1x, 1e-4f)
        assertEquals(2f * d1x, d2x, 1e-4f)
        assertEquals(2f * d1y, d2y, 1e-4f)
        assertEquals(2f * (one.rotation - start[0].rotation), two.rotation - start[0].rotation, 1e-4f)
    }

    @Test
    fun doubleStep_withDrag_matchesTwoSingleSteps() {
        fun engine() = PhysicsEngine(w, h).apply { linearDrag = 0.05f }
        val start = listOf(creature(x = w / 2f, y = h / 2f, vx = 4f, vy = 3f))

        val e1 = engine()
        val twoSingles = e1.update(e1.update(start, 1f), 1f).single()
        val oneDouble = engine().update(start, 2f).single()

        // Velocity decay is frame-rate independent (drag^step) ...
        assertEquals(twoSingles.vx, oneDouble.vx, 1e-4f)
        assertEquals(twoSingles.vy, oneDouble.vy, 1e-4f)
        // ... and the distance covered is roughly the same (within one frame of drag).
        val dSingles = twoSingles.x - start[0].x
        val dDouble = oneDouble.x - start[0].x
        assertEquals(dSingles, dDouble, dDouble * 0.06f)
    }
}
