package com.quokkalabs.strangeplanet.domain

import com.quokkalabs.strangeplanet.data.model.DifficultyLevel
import com.quokkalabs.strangeplanet.data.model.Invader
import com.quokkalabs.strangeplanet.data.model.InvaderType
import com.quokkalabs.strangeplanet.data.model.SIParticle
import com.quokkalabs.strangeplanet.data.model.SIShieldBlock
import com.quokkalabs.strangeplanet.data.model.SIPhase
import com.quokkalabs.strangeplanet.data.model.SIProjectile
import com.quokkalabs.strangeplanet.data.model.SpaceInvadersState
import kotlin.random.Random

class SpaceInvadersEngine(
    private val screenWidth: Float,
    private val screenHeight: Float,
    private val difficulty: DifficultyLevel = DifficultyLevel.STANDARD,
) {
    companion object {
        private const val COLS = 8
        private const val ROWS = 5
        private const val HIT_PAUSE_FRAMES = 60
    }

    // ── Difficulty multiplier table ────────────────────────────────────────
    private val startLives = when (difficulty) {
        DifficultyLevel.GENTLE -> 5
        DifficultyLevel.STANDARD -> 3
        DifficultyLevel.AGGRESSIVE -> 2
    }
    private val speedMultiplier = when (difficulty) {
        DifficultyLevel.GENTLE -> 0.7f
        DifficultyLevel.STANDARD -> 1.0f
        DifficultyLevel.AGGRESSIVE -> 1.4f
    }
    private val enemyFireMultiplier = when (difficulty) {
        DifficultyLevel.GENTLE -> 0.5f
        DifficultyLevel.STANDARD -> 1.0f
        DifficultyLevel.AGGRESSIVE -> 1.8f
    }
    private val fireInterval = when (difficulty) {
        DifficultyLevel.GENTLE -> 12
        DifficultyLevel.STANDARD -> 15
        DifficultyLevel.AGGRESSIVE -> 19
    }
    private val playerWidthMultiplier = when (difficulty) {
        DifficultyLevel.GENTLE -> 1.2f
        DifficultyLevel.STANDARD -> 1.0f
        DifficultyLevel.AGGRESSIVE -> 0.8f
    }

    // ── Derived dimensions ─────────────────────────────────────────────────
    val playerWidth = screenWidth * 0.12f * playerWidthMultiplier
    private val playerHeight = screenHeight * 0.04f
    val playerY = screenHeight * 0.82f
    val invaderSize = screenWidth * 0.07f
    private val playerProjectileSpeed = screenHeight * 0.014f
    private val enemyProjectileSpeed = screenHeight * 0.007f * speedMultiplier
    private val invaderSpacingX = screenWidth / (COLS + 1).toFloat()
    private val invaderSpacingY = screenHeight * 0.06f
    private val invaderBaseSpeed = screenWidth * 0.0015f * speedMultiplier
    private val invaderStepDown = screenHeight * 0.025f
    private val shieldBlockSize = screenWidth * 0.022f
    private val shieldY = screenHeight * 0.68f
    private val numShields = 4

    private val killSayings = listOf(
        "The entity has been neutralised!",
        "Successful projectile trajectory!",
        "One fewer descending creature!",
        "My aim calibration is adequate!",
        "The creature has ceased descending!",
        "A direct impact on the quadruped!",
    )

    private val deathSayings = listOf(
        "I have been struck by a projectile!",
        "The hostile entity's aim was precise!",
        "My physical form sustained damage!",
        "An unwelcome projectile interaction!",
    )

    private val waveClearSayings = listOf(
        "All entities have been neutralised!",
        "The wave of creatures has been repelled!",
        "The descending creatures are defeated!",
        "Atmospheric defence successful!",
    )

    fun createInitialState(
        wave: Int = 1,
        score: Int = 0,
        lives: Int = startLives,
    ): SpaceInvadersState {
        val invaders = mutableListOf<Invader>()
        val startX = invaderSpacingX
        val startY = screenHeight * 0.10f +
            (wave - 1).coerceAtMost(3) * invaderStepDown

        for (row in 0 until ROWS) {
            val type = when {
                row < 1 -> InvaderType.DOG
                row < 3 -> InvaderType.CAT
                else -> InvaderType.FOOT_FABRIC_TUBE
            }
            for (col in 0 until COLS) {
                invaders.add(
                    Invader(
                        row = row,
                        col = col,
                        type = type,
                        x = startX + col * invaderSpacingX,
                        y = startY + row * invaderSpacingY,
                    ),
                )
            }
        }

        return SpaceInvadersState(
            playerX = screenWidth / 2f,
            playerY = playerY,
            playerWidth = playerWidth,
            invaders = invaders,
            wave = wave,
            score = score,
            lives = lives,
            screenWidth = screenWidth,
            screenHeight = screenHeight,
            shields = createShields(),
        )
    }

    fun update(
        state: SpaceInvadersState,
        playerTouchX: Float?,
    ): SpaceInvadersState {
        // Always tick particles even when not PLAYING
        val tickedParticles = state.particles
            .map { p -> p.copy(
                x = p.x + p.vx,
                y = p.y + p.vy,
                alpha = p.alpha - (1f / 18f),
                life = p.life - 1,
            ) }
            .filter { it.life > 0 && it.alpha > 0f }

        val withParticles = state.copy(particles = tickedParticles)

        return when (withParticles.phase) {
            SIPhase.READY -> withParticles
            SIPhase.PLAYING -> updatePlaying(withParticles, playerTouchX)
            SIPhase.PAUSED -> withParticles
            SIPhase.PLAYER_HIT -> updateHitPause(withParticles)
            SIPhase.WAVE_CLEAR -> withParticles
            SIPhase.GAME_OVER -> withParticles
        }
    }

    fun startGame(state: SpaceInvadersState): SpaceInvadersState =
        state.copy(phase = SIPhase.PLAYING)

    // ── Main game loop ──────────────────────────────────────────────────────

    private fun updatePlaying(
        state: SpaceInvadersState,
        playerTouchX: Float?,
    ): SpaceInvadersState {
        val halfPlayer = playerWidth / 2f

        // 1. Move player
        val newPlayerX = if (playerTouchX != null) {
            playerTouchX.coerceIn(halfPlayer, screenWidth - halfPlayer)
        } else {
            state.playerX
        }

        // 2. Move invaders
        val aliveInvaders = state.invaders.filter { it.alive }
        if (aliveInvaders.isEmpty()) {
            return state.copy(
                phase = SIPhase.WAVE_CLEAR,
                activeSaying = waveClearSayings.random(),
            )
        }

        val speedMul = 1f + (ROWS * COLS - aliveInvaders.size) * 0.03f
        val waveMul = 1f + (state.wave - 1) * 0.12f
        val invaderSpeed = invaderBaseSpeed * speedMul * waveMul
        var direction = state.invaderDirection
        var needsStepDown = false

        for (inv in aliveInvaders) {
            val nextX = inv.x + invaderSpeed * direction
            if (nextX + invaderSize / 2f > screenWidth - invaderSize * 0.3f ||
                nextX - invaderSize / 2f < invaderSize * 0.3f
            ) {
                needsStepDown = true
                break
            }
        }

        val movedInvaders = state.invaders.map { inv ->
            if (!inv.alive) return@map inv
            if (needsStepDown) {
                inv.copy(y = inv.y + invaderStepDown)
            } else {
                inv.copy(x = inv.x + invaderSpeed * direction)
            }
        }

        if (needsStepDown) direction = -direction

        // 3. Auto-fire
        var fireCounter = state.fireCounter + 1
        var playerProjectiles = state.playerProjectiles
        if (playerTouchX != null && fireCounter >= fireInterval) {
            fireCounter = 0
            playerProjectiles = playerProjectiles + SIProjectile(
                newPlayerX, playerY - playerHeight, true,
            )
        }

        // 4. Move projectiles
        playerProjectiles = playerProjectiles
            .map { it.copy(y = it.y - playerProjectileSpeed) }
            .filter { it.y > 0f }

        var enemyProjectiles = state.enemyProjectiles
            .map { it.copy(y = it.y + enemyProjectileSpeed) }
            .filter { it.y < screenHeight }

        // 5. Enemy fire
        val updatedAlive = movedInvaders.filter { it.alive }
        val fireChance = 0.015 * enemyFireMultiplier * (1.0 + (state.wave - 1) * 0.25)
        if (updatedAlive.isNotEmpty() && Math.random() < fireChance) {
            val shooter = updatedAlive.random()
            enemyProjectiles = enemyProjectiles + SIProjectile(
                shooter.x, shooter.y + invaderSize / 2f, false,
            )
        }

        // 6. Collision: projectiles vs shields
        val mutableShields = state.shields.toMutableList()
        val shieldHitX = shieldBlockSize / 2f
        // Expand vertical hit zone to prevent fast projectiles tunnelling through
        val shieldHitY = shieldBlockSize / 2f + playerProjectileSpeed * 0.6f

        // Player shots absorbed by shields
        val shieldFilteredPlayerShots = mutableListOf<SIProjectile>()
        for (proj in playerProjectiles) {
            var absorbed = false
            for (i in mutableShields.indices) {
                val b = mutableShields[i]
                if (!b.alive) continue
                if (proj.x in (b.x - shieldHitX)..(b.x + shieldHitX) &&
                    proj.y in (b.y - shieldHitY)..(b.y + shieldHitY)
                ) {
                    mutableShields[i] = b.copy(alive = false)
                    absorbed = true
                    break
                }
            }
            if (!absorbed) shieldFilteredPlayerShots.add(proj)
        }
        playerProjectiles = shieldFilteredPlayerShots

        // Enemy shots absorbed by shields
        val shieldFilteredEnemyShots = mutableListOf<SIProjectile>()
        for (proj in enemyProjectiles) {
            var absorbed = false
            for (i in mutableShields.indices) {
                val b = mutableShields[i]
                if (!b.alive) continue
                if (proj.x in (b.x - shieldHitX)..(b.x + shieldHitX) &&
                    proj.y in (b.y - shieldHitY)..(b.y + shieldHitY)
                ) {
                    mutableShields[i] = b.copy(alive = false)
                    absorbed = true
                    break
                }
            }
            if (!absorbed) shieldFilteredEnemyShots.add(proj)
        }
        enemyProjectiles = shieldFilteredEnemyShots

        // Invaders destroy shields they pass through
        for (inv in movedInvaders) {
            if (!inv.alive) continue
            val invHs = invaderSize / 2f
            for (i in mutableShields.indices) {
                val b = mutableShields[i]
                if (!b.alive) continue
                if (b.x in (inv.x - invHs)..(inv.x + invHs) &&
                    b.y in (inv.y - invHs)..(inv.y + invHs)
                ) {
                    mutableShields[i] = b.copy(alive = false)
                }
            }
        }

        // 7. Collision: player shots vs invaders
        var score = state.score
        var saying: String? = state.activeSaying
        val hitInvaders = movedInvaders.toMutableList()
        val survivingPlayerShots = mutableListOf<SIProjectile>()
        val newParticles = mutableListOf<SIParticle>()

        for (proj in playerProjectiles) {
            var hit = false
            for (i in hitInvaders.indices) {
                val inv = hitInvaders[i]
                if (!inv.alive) continue
                val hs = invaderSize / 2f
                if (proj.x in (inv.x - hs)..(inv.x + hs) &&
                    proj.y in (inv.y - hs)..(inv.y + hs)
                ) {
                    hitInvaders[i] = inv.copy(alive = false)
                    score += inv.type.points
                    saying = killSayings.random()
                    newParticles.addAll(spawnParticles(inv))
                    hit = true
                    break
                }
            }
            if (!hit) survivingPlayerShots.add(proj)
        }

        // 7. Collision: enemy shots vs player
        var lives = state.lives
        var phase: SIPhase = SIPhase.PLAYING
        val survivingEnemyShots = mutableListOf<SIProjectile>()

        for (proj in enemyProjectiles) {
            if (proj.x in (newPlayerX - halfPlayer)..(newPlayerX + halfPlayer) &&
                proj.y in (playerY - playerHeight)..(playerY + playerHeight)
            ) {
                lives--
                saying = deathSayings.random()
                phase = if (lives <= 0) SIPhase.GAME_OVER else SIPhase.PLAYER_HIT
            } else {
                survivingEnemyShots.add(proj)
            }
        }

        // 8. Invaders breached player zone?
        val lowest = hitInvaders.filter { it.alive }.maxByOrNull { it.y }
        if (lowest != null && lowest.y + invaderSize / 2f >= playerY - playerHeight) {
            phase = SIPhase.GAME_OVER
            saying = "The entities have breached our position!"
        }

        // 9. Wave clear?
        if (phase == SIPhase.PLAYING && hitInvaders.none { it.alive }) {
            phase = SIPhase.WAVE_CLEAR
            saying = waveClearSayings.random()
        }

        return state.copy(
            playerX = newPlayerX,
            invaders = hitInvaders,
            playerProjectiles = survivingPlayerShots,
            enemyProjectiles = survivingEnemyShots,
            invaderDirection = direction,
            score = score,
            lives = lives,
            phase = phase,
            activeSaying = saying,
            fireCounter = fireCounter,
            hitPauseTimer = if (phase == SIPhase.PLAYER_HIT) HIT_PAUSE_FRAMES else 0,
            particles = state.particles + newParticles,
            shields = mutableShields,
        )
    }

    private fun createShields(): List<SIShieldBlock> {
        val blocks = mutableListOf<SIShieldBlock>()
        val spacing = screenWidth / (numShields + 1).toFloat()
        val bw = 5  // blocks wide
        val bh = 4  // blocks tall
        val bs = shieldBlockSize

        for (s in 0 until numShields) {
            val cx = spacing * (s + 1)
            val left = cx - (bw / 2f) * bs
            for (row in 0 until bh) {
                for (col in 0 until bw) {
                    // Cut out arch at bottom centre (row 3, cols 1-3 middle)
                    if (row == bh - 1 && col in 1..(bw - 2)) continue
                    blocks.add(
                        SIShieldBlock(
                            x = left + col * bs + bs / 2f,
                            y = shieldY + row * bs,
                        ),
                    )
                }
            }
        }
        return blocks
    }

    fun pauseGame(state: SpaceInvadersState): SpaceInvadersState =
        if (state.phase == SIPhase.PLAYING) state.copy(phase = SIPhase.PAUSED) else state

    fun resumeGame(state: SpaceInvadersState): SpaceInvadersState =
        if (state.phase == SIPhase.PAUSED) state.copy(phase = SIPhase.PLAYING) else state

    private fun spawnParticles(inv: Invader): List<SIParticle> {
        val color = when (inv.type) {
            InvaderType.DOG -> 0xFFFFB347    // orange-ish
            InvaderType.CAT -> 0xFFFF6B9D    // pink-ish
            InvaderType.FOOT_FABRIC_TUBE -> 0xFF87CEEB  // light blue
        }
        val speed = screenWidth * 0.006f
        return List(8) {
            val angle = (it / 8f) * 2f * Math.PI.toFloat() + Random.nextFloat() * 0.4f
            SIParticle(
                x = inv.x,
                y = inv.y,
                vx = kotlin.math.cos(angle) * speed * (0.6f + Random.nextFloat() * 0.8f),
                vy = kotlin.math.sin(angle) * speed * (0.6f + Random.nextFloat() * 0.8f),
                color = color,
            )
        }
    }

    private fun updateHitPause(state: SpaceInvadersState): SpaceInvadersState {
        val remaining = state.hitPauseTimer - 1
        return if (remaining <= 0) {
            state.copy(
                phase = SIPhase.PLAYING,
                enemyProjectiles = emptyList(),
                playerProjectiles = emptyList(),
                activeSaying = null,
                hitPauseTimer = 0,
            )
        } else {
            state.copy(hitPauseTimer = remaining)
        }
    }
}
