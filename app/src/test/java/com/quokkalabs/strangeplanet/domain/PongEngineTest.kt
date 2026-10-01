package com.quokkalabs.strangeplanet.domain

import com.quokkalabs.strangeplanet.data.model.GameMode
import com.quokkalabs.strangeplanet.data.model.GamePhase
import com.quokkalabs.strangeplanet.data.model.GameSide
import com.quokkalabs.strangeplanet.data.model.PongGameState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PongEngineTest {

    private val w = 1080f
    private val h = 2340f
    private val engine = PongEngine(w, h)

    private fun playing(mode: GameMode = GameMode.TWO_PLAYER): PongGameState =
        engine.createInitialState(mode).copy(phase = GamePhase.PLAYING)

    private fun runUntilNotPlaying(start: PongGameState, maxTicks: Int = 300): PongGameState {
        var s = start
        var i = 0
        while (s.phase == GamePhase.PLAYING && i < maxTicks) {
            s = engine.update(s, playerTouchX = null)
            i++
        }
        return s
    }

    @Test
    fun ballReflectsOffLeftWall() {
        val s = playing().copy(ballX = engine.ballRadius + 3f, ballY = h / 2f, ballVx = -12f, ballVy = 5f)
        val n = engine.update(s, playerTouchX = null)
        assertEquals(engine.ballRadius, n.ballX, 1e-3f)
        assertEquals(12f, n.ballVx, 1e-4f)
        assertEquals(5f, n.ballVy, 1e-4f)
        assertTrue(n.wallBounced)
    }

    @Test
    fun ballReflectsOffRightWall() {
        val s = playing().copy(ballX = w - engine.ballRadius - 3f, ballY = h / 2f, ballVx = 12f, ballVy = -5f)
        val n = engine.update(s, playerTouchX = null)
        assertEquals(w - engine.ballRadius, n.ballX, 1e-3f)
        assertEquals(-12f, n.ballVx, 1e-4f)
        assertEquals(-5f, n.ballVy, 1e-4f)
        assertTrue(n.wallBounced)
    }

    @Test
    fun ballPastAiPaddle_scoresForPlayer() {
        val s = playing(GameMode.SINGLE_PLAYER).copy(
            ballX = w / 2f, ballY = engine.aiPaddleY - 40f, ballVx = 0f, ballVy = -30f,
        )
        val n = runUntilNotPlaying(s)
        assertEquals(GamePhase.POINT_SCORED, n.phase)
        assertEquals(1, n.playerScore)
        assertEquals(0, n.aiScore)
        assertEquals(GameSide.PLAYER, n.lastScorer)
        assertEquals(GameSide.PLAYER, n.activeSaying?.first)
    }

    @Test
    fun ballPastPlayerPaddle_scoresForAi() {
        val s = playing(GameMode.SINGLE_PLAYER).copy(
            ballX = w / 2f, ballY = engine.playerPaddleY + 40f, ballVx = 0f, ballVy = 30f,
        )
        val n = runUntilNotPlaying(s)
        assertEquals(GamePhase.POINT_SCORED, n.phase)
        assertEquals(0, n.playerScore)
        assertEquals(1, n.aiScore)
        assertEquals(GameSide.AI, n.lastScorer)
    }

    @Test
    fun afterPointPause_ballIsServedTowardsTheConceder() {
        var s = runUntilNotPlaying(
            playing(GameMode.SINGLE_PLAYER).copy(ballX = w / 2f, ballY = engine.aiPaddleY - 40f, ballVy = -30f),
        )
        assertEquals(GamePhase.POINT_SCORED, s.phase)
        var ticks = 0
        while (s.phase == GamePhase.POINT_SCORED && ticks < 200) {
            s = engine.update(s, playerTouchX = null)
            ticks++
        }
        assertEquals(GamePhase.PLAYING, s.phase)
        assertTrue("player scored, so the serve heads down to the player", s.ballVy > 0f)
    }

    @Test
    fun playerPaddleHit_reversesVerticalDirection() {
        val s = playing().copy(
            ballX = w / 2f,
            ballY = engine.playerPaddleY - engine.paddleHeight - engine.ballRadius - 5f,
            ballVx = 0f,
            ballVy = engine.ballBaseSpeed,
            playerPaddleX = w / 2f,
        )
        val n = engine.update(s, playerTouchX = w / 2f)
        assertTrue("ball should now travel up, vy=${n.ballVy}", n.ballVy < 0f)
        assertEquals(-engine.ballBaseSpeed, n.ballVy, 1e-3f) // centre hit: straight back
        // Bounces off the drawn bar's top edge (the bar is centred on playerPaddleY).
        assertEquals(engine.playerPaddleY - engine.paddleHeight / 2f - engine.ballRadius, n.ballY, 1e-3f)
        assertEquals(1, n.rally)
        assertEquals(1f, n.playerHitPulse, 0f)
    }

    @Test
    fun topPaddleHit_reversesVerticalDirection_andOffCentreHitDeflects() {
        val s = playing().copy(
            ballX = w / 2f + engine.paddleWidth * 0.4f, // right half of the paddle
            ballY = engine.aiPaddleY + engine.paddleHeight + engine.ballRadius + 5f,
            ballVx = 0f,
            ballVy = -engine.ballBaseSpeed,
            aiPaddleX = w / 2f,
        )
        val n = engine.update(s, playerTouchX = null, player2TouchX = w / 2f)
        assertTrue("ball should now travel down, vy=${n.ballVy}", n.ballVy > 0f)
        assertTrue("off-centre hit deflects right, vx=${n.ballVx}", n.ballVx > 0f)
        assertEquals(1, n.rally)
        assertEquals(1f, n.aiHitPulse, 0f)
    }
}
