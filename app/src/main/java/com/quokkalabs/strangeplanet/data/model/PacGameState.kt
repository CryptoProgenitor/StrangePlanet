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
    FELINE("The Vibrating Creature"),
    ROLLSUCK("Rollsuck Supreme"),
    UNICORN("The Mythic Equine"),
}

enum class SeekerType {
    MINUTE_REMINDER,    // Blinky: direct chaser
    SOCIAL_ANXIETY,     // Pinky: 4-tiles-ahead ambush
    LOGICAL_DEBATER,    // Inky: vector flanker
    OPTIONAL_OBLIGATION, // Clyde: shy wanderer
}

enum class SeekerMode { SCATTER, CHASE, FRIGHTENED, EATEN }

data class SeekerEntity(
    val type: SeekerType,
    val col: Int,
    val row: Int,
    val progress: Float = 0f,
    val dir: PacDir = PacDir.LEFT,
    val mode: SeekerMode = SeekerMode.SCATTER,
    // While > 0 the (regenerated) seeker dwells in the pen, frozen, before
    // re-entering the maze. Set when EATEN eyes reach the home tile.
    val penTimer: Int = 0,
)

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
    // Remaining sock (power pellet) tiles, keyed row * cols + col
    val socks: Set<Int> = emptySet(),
    // Wall tiles, keyed row * cols + col (static for the level)
    val walls: Set<Int> = emptySet(),
    val score: Int = 0,
    val highScore: Int = 0,
    val lives: Int = 3,
    val level: Int = 1,
    val phase: PacPhase = PacPhase.READY,
    val screenWidth: Float = 0f,
    val screenHeight: Float = 0f,
    val activeSaying: String? = null,
    val seekers: List<SeekerEntity> = emptyList(),
    // Ticks elapsed in current scatter/chase wave cycle
    val waveTick: Int = 0,
    // Remaining frightened ticks (0 = not frightened)
    val frightenedTick: Int = 0,
    // Consecutive perished beings consumed in the current frightened window
    val frightenedCombo: Int = 0,
)
