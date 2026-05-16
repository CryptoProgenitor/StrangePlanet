package com.quokkalabs.strangeplanet.data.model

private var orbIdCounter = 0L
fun nextOrbId(): Long = ++orbIdCounter

/**
 * Cosmic agglomeration tiers. [radiusFrac] is the orb radius expressed as a
 * fraction of the containment vessel's width, so everything scales with the
 * screen. [next] is the tier produced when two of this tier merge.
 */
enum class MergeTier(val displayName: String, val radiusFrac: Float) {
    DUST_MOTE("INSIGNIFICANT SPECK", 0.034f),
    PEBBLE("SMALL HARD SPHERE", 0.046f),
    BOULDER("LARGE HARD SPHERE", 0.062f),
    MOONLET("MINOR SATELLITE", 0.082f),
    MOON("ORBITING COMPANION", 0.104f),
    STRANGE_PLANET("HOME. ALLEGEDLY.", 0.128f),
    GAS_GIANT("ENORMOUS GAS SPHERE", 0.152f),
    STAR("NEARBY LUMINOUS SPHERE", 0.178f),
    NEUTRON_STAR("DENSE STELLAR REMNANT", 0.204f),
    BLACK_HOLE("THE INEVITABLE VOID", 0.232f);

    val next: MergeTier? get() = entries.getOrNull(ordinal + 1)

    companion object {
        /** Tiers that can be handed to the player to drop (the small ones). */
        val DROPPABLE = listOf(DUST_MOTE, PEBBLE, BOULDER, MOONLET)
    }
}

data class Orb(
    val id: Long,
    val tier: MergeTier,
    val x: Float,
    val y: Float,
    val vx: Float = 0f,
    val vy: Float = 0f,
)

enum class MergePhase { READY, PLAYING, GAME_OVER }

data class MergeState(
    val orbs: List<Orb> = emptyList(),
    val currentTier: MergeTier = MergeTier.DUST_MOTE,
    val nextTier: MergeTier = MergeTier.PEBBLE,
    val spoutX: Float = 0f,
    val score: Int = 0,
    val highScore: Int = 0,
    val phase: MergePhase = MergePhase.READY,
    val canDrop: Boolean = false,
    val lastMergeName: String? = null,
    val voidFlash: Boolean = false,
    val screenWidth: Float = 0f,
    val screenHeight: Float = 0f,
    // Vessel geometry (computed once by the engine, carried in state for the UI)
    val vesselLeft: Float = 0f,
    val vesselRight: Float = 0f,
    val vesselTop: Float = 0f,
    val vesselBottom: Float = 0f,
)
