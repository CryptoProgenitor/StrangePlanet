package com.quokkalabs.strangeplanet.data.model

enum class PacPhase {
    READY, PLAYING, DYING, LEVEL_CLEARED, GAME_OVER, PAUSED
}

enum class PacDir(val dc: Int, val dr: Int) {
    NONE(0, 0),
    UP(0, -1),
    DOWN(0, 1),
    LEFT(-1, 0),
    RIGHT(1, 0);

    fun opposite(): PacDir = when (this) {
        UP -> DOWN
        DOWN -> UP
        LEFT -> RIGHT
        RIGHT -> LEFT
        NONE -> NONE
    }
}

enum class PacAvatar(val label: String) {
    BEING("The Being"),
    HOUND("The Hound"),
    FELINE("The Feline"),
    ROLLSUCK("The Cleaning Disc"),
    UNICORN("The Mythic Equine"),
}

data class PacSettings(
    val soundEnabled: Boolean = true,
    val showSayings: Boolean = true,
    val avatar: PacAvatar = PacAvatar.BEING,
)

/**
 * A grid-aligned mover. Logical position is (col,row); [progress] is the
 * 0f..1f interpolation from that tile toward the next tile in [dir].
 * [queuedDir] is the player's intended next turn (input queue) — it is
 * adopted the moment the entity is tile-centred and the turn is legal.
 */
data class PacEntity(
    val col: Int,
    val row: Int,
    val progress: Float = 0f,
    val dir: PacDir = PacDir.NONE,
    val queuedDir: PacDir = PacDir.NONE,
)

data class PacGameState(
    val being: PacEntity = PacEntity(0, 0),
    val cols: Int = 0,
    val rows: Int = 0,
    val tileSize: Float = 0f,
    val originX: Float = 0f,
    val originY: Float = 0f,
    // Remaining star tiles, keyed row * cols + col
    val pellets: Set<Int> = emptySet(),
    val totalPellets: Int = 0,
    // Wall tiles, keyed row * cols + col (static for the level)
    val walls: Set<Int> = emptySet(),
    val score: Int = 0,
    val lives: Int = 3,
    val level: Int = 1,
    val phase: PacPhase = PacPhase.READY,
    val screenWidth: Float = 0f,
    val screenHeight: Float = 0f,
    val activeSaying: String? = null,
)
