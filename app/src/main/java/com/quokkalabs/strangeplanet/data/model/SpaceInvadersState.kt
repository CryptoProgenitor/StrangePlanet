package com.quokkalabs.strangeplanet.data.model

enum class SIPhase {
    READY, PLAYING, PAUSED, PLAYER_HIT, WAVE_CLEAR, GAME_OVER
}

enum class InvaderType(val points: Int) {
    DOG(30),
    CAT(20),
    FOOT_FABRIC_TUBE(10),
}

data class Invader(
    val row: Int,
    val col: Int,
    val type: InvaderType,
    val x: Float,
    val y: Float,
    val alive: Boolean = true,
)

data class SIProjectile(
    val x: Float,
    val y: Float,
    val fromPlayer: Boolean,
)

data class SIParticle(
    val x: Float,
    val y: Float,
    val vx: Float,
    val vy: Float,
    val alpha: Float = 1f,
    val color: Long = 0xFFFFFFFF,
    val life: Int = 18,
)

data class SIShieldBlock(
    val x: Float,
    val y: Float,
    val alive: Boolean = true,
)

data class SISettings(
    val soundEnabled: Boolean = true,
    val showSayings: Boolean = true,
    val difficulty: DifficultyLevel = DifficultyLevel.STANDARD,
)

data class SpaceInvadersState(
    val playerX: Float = 0f,
    val playerY: Float = 0f,
    val playerWidth: Float = 0f,
    val invaders: List<Invader> = emptyList(),
    val playerProjectiles: List<SIProjectile> = emptyList(),
    val enemyProjectiles: List<SIProjectile> = emptyList(),
    val score: Int = 0,
    val lives: Int = 3,
    val phase: SIPhase = SIPhase.READY,
    val wave: Int = 1,
    val invaderDirection: Int = 1,
    val screenWidth: Float = 0f,
    val screenHeight: Float = 0f,
    val activeSaying: String? = null,
    val fireCounter: Int = 0,
    val hitPauseTimer: Int = 0,
    val particles: List<SIParticle> = emptyList(),
    val shields: List<SIShieldBlock> = emptyList(),
)
