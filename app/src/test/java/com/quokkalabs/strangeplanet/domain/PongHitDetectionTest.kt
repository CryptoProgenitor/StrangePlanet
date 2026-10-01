package com.quokkalabs.strangeplanet.domain

import com.quokkalabs.strangeplanet.data.model.GameMode
import com.quokkalabs.strangeplanet.data.model.GamePhase
import com.quokkalabs.strangeplanet.data.model.PongGameState
import org.junit.Assert.assertTrue
import org.junit.Ignore
import org.junit.Test

/**
 * Can an honest-looking hit let the ball pass through the bat?
 *
 * PongEngine only tests for a paddle hit on the single tick in which the ball's
 * bottom crosses the line `playerPaddleY - paddleHeight` (which is drawn ~half a
 * paddle-height ABOVE the visible bar), using the bat's position at the end of
 * that tick.
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
    @Ignore("BUG: PongEngine only checks for a hit on the tick the ball crosses the hit line")
    @Test
    fun batArrivingWhileBallOverlapsTheBar_shouldDeflect() {
        val x = 300f
        // Tick 1: the ball's bottom crosses the hit line; the bat is elsewhere.
        var s = falling(x, ballBottom = hitLine - 2f, batX = 800f)
        s = engine.update(s, playerTouchX = 800f)
        assertTrue("setup: still falling after the crossing tick", s.ballVy > 0f)
        // Tick 2: the bat is now right under the ball, which overlaps the drawn bar.
        s = engine.update(s, playerTouchX = x)
        val ballBottom = s.ballY + r
        val ballTop = s.ballY - r
        assertTrue("setup: ball overlaps the drawn bar", ballBottom > barTop && ballTop < barBottom)
        assertTrue("ball overlapping the bat should bounce, vy=${s.ballVy}", s.ballVy < 0f)
    }

    /**
     * A fast swipe carries the bat right across the ball's path during the crossing
     * tick, but only the bat's end position is tested.
     */
    @Ignore("BUG: PongEngine tests only the bat's end-of-tick position, not the path it swept")
    @Test
    fun batSweepingAcrossTheBallDuringTheTick_shouldDeflect() {
        val x = 540f
        val reach = engine.paddleWidth / 2f + r
        // Bat starts just left of the hit zone and ends just right of it.
        var s = falling(x, ballBottom = hitLine - 2f, batX = x - reach - 5f)
        s = engine.update(s, playerTouchX = x + reach + 5f)
        assertTrue("bat swept through the ball's path, vy=${s.ballVy}", s.ballVy < 0f)
    }
}
