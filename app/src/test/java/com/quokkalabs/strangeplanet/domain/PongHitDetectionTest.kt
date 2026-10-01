package com.quokkalabs.strangeplanet.domain

import com.quokkalabs.strangeplanet.data.model.GameMode
import com.quokkalabs.strangeplanet.data.model.GamePhase
import com.quokkalabs.strangeplanet.data.model.PongGameState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * An honest-looking hit must not let the ball pass through the bat.
 *
 * PongEngine used to test for a hit only on the single tick the ball's bottom
 * crossed a line drawn ~half a bar above the visible bar, and only at the bat's
 * end-of-tick position. A hit now counts while the ball overlaps the drawn bar,
 * across the path the bat swept; a ball that is already past still scores.
 */
class PongHitDetectionTest {

    private val w = 1080f
    private val h = 2340f
    private val engine = PongEngine(w, h)
    private val r = engine.ballRadius
    private val hitLine = engine.playerPaddleY - engine.paddleHeight
    private val barTop = engine.playerPaddleY - engine.paddleHeight / 2f    // as drawn
    private val barBottom = engine.playerPaddleY + engine.paddleHeight / 2f // as drawn

    private fun falling(x: Float, ballBottom: Float, batX: Float): PongGameState =
        engine.createInitialState(GameMode.TWO_PLAYER).copy(
            phase = GamePhase.PLAYING,
            ballX = x,
            ballY = ballBottom - r,
            ballVx = 0f,
            ballVy = engine.ballBaseSpeed,
            playerPaddleX = batX,
        )

    /**
     * The bat arrives under the ball one tick late. The ball has crossed the hit
     * line (still above the drawn bar), so the engine never checks again, and the
     * ball then visibly overlaps the bat for several ticks while falling through it.
     */
    @Test
    fun batArrivingWhileBallOverlapsTheBar_shouldDeflect() {
        val x = 300f
        // Tick 1: the ball's bottom crosses the hit line; the bat is elsewhere.
        var s = falling(x, ballBottom = hitLine - 2f, batX = 800f)
        s = engine.update(s, playerTouchX = 800f)
        assertTrue("setup: still falling after the crossing tick", s.ballVy > 0f)
        // Tick 2: the bat is now right under the ball, which will overlap the drawn bar.
        val nextY = s.ballY + s.ballVy
        assertTrue("setup: ball overlaps the drawn bar", nextY + r > barTop && nextY - r < barBottom)
        s = engine.update(s, playerTouchX = x)
        assertTrue("ball overlapping the bat should bounce, vy=${s.ballVy}", s.ballVy < 0f)
    }

    /**
     * A fast swipe carries the bat right across the ball's path during the crossing
     * tick, but only the bat's end position is tested.
     */
    @Test
    fun batSweepingAcrossTheBallDuringTheTick_shouldDeflect() {
        val x = 540f
        val reach = engine.paddleWidth / 2f + r
        // Bat starts just left of the hit zone and ends just right of it.
        var s = falling(x, ballBottom = hitLine - 2f, batX = x - reach - 5f)
        s = engine.update(s, playerTouchX = x + reach + 5f)
        assertTrue("bat swept through the ball's path, vy=${s.ballVy}", s.ballVy < 0f)
    }

    /** A ball that has already passed the bar is a genuine miss, however the bat moves. */
    @Test
    fun ballAlreadyPastTheBar_stillScores() {
        val x = 300f
        var s = falling(x, ballBottom = barBottom + 2f * r + 4f, batX = 800f) // centre below the bar
        s = engine.update(s, playerTouchX = x)
        assertTrue("no bounce once the ball is past, vy=${s.ballVy}", s.ballVy > 0f)
        repeat(200) { if (s.phase == GamePhase.PLAYING) s = engine.update(s, playerTouchX = x) }
        assertEquals(GamePhase.POINT_SCORED, s.phase)
    }

    /** A clean hit bounces off the drawn bar's top edge. */
    @Test
    fun cleanHit_bouncesFromTheDrawnBarTop() {
        val x = 540f
        var s = falling(x, ballBottom = barTop - 2f, batX = x)
        s = engine.update(s, playerTouchX = x)
        assertTrue(s.ballVy < 0f)
        assertEquals(barTop - r, s.ballY, 1e-3f)
    }
}
