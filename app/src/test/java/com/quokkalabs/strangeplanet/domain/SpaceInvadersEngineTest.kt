package com.quokkalabs.strangeplanet.domain

import com.quokkalabs.strangeplanet.data.model.SIPhase
import com.quokkalabs.strangeplanet.data.model.SIProjectile
import com.quokkalabs.strangeplanet.data.model.SpaceInvadersState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SpaceInvadersEngineTest {

    private val w = 1080f
    private val h = 2340f
    private val engine = SpaceInvadersEngine(w, h)

    private fun playing(): SpaceInvadersState = engine.startGame(engine.createInitialState())

    /** A player shot placed just below an invader so this tick's upward move lands it inside the hitbox. */
    private fun shotJustBelow(x: Float, y: Float) = SIProjectile(x, y + 10f, fromPlayer = true)

    @Test
    fun playerShotKillsInvader_andScores() {
        val s = playing()
        val target = s.invaders.first { it.row == 4 && it.col == 3 }
        val n = engine.update(s.copy(playerProjectiles = listOf(shotJustBelow(target.x, target.y))), playerTouchX = null)

        val after = n.invaders.first { it.row == 4 && it.col == 3 }
        assertFalse(after.alive)
        assertEquals(target.type.points, n.score)
        assertEquals(39, n.invaders.count { it.alive })
        assertTrue("the shot is consumed", n.playerProjectiles.isEmpty())
        assertTrue("kill spawns particles", n.particles.isNotEmpty())
        assertEquals(SIPhase.PLAYING, n.phase)
    }

    @Test
    fun killingTheLastInvader_givesWaveClear() {
        val base = playing()
        val target = base.invaders.first { it.row == 0 && it.col == 0 }
        val s = base.copy(
            invaders = base.invaders.map { if (it === target) it else it.copy(alive = false) },
            playerProjectiles = listOf(shotJustBelow(target.x, target.y)),
            score = 500,
        )
        val n = engine.update(s, playerTouchX = null)
        assertEquals(SIPhase.WAVE_CLEAR, n.phase)
        assertEquals(500 + target.type.points, n.score)
        assertTrue(n.invaders.none { it.alive })
    }

    @Test
    fun noInvadersLeft_givesWaveClear() {
        val base = playing()
        val n = engine.update(base.copy(invaders = base.invaders.map { it.copy(alive = false) }), playerTouchX = null)
        assertEquals(SIPhase.WAVE_CLEAR, n.phase)
    }

    @Test
    fun invadersReachingPlayerRow_givesGameOver() {
        val base = playing()
        val lowestY = base.invaders.maxOf { it.y }
        val drop = engine.playerY - lowestY
        val s = base.copy(invaders = base.invaders.map { it.copy(y = it.y + drop) })
        val n = engine.update(s, playerTouchX = null)
        assertEquals(SIPhase.GAME_OVER, n.phase)
        // And the game stays over.
        assertEquals(SIPhase.GAME_OVER, engine.update(n, playerTouchX = w / 2f).phase)
    }

    @Test
    fun enemyShotOnPlayer_losesLife_thenResumes() {
        val base = playing()
        val shot = SIProjectile(base.playerX, engine.playerY - 5f, fromPlayer = false)
        var s = engine.update(base.copy(enemyProjectiles = listOf(shot)), playerTouchX = null)
        assertEquals(SIPhase.PLAYER_HIT, s.phase)
        assertEquals(base.lives - 1, s.lives)

        repeat(60) { s = engine.update(s, playerTouchX = null) }
        assertEquals(SIPhase.PLAYING, s.phase)
        assertTrue(s.enemyProjectiles.isEmpty())
        assertEquals(base.lives - 1, s.lives)
    }

    @Test
    fun enemyShotOnLastLife_givesGameOver() {
        val base = playing().copy(lives = 1)
        val shot = SIProjectile(base.playerX, engine.playerY - 5f, fromPlayer = false)
        val n = engine.update(base.copy(enemyProjectiles = listOf(shot)), playerTouchX = null)
        assertEquals(SIPhase.GAME_OVER, n.phase)
        assertEquals(0, n.lives)
    }
}
