package com.quokkalabs.strangeplanet.domain

import com.quokkalabs.strangeplanet.data.model.AsteroidGameState
import com.quokkalabs.strangeplanet.data.model.AsteroidInput
import com.quokkalabs.strangeplanet.data.model.AsteroidPhase
import com.quokkalabs.strangeplanet.data.model.Bullet
import com.quokkalabs.strangeplanet.data.model.Particle
import com.quokkalabs.strangeplanet.data.model.Rock
import com.quokkalabs.strangeplanet.data.model.RockSize
import com.quokkalabs.strangeplanet.data.model.Ship
import com.quokkalabs.strangeplanet.data.model.Ufo
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * Pure, Android-free Asteroids-style physics. All collisions reduce to
 * circle–circle (a bullet is a zero-radius circle), so the rendered sprite
 * art never has to match the hit geometry.
 *
 * Screen-space is pixels; every magnitude scales off the shorter screen
 * edge so behaviour is consistent across devices.
 */
class AsteroidEngine(
    private val screenWidth: Float,
    private val screenHeight: Float,
) {
    private val s = minOf(screenWidth, screenHeight)
    private val scale = s / 1080f

    // Hitbox radii.
    private val shipR = s * 0.040f
    private fun rockR(size: RockSize) = when (size) {
        RockSize.LARGE -> s * 0.072f
        RockSize.MEDIUM -> s * 0.044f
        RockSize.SMALL -> s * 0.026f
    }
    private val ufoR = s * 0.050f

    // Tunable physics (at 1080-px reference, then scaled).
    private val rotSpeed = 4.5f
    private val thrustAccel = 0.20f * scale
    private val drag = 0.988f
    private val maxSpeed = 9f * scale
    private val bulletSpeed = 11f * scale
    private val bulletLife = 55
    private val fireInterval = 9
    private val hyperspaceCooldownTicks = 240
    private val invincibleSpawnTicks = 110
    private val ufoSpawnInterval = 620
    private val ufoShootInterval = 120

    // Deterministic LCG — keeps the state data class free of RNG noise.
    private var rng: Int = 0x9E3779B9.toInt()
    private fun nextRand(): Float {
        rng = rng * 1664525 + 1013904223
        return ((rng ushr 8) and 0xFFFFFF) / 16777216f
    }
    private fun rand(min: Float, max: Float) = min + nextRand() * (max - min)

    private fun wrap(v: Float, max: Float): Float = when {
        v < 0f -> v + max
        v >= max -> v - max
        else -> v
    }

    private fun rockSpeed(size: RockSize, level: Int): Float {
        val base = when (size) {
            RockSize.LARGE -> 1.3f
            RockSize.MEDIUM -> 1.9f
            RockSize.SMALL -> 2.6f
        }
        return (base + level * 0.35f) * scale
    }

    private fun spawnRocks(level: Int, shipX: Float, shipY: Float): List<Rock> {
        val count = level + 3
        val out = ArrayList<Rock>(count)
        repeat(count) {
            var rx: Float
            var ry: Float
            do {
                rx = rand(0f, screenWidth)
                ry = rand(0f, screenHeight)
            } while (hypot(rx - shipX, ry - shipY) < s * 0.28f)
            val dir = rand(0f, 6.2831855f)
            val sp = rockSpeed(RockSize.LARGE, level)
            out.add(
                Rock(
                    x = rx,
                    y = ry,
                    vx = cos(dir) * sp,
                    vy = sin(dir) * sp,
                    size = RockSize.LARGE,
                    angleDeg = rand(0f, 360f),
                    spin = rand(-2.2f, 2.2f),
                ),
            )
        }
        return out
    }

    private fun centredShip() = Ship(
        x = screenWidth / 2f,
        y = screenHeight / 2f,
        angleDeg = -90f,
        invincibleTicks = invincibleSpawnTicks,
    )

    fun createInitialState(
        level: Int = 1,
        score: Int = 0,
        lives: Int = 3,
        highScore: Int = 0,
        extraLifeAwarded: Int = 0,
    ): AsteroidGameState {
        val ship = centredShip()
        return AsteroidGameState(
            ship = ship,
            rocks = spawnRocks(level, ship.x, ship.y),
            score = score,
            highScore = highScore,
            lives = lives,
            level = level,
            phase = AsteroidPhase.READY,
            screenWidth = screenWidth,
            screenHeight = screenHeight,
            ufoSpawnTick = 0,
            extraLifeAwarded = extraLifeAwarded,
        )
    }

    fun startGame(state: AsteroidGameState): AsteroidGameState =
        if (state.phase == AsteroidPhase.READY)
            state.copy(phase = AsteroidPhase.PLAYING)
        else state

    /** Respawn the ship at centre after a death (lives already decremented). */
    fun respawnShip(state: AsteroidGameState): AsteroidGameState =
        state.copy(ship = centredShip(), phase = AsteroidPhase.PLAYING)

    fun pauseGame(state: AsteroidGameState): AsteroidGameState =
        if (state.phase == AsteroidPhase.PLAYING)
            state.copy(phase = AsteroidPhase.PAUSED)
        else state

    fun resumeGame(state: AsteroidGameState): AsteroidGameState =
        if (state.phase == AsteroidPhase.PAUSED)
            state.copy(phase = AsteroidPhase.PLAYING)
        else state

    private fun explosion(
        x: Float,
        y: Float,
        n: Int,
        life: Int,
        cosmic: Boolean,
    ): List<Particle> = (0 until n).map {
        val dir = rand(0f, 6.2831855f)
        val sp = rand(1.5f, 4.0f) * scale
        Particle(
            x = x,
            y = y,
            vx = cos(dir) * sp,
            vy = sin(dir) * sp,
            life = life,
            maxLife = life,
            cosmic = cosmic,
        )
    }

    private fun splitRock(r: Rock, level: Int): List<Rock> {
        val nextSize = when (r.size) {
            RockSize.LARGE -> RockSize.MEDIUM
            RockSize.MEDIUM -> RockSize.SMALL
            RockSize.SMALL -> return emptyList()
        }
        val sp = rockSpeed(nextSize, level)
        return (0 until 2).map { i ->
            val dir = rand(0f, 6.2831855f) + if (i == 0) 0.4f else -0.4f
            Rock(
                x = r.x,
                y = r.y,
                vx = cos(dir) * sp,
                vy = sin(dir) * sp,
                size = nextSize,
                angleDeg = rand(0f, 360f),
                spin = rand(-3f, 3f),
            )
        }
    }

    private fun rockScore(size: RockSize) = when (size) {
        RockSize.LARGE -> 20
        RockSize.MEDIUM -> 50
        RockSize.SMALL -> 100
    }

    fun update(state: AsteroidGameState, input: AsteroidInput): AsteroidGameState {
        if (state.phase != AsteroidPhase.PLAYING) return state

        var score = state.score
        var lives = state.lives
        val particles = ArrayList(state.particles)
        var saying = state.activeSaying

        // ── Ship ────────────────────────────────────────────────────────────
        var ship = state.ship
        var bullets = ArrayList(state.bullets)
        if (ship != null) {
            var sh = ship
            var angle = sh.angleDeg
            if (input.rotLeft) angle -= rotSpeed
            if (input.rotRight) angle += rotSpeed
            val rad = angle * 0.017453292f
            var vx = sh.vx
            var vy = sh.vy
            if (input.thrust) {
                vx += cos(rad) * thrustAccel
                vy += sin(rad) * thrustAccel
            }
            vx *= drag
            vy *= drag
            val sp = hypot(vx, vy)
            if (sp > maxSpeed) {
                vx = vx / sp * maxSpeed
                vy = vy / sp * maxSpeed
            }
            var x = wrap(sh.x + vx, screenWidth)
            var y = wrap(sh.y + vy, screenHeight)
            var inv = (sh.invincibleTicks - 1).coerceAtLeast(0)
            var fireCd = (sh.fireCooldown - 1).coerceAtLeast(0)
            var hyperCd = (sh.hyperspaceCooldown - 1).coerceAtLeast(0)

            if (input.fire && fireCd == 0) {
                bullets.add(
                    Bullet(
                        x = x + cos(rad) * shipR,
                        y = y + sin(rad) * shipR,
                        vx = vx + cos(rad) * bulletSpeed,
                        vy = vy + sin(rad) * bulletSpeed,
                        life = bulletLife,
                    ),
                )
                fireCd = fireInterval
            }

            if (input.hyperspace && hyperCd == 0) {
                particles.addAll(explosion(x, y, 8, 40, true))
                if (nextRand() < 0.15f) {
                    // Unfortunate calculation — instant destruction.
                    particles.addAll(explosion(x, y, 12, 80, false))
                    lives -= 1
                    saying = "THE CONVEYANCE MADE AN UNFORTUNATE CALCULATION."
                    return finishTick(
                        state, ship = null, bullets = bullets,
                        rocks = state.rocks, ufo = state.ufo,
                        ufoBullets = state.ufoBullets, particles = particles,
                        score = score, lives = lives, saying = saying,
                        died = true,
                    )
                }
                x = rand(screenWidth * 0.1f, screenWidth * 0.9f)
                y = rand(screenHeight * 0.1f, screenHeight * 0.9f)
                vx = 0f
                vy = 0f
                inv = invincibleSpawnTicks
                hyperCd = hyperspaceCooldownTicks
            }

            ship = sh.copy(
                x = x, y = y, vx = vx, vy = vy, angleDeg = angle,
                thrustOn = input.thrust, invincibleTicks = inv,
                fireCooldown = fireCd, hyperspaceCooldown = hyperCd,
            )
        }

        // ── Bullets ─────────────────────────────────────────────────────────
        bullets = ArrayList(
            bullets.mapNotNull { b ->
                if (b.life <= 1) null
                else b.copy(
                    x = wrap(b.x + b.vx, screenWidth),
                    y = wrap(b.y + b.vy, screenHeight),
                    life = b.life - 1,
                )
            },
        )

        // ── Rocks ───────────────────────────────────────────────────────────
        var rocks = ArrayList(
            state.rocks.map { r ->
                r.copy(
                    x = wrap(r.x + r.vx, screenWidth),
                    y = wrap(r.y + r.vy, screenHeight),
                    angleDeg = r.angleDeg + r.spin,
                )
            },
        )

        // Bullet × rock.
        val survivingBullets = ArrayList<Bullet>(bullets.size)
        for (b in bullets) {
            var hit = false
            var i = 0
            while (i < rocks.size) {
                val r = rocks[i]
                if (hypot(b.x - r.x, b.y - r.y) < rockR(r.size)) {
                    score += rockScore(r.size)
                    particles.addAll(explosion(r.x, r.y, 7, 35, false))
                    rocks.removeAt(i)
                    rocks.addAll(splitRock(r, state.level))
                    hit = true
                    break
                }
                i++
            }
            if (!hit) survivingBullets.add(b)
        }
        bullets = survivingBullets

        // ── UFO ─────────────────────────────────────────────────────────────
        var ufo = state.ufo
        var ufoBullets = ArrayList(
            state.ufoBullets.mapNotNull { b ->
                if (b.life <= 1) null
                else b.copy(
                    x = wrap(b.x + b.vx, screenWidth),
                    y = wrap(b.y + b.vy, screenHeight),
                    life = b.life - 1,
                )
            },
        )
        var ufoSpawnTick = state.ufoSpawnTick + 1
        if (ufo == null && rocks.isNotEmpty() && ufoSpawnTick >= ufoSpawnInterval) {
            ufoSpawnTick = 0
            val fromLeft = nextRand() < 0.5f
            val small = state.level >= 3
            ufo = Ufo(
                x = if (fromLeft) -ufoR else screenWidth + ufoR,
                y = rand(screenHeight * 0.15f, screenHeight * 0.85f),
                vx = (if (fromLeft) 1f else -1f) * 2.4f * scale,
                vy = 0f,
                small = small,
                shootCooldown = ufoShootInterval,
            )
            saying = "THE UNINVITED OVAL VESSEL HAS ARRIVED."
        }
        if (ufo != null) {
            var u = ufo
            val nx = u.x + u.vx
            if (nx < -ufoR * 2 || nx > screenWidth + ufoR * 2) {
                ufo = null
            } else {
                var cd = u.shootCooldown - 1
                if (cd <= 0) {
                    cd = ufoShootInterval
                    val dir = rand(0f, 6.2831855f)
                    ufoBullets.add(
                        Bullet(
                            x = u.x,
                            y = u.y,
                            vx = cos(dir) * bulletSpeed * 0.7f,
                            vy = sin(dir) * bulletSpeed * 0.7f,
                            life = bulletLife + 20,
                        ),
                    )
                }
                ufo = u.copy(x = nx, y = wrap(u.y + u.vy, screenHeight), shootCooldown = cd)
            }
        }

        // Player bullet × UFO.
        if (ufo != null) {
            val u = ufo
            val keep = ArrayList<Bullet>(bullets.size)
            var killed = false
            for (b in bullets) {
                if (!killed && hypot(b.x - u.x, b.y - u.y) < ufoR) {
                    score += if (u.small) 1000 else 200
                    particles.addAll(explosion(u.x, u.y, 8, 50, true))
                    killed = true
                } else {
                    keep.add(b)
                }
            }
            bullets = keep
            if (killed) ufo = null
        }

        // ── Ship damage ─────────────────────────────────────────────────────
        var died = false
        if (ship != null && ship.invincibleTicks == 0) {
            val sx = ship.x
            val sy = ship.y
            val rockHit = rocks.any { hypot(sx - it.x, sy - it.y) < rockR(it.size) + shipR }
            val ufoHit = ufo?.let { hypot(sx - it.x, sy - it.y) < ufoR + shipR } == true
            val ufoBulletHit = ufoBullets.any { hypot(sx - it.x, sy - it.y) < shipR }
            if (rockHit || ufoHit || ufoBulletHit) {
                particles.addAll(explosion(sx, sy, 12, 80, false))
                lives -= 1
                died = true
                saying = "THIS IS NOT IDEAL."
                ship = null
            }
        }

        return finishTick(
            state, ship = ship, bullets = bullets, rocks = rocks, ufo = ufo,
            ufoBullets = ufoBullets, particles = particles, score = score,
            lives = lives, saying = saying, died = died,
            ufoSpawnTick = ufoSpawnTick,
        )
    }

    private fun finishTick(
        prev: AsteroidGameState,
        ship: Ship?,
        bullets: List<Bullet>,
        rocks: List<Rock>,
        ufo: Ufo?,
        ufoBullets: List<Bullet>,
        particles: List<Particle>,
        score: Int,
        lives: Int,
        saying: String?,
        died: Boolean,
        ufoSpawnTick: Int = prev.ufoSpawnTick,
    ): AsteroidGameState {
        val livePart = particles.mapNotNull { p ->
            if (p.life <= 1) null
            else p.copy(
                x = p.x + p.vx,
                y = p.y + p.vy,
                life = p.life - 1,
            )
        }

        // Extra life every 10 000 points (capped at 5).
        var awarded = prev.extraLifeAwarded
        var finalLives = lives
        var finalSaying = saying
        val due = score / 10000
        if (due > awarded && finalLives < 5) {
            finalLives += 1
            awarded = due
            finalSaying = "AN ADDITIONAL ATTEMPT HAS BEEN ALLOCATED."
        } else if (due > awarded) {
            awarded = due
        }

        val cleared = rocks.isEmpty() && ufo == null && !died
        val phase = when {
            died && finalLives <= 0 -> AsteroidPhase.GAME_OVER
            died -> AsteroidPhase.DYING
            cleared -> AsteroidPhase.LEVEL_CLEARED
            else -> AsteroidPhase.PLAYING
        }
        if (phase == AsteroidPhase.LEVEL_CLEARED) {
            finalSaying = "VIBRATION EMOTION. TIER ${prev.level + 1} COMMENCING."
        }

        return prev.copy(
            ship = ship,
            rocks = rocks,
            bullets = bullets,
            ufo = ufo,
            ufoBullets = ufoBullets,
            particles = livePart,
            score = score,
            lives = finalLives,
            phase = phase,
            ufoSpawnTick = ufoSpawnTick,
            extraLifeAwarded = awarded,
            activeSaying = finalSaying,
        )
    }
}
