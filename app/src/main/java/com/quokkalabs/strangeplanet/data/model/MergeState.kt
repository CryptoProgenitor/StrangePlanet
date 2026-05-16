package com.quokkalabs.strangeplanet.data.model

private var orbIdCounter = 0L
fun nextOrbId(): Long = ++orbIdCounter

/**
 * Cosmic agglomeration tiers. [radiusFrac] is the orb radius expressed as a
 * fraction of the containment vessel's width, so everything scales with the
 * screen. [next] is the tier produced when two of this tier merge.
 */
/**
 * [mu] is the surface friction coefficient. Pairwise friction adds (rough
 * regolith bodies grip and spin each other; smooth/luminous bodies slide).
 */
enum class MergeTier(
    val displayName: String,
    val radiusFrac: Float,
    val mu: Float,
) {
    DUST_MOTE("INSIGNIFICANT SPECK", 0.034f, 0.55f),
    PEBBLE("SMALL HARD SPHERE", 0.046f, 0.60f),
    BOULDER("LARGE HARD SPHERE", 0.062f, 0.65f),
    MOONLET("MINOR SATELLITE", 0.082f, 0.45f),
    MOON("ORBITING COMPANION", 0.104f, 0.35f),
    STRANGE_PLANET("HOME. ALLEGEDLY.", 0.128f, 0.40f),
    GAS_GIANT("ENORMOUS GAS SPHERE", 0.152f, 0.22f),
    STAR("NEARBY LUMINOUS SPHERE", 0.178f, 0.16f),
    NEUTRON_STAR("DENSE STELLAR REMNANT", 0.204f, 0.14f),
    BLACK_HOLE("THE INEVITABLE VOID", 0.232f, 0.10f);

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
    // Rigid-body rotation: angle (radians) and angular velocity (rad/s).
    val angle: Float = 0f,
    val omega: Float = 0f,
)

/** Lifetime (frames) of a merge burst effect. */
const val POP_MAX = 16

/** Transient visual burst spawned at a merge point. Pure cosmetic. */
data class Pop(
    val id: Long,
    val x: Float,
    val y: Float,
    val radius: Float,
    val tier: MergeTier?,
    val big: Boolean = false,
    val age: Int = 0,
)

enum class MergePhase { READY, PLAYING, GAME_OVER }

data class MergeState(
    val orbs: List<Orb> = emptyList(),
    val pops: List<Pop> = emptyList(),
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
