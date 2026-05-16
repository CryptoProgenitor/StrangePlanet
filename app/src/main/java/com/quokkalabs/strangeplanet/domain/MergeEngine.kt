package com.quokkalabs.strangeplanet.domain

import com.quokkalabs.strangeplanet.data.model.MergePhase
import com.quokkalabs.strangeplanet.data.model.MergeState
import com.quokkalabs.strangeplanet.data.model.MergeTier
import com.quokkalabs.strangeplanet.data.model.Orb
import com.quokkalabs.strangeplanet.data.model.nextOrbId
import kotlin.math.hypot
import kotlin.random.Random

/**
 * Lightweight 2D circle physics for the cosmic agglomeration game. Semi-implicit
 * Euler with two substeps for stability, equal-mass collision impulses, and a
 * greedy merge pass. Creating a BLACK_HOLE triggers Void Consumption.
 */
class MergeEngine(
    private val screenWidth: Float,
    private val screenHeight: Float,
) {
    private val vesselWidth = screenWidth * 0.84f
    private val vesselLeft = (screenWidth - vesselWidth) / 2f
    private val vesselRight = vesselLeft + vesselWidth
    private val vesselTop = screenHeight * 0.26f
    private val vesselBottom = screenHeight * 0.93f

    private val gravity = screenHeight * 3.1f      // px / s^2
    private val restitution = 0.34f
    private val wallDamp = 0.52f
    private val airDrag = 0.999f
    private val maxSpeed = screenHeight * 2.4f

    private var cooldown = 0
    private var overflowTicks = 0

    fun radiusOf(tier: MergeTier): Float = tier.radiusFrac * vesselWidth

    fun createInitialState(highScore: Int): MergeState {
        cooldown = 0
        overflowTicks = 0
        return MergeState(
            orbs = emptyList(),
            currentTier = randomDrop(),
            nextTier = randomDrop(),
            spoutX = (vesselLeft + vesselRight) / 2f,
            score = 0,
            highScore = highScore,
            phase = MergePhase.READY,
            canDrop = false,
            screenWidth = screenWidth,
            screenHeight = screenHeight,
            vesselLeft = vesselLeft,
            vesselRight = vesselRight,
            vesselTop = vesselTop,
            vesselBottom = vesselBottom,
        )
    }

    fun startGame(s: MergeState): MergeState {
        cooldown = 0
        overflowTicks = 0
        return s.copy(
            phase = MergePhase.PLAYING,
            orbs = emptyList(),
            score = 0,
            currentTier = randomDrop(),
            nextTier = randomDrop(),
            canDrop = true,
            lastMergeName = null,
            voidFlash = false,
        )
    }

    private fun randomDrop(): MergeTier =
        MergeTier.DROPPABLE[Random.nextInt(MergeTier.DROPPABLE.size)]

    private fun clampSpout(x: Float, tier: MergeTier): Float {
        val r = radiusOf(tier)
        return x.coerceIn(vesselLeft + r, vesselRight - r)
    }

    /**
     * Advance one frame. [requestedSpoutX] always re-aims the spout; [drop]
     * releases the current orb when the cooldown has elapsed.
     */
    fun update(state: MergeState, requestedSpoutX: Float, drop: Boolean): MergeState {
        if (state.phase != MergePhase.PLAYING) return state

        var s = state.copy(spoutX = clampSpout(requestedSpoutX, state.currentTier))
        var orbs = s.orbs.toMutableList()
        var score = s.score
        var mergeName = s.lastMergeName
        var voidFlash = false
        var currentTier = s.currentTier
        var nextTier = s.nextTier

        // ── Drop ────────────────────────────────────────────────────────────
        if (drop && cooldown <= 0) {
            val r = radiusOf(currentTier)
            orbs.add(
                Orb(
                    id = nextOrbId(),
                    tier = currentTier,
                    x = s.spoutX,
                    y = vesselTop - r - 4f,
                    vy = screenHeight * 0.05f,
                ),
            )
            currentTier = nextTier
            nextTier = randomDrop()
            cooldown = 26
        }
        if (cooldown > 0) cooldown--

        // ── Integrate (2 substeps) ─────────────────────────────────────────
        val dt = (1f / 60f) / 2f
        repeat(2) {
            for (i in orbs.indices) {
                val o = orbs[i]
                var vx = o.vx
                var vy = o.vy + gravity * dt
                vx *= airDrag
                vy *= airDrag
                val sp = hypot(vx, vy)
                if (sp > maxSpeed) {
                    val k = maxSpeed / sp
                    vx *= k; vy *= k
                }
                orbs[i] = o.copy(x = o.x + vx * dt, y = o.y + vy * dt, vx = vx, vy = vy)
            }
            resolveWalls(orbs)
            repeat(3) { resolveCollisions(orbs) }
        }

        // ── Merge pass ──────────────────────────────────────────────────────
        val merged = HashSet<Long>()
        val survivors = ArrayList<Orb>(orbs.size)
        val spawned = ArrayList<Orb>()
        for (i in orbs.indices) {
            val a = orbs[i]
            if (a.id in merged) continue
            var didMerge = false
            for (j in i + 1 until orbs.size) {
                val b = orbs[j]
                if (b.id in merged || b.tier != a.tier) continue
                val ra = radiusOf(a.tier)
                val rb = radiusOf(b.tier)
                val d = hypot(b.x - a.x, b.y - a.y)
                // The collision solver separates same-tier orbs to exactly
                // (ra + rb); treat anything at/within contact as a merge.
                if (d < (ra + rb) * 1.06f) {
                    merged.add(a.id)
                    merged.add(b.id)
                    val mx = (a.x + b.x) / 2f
                    val my = (a.y + b.y) / 2f
                    val formed = a.tier.next
                    if (formed == MergeTier.BLACK_HOLE) {
                        // Void Consumption — the inevitable void.
                        val voidR = radiusOf(MergeTier.BLACK_HOLE) * 2.9f
                        var consumed = 0
                        for (k in orbs.indices) {
                            val c = orbs[k]
                            if (c.id in merged) continue
                            if (hypot(c.x - mx, c.y - my) < voidR + radiusOf(c.tier)) {
                                merged.add(c.id)
                                score += (c.tier.ordinal + 1) * 8
                                consumed++
                            }
                        }
                        score += 250 + consumed * 30
                        mergeName = "THE VOID CONSUMES."
                        voidFlash = true
                    } else if (formed != null) {
                        spawned.add(
                            Orb(
                                id = nextOrbId(),
                                tier = formed,
                                x = mx,
                                y = my,
                                vx = (a.vx + b.vx) / 2f,
                                vy = (a.vy + b.vy) / 2f,
                            ),
                        )
                        score += (formed.ordinal + 1) * 5
                        mergeName = formed.displayName
                    }
                    didMerge = true
                    break
                }
            }
            if (!didMerge) survivors.add(a)
        }
        survivors.removeAll { it.id in merged }
        survivors.addAll(spawned)
        orbs = survivors

        // ── Game over: a slow orb resting above the capacity line ──────────
        val overflowing = orbs.any { o ->
            (o.y - radiusOf(o.tier)) < vesselTop && hypot(o.vx, o.vy) < screenHeight * 0.18f
        }
        overflowTicks = if (overflowing) overflowTicks + 1 else 0
        val phase = if (overflowTicks > 110) MergePhase.GAME_OVER else MergePhase.PLAYING

        s = s.copy(
            orbs = orbs,
            score = score,
            currentTier = currentTier,
            nextTier = nextTier,
            canDrop = cooldown <= 0 && phase == MergePhase.PLAYING,
            lastMergeName = mergeName,
            voidFlash = voidFlash,
            phase = phase,
        )
        return s
    }

    private fun resolveWalls(orbs: MutableList<Orb>) {
        for (i in orbs.indices) {
            val o = orbs[i]
            val r = radiusOf(o.tier)
            var x = o.x; var y = o.y; var vx = o.vx; var vy = o.vy
            if (x - r < vesselLeft) {
                x = vesselLeft + r; vx = -vx * wallDamp
            } else if (x + r > vesselRight) {
                x = vesselRight - r; vx = -vx * wallDamp
            }
            if (y + r > vesselBottom) {
                y = vesselBottom - r
                vy = -vy * wallDamp
                vx *= 0.86f // floor friction
            }
            orbs[i] = o.copy(x = x, y = y, vx = vx, vy = vy)
        }
    }

    private fun resolveCollisions(orbs: MutableList<Orb>) {
        for (i in orbs.indices) {
            for (j in i + 1 until orbs.size) {
                val a = orbs[i]
                val b = orbs[j]
                val ra = radiusOf(a.tier)
                val rb = radiusOf(b.tier)
                val dx = b.x - a.x
                val dy = b.y - a.y
                var dist = hypot(dx, dy)
                val minDist = ra + rb
                if (dist < minDist && dist > 0.0001f) {
                    val nx = dx / dist
                    val ny = dy / dist
                    val overlap = (minDist - dist) / 2f
                    val ax = a.x - nx * overlap
                    val ay = a.y - ny * overlap
                    val bx = b.x + nx * overlap
                    val by = b.y + ny * overlap
                    // Equal-mass impulse along the contact normal.
                    val rvx = b.vx - a.vx
                    val rvy = b.vy - a.vy
                    val relN = rvx * nx + rvy * ny
                    var avx = a.vx; var avy = a.vy
                    var bvx = b.vx; var bvy = b.vy
                    if (relN < 0f) {
                        val jImp = -(1f + restitution) * relN / 2f
                        avx -= jImp * nx; avy -= jImp * ny
                        bvx += jImp * nx; bvy += jImp * ny
                    }
                    orbs[i] = a.copy(x = ax, y = ay, vx = avx, vy = avy)
                    orbs[j] = b.copy(x = bx, y = by, vx = bvx, vy = bvy)
                } else if (dist <= 0.0001f) {
                    // Perfectly coincident — nudge apart deterministically.
                    orbs[j] = b.copy(x = b.x + ra * 0.5f)
                }
            }
        }
    }
}
