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
    STRANGE_PLANET("HOME-ORB", 0.128f, 0.40f),
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

/** Lifetime (frames) of a regular merge burst effect. */
const val POP_MAX = 26

/** Lifetime (frames) of the dramatic Void-Consumption burst. */
const val VOID_POP_MAX = 54

/** Frames an orb takes to spiral into the forming void. */
const val CONSUME_MAX = 48

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

/** An orb being drawn into a forming black hole (Void Consumption). */
data class ConsumingOrb(
    val tier: MergeTier,
    val startX: Float,
    val startY: Float,
    val cx: Float,
    val cy: Float,
    val age: Int = 0,
)

enum class MergePhase { READY, PLAYING, GAME_OVER }

/** Solo, or a 1v1 Bluetooth score race (each device runs its own board). */
enum class MergeMode { SOLO, BT_HOST, BT_CLIENT }

/** Outcome of a competitive match, shown on the results overlay. */
enum class MatchResult { WIN, LOSE, TIE }

data class MergeState(
    val orbs: List<Orb> = emptyList(),
    val pops: List<Pop> = emptyList(),
    val consuming: List<ConsumingOrb> = emptyList(),
    val currentTier: MergeTier = MergeTier.DUST_MOTE,
    val nextTier: MergeTier = MergeTier.PEBBLE,
    /** The two tiers queued after [nextTier] — the "upcoming" column preview. */
    val upcoming: List<MergeTier> = emptyList(),
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
