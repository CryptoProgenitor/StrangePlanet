package com.quokkalabs.strangeplanet.data.model

enum class AsteroidPhase {
    READY, PLAYING, DYING, LEVEL_CLEARED, GAME_OVER
}

enum class RockSize { LARGE, MEDIUM, SMALL }

/** Held-button input snapshot, consumed by the engine each tick. */
data class AsteroidInput(
    val rotLeft: Boolean = false,
    val rotRight: Boolean = false,
    val thrust: Boolean = false,
    val fire: Boolean = false,
    val hyperspace: Boolean = false,
)

data class Ship(
    val x: Float,
    val y: Float,
    val vx: Float = 0f,
    val vy: Float = 0f,
    // Degrees, 0 = pointing up (screen -Y), clockwise positive.
    val angleDeg: Float = 0f,
    val thrustOn: Boolean = false,
    val invincibleTicks: Int = 0,
    val fireCooldown: Int = 0,
    val hyperspaceCooldown: Int = 0,
)

data class Rock(
    val x: Float,
    val y: Float,
    val vx: Float,
    val vy: Float,
    val size: RockSize,
    val angleDeg: Float = 0f,
    val spin: Float = 0f,
)

data class Bullet(
    val x: Float,
    val y: Float,
    val vx: Float,
    val vy: Float,
    val life: Int,
)

data class Ufo(
    val x: Float,
    val y: Float,
    val vx: Float,
    val vy: Float,
    val small: Boolean,
    val shootCooldown: Int,
)

data class Particle(
    val x: Float,
    val y: Float,
    val vx: Float,
    val vy: Float,
    val life: Int,
    val maxLife: Int,
    val cosmic: Boolean = false,
)

data class AsteroidSettings(
    val soundEnabled: Boolean = true,
    // Alternate control bar order: fire, thrust, hyperspace, left, right.
    val altLayout: Boolean = false,
)

data class AsteroidGameState(
    val ship: Ship? = null,
    val rocks: List<Rock> = emptyList(),
    val bullets: List<Bullet> = emptyList(),
    val ufo: Ufo? = null,
    val ufoBullets: List<Bullet> = emptyList(),
    val particles: List<Particle> = emptyList(),
    val score: Int = 0,
    val highScore: Int = 0,
    val lives: Int = 3,
    val level: Int = 1,
    val phase: AsteroidPhase = AsteroidPhase.READY,
    val screenWidth: Float = 0f,
    val screenHeight: Float = 0f,
    val ufoSpawnTick: Int = 0,
    val extraLifeAwarded: Int = 0,
    val activeSaying: String? = null,
)
