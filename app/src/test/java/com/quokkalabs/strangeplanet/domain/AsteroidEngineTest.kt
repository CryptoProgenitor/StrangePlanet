package com.quokkalabs.strangeplanet.domain

import com.quokkalabs.strangeplanet.data.model.AsteroidGameState
import com.quokkalabs.strangeplanet.data.model.AsteroidInput
import com.quokkalabs.strangeplanet.data.model.AsteroidPhase
import com.quokkalabs.strangeplanet.data.model.Bullet
import com.quokkalabs.strangeplanet.data.model.Rock
import com.quokkalabs.strangeplanet.data.model.RockSize
import com.quokkalabs.strangeplanet.data.model.Ship
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AsteroidEngineTest {

    private val w = 1080f
    private val h = 2340f
    private val engine = AsteroidEngine(w, h)
    private val noInput = AsteroidInput()

    /** PLAYING state with a hand-placed field; the default ship is far from everything and shielded. */
    private fun playing(
        rocks: List<Rock>,
        bullets: List<Bullet> = emptyList(),
        ship: Ship? = Ship(x = w / 2f, y = h * 0.85f, invincibleTicks = 100),
        lives: Int = 3,
    ): AsteroidGameState = engine.startGame(engine.createInitialState(lives = lives))
        .copy(rocks = rocks, bullets = bullets, ship = ship, ufo = null, ufoBullets = emptyList())

    private fun still(x: Float, y: Float, size: RockSize) = Rock(x, y, 0f, 0f, size)

    @Test
    fun bulletHittingLargeRock_splitsIntoTwoMediumsAndScores() {
        val s = playing(
            rocks = listOf(still(540f, 600f, RockSize.LARGE)),
            bullets = listOf(Bullet(540f, 600f, 0f, 0f, life = 10)),
        )
        val n = engine.update(s, noInput)
        assertEquals(2, n.rocks.size)
        assertTrue(n.rocks.all { it.size == RockSize.MEDIUM })
        assertTrue("fragments start where the parent was", n.rocks.all { it.x == 540f && it.y == 600f })
        assertEquals(20, n.score)
        assertTrue("bullet is consumed", n.bullets.isEmpty())
        assertEquals(AsteroidPhase.PLAYING, n.phase)
    }

    @Test
    fun destroyingTheLastSmallRock_clearsTheLevel() {
        val s = playing(
            rocks = listOf(still(300f, 300f, RockSize.SMALL)),
            bullets = listOf(Bullet(300f, 300f, 0f, 0f, life = 10)),
        )
        val n = engine.update(s, noInput)
        assertTrue("small rocks don't split", n.rocks.isEmpty())
        assertEquals(100, n.score)
        assertEquals(AsteroidPhase.LEVEL_CLEARED, n.phase)
    }

    @Test
    fun shipHitByRock_whenNotInvincible_losesLifeAndDies() {
        val s = playing(
            rocks = listOf(still(540f, 1170f, RockSize.LARGE)),
            ship = Ship(x = 540f, y = 1170f, invincibleTicks = 0),
        )
        val n = engine.update(s, noInput)
        assertEquals(AsteroidPhase.DYING, n.phase)
        assertEquals(2, n.lives)
        assertNull(n.ship)
    }

    @Test
    fun shipHitByRock_onLastLife_isGameOver() {
        val s = playing(
            rocks = listOf(still(540f, 1170f, RockSize.MEDIUM)),
            ship = Ship(x = 540f, y = 1170f, invincibleTicks = 0),
            lives = 1,
        )
        val n = engine.update(s, noInput)
        assertEquals(AsteroidPhase.GAME_OVER, n.phase)
        assertEquals(0, n.lives)
    }

    @Test
    fun invincibleShip_passesThroughRocksUnharmed() {
        val s = playing(
            rocks = listOf(still(540f, 1170f, RockSize.LARGE)),
            ship = Ship(x = 540f, y = 1170f, invincibleTicks = 50),
        )
        val n = engine.update(s, noInput)
        assertEquals(AsteroidPhase.PLAYING, n.phase)
        assertEquals(3, n.lives)
        assertNotNull(n.ship)
    }
}
