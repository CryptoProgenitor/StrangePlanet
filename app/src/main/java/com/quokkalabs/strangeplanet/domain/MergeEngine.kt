package com.quokkalabs.strangeplanet.domain

import com.quokkalabs.strangeplanet.data.model.MergePhase
import com.quokkalabs.strangeplanet.data.model.MergeState
import com.quokkalabs.strangeplanet.data.model.MergeTier
import com.quokkalabs.strangeplanet.data.model.Orb
import com.quokkalabs.strangeplanet.data.model.CONSUME_MAX
import com.quokkalabs.strangeplanet.data.model.ConsumingOrb
import com.quokkalabs.strangeplanet.data.model.POP_MAX
import com.quokkalabs.strangeplanet.data.model.Pop
import com.quokkalabs.strangeplanet.data.model.VOID_POP_MAX
import com.quokkalabs.strangeplanet.data.model.nextOrbId
import kotlin.math.hypot
import kotlin.math.sqrt
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
    private val angularDrag = 0.992f               // spin bleeds off slowly
    private val maxOmega = 26f                     // rad/s spin clamp
    private val floorMu = 0.7f                     // vessel surface roughness

    private var cooldown = 0
    private var overflowTicks = 0
    // Frames to suppress the next merge pass so cascades step visibly.
    private var mergeCooldown = 0
    // Frames of global slow-motion while a void consumes (cinematic).
    private var slowmoTicks = 0

    fun radiusOf(tier: MergeTier): Float = tier.radiusFrac * vesselWidth

    // Relative mass ∝ area; polar moment of a disc I = ½·m·r².
    private fun massOf(tier: MergeTier): Float =
        tier.radiusFrac * tier.radiusFrac * 100f

    private fun inertiaOf(tier: MergeTier): Float {
        val r = radiusOf(tier)
        return 0.5f * massOf(tier) * r * r
    }

    fun createInitialState(highScore: Int): MergeState {
        cooldown = 0
        overflowTicks = 0
        mergeCooldown = 0
        slowmoTicks = 0
        return MergeState(
            orbs = emptyList(),
            currentTier = randomDrop(),
            nextTier = randomDrop(),
            upcoming = listOf(randomDrop(), randomDrop()),
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
        mergeCooldown = 0
        slowmoTicks = 0
        return s.copy(
            phase = MergePhase.PLAYING,
            orbs = emptyList(),
            pops = emptyList(),
            consuming = emptyList(),
            score = 0,
            currentTier = randomDrop(),
            nextTier = randomDrop(),
            upcoming = listOf(randomDrop(), randomDrop()),
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

        // Age existing merge bursts, dropping the expired ones. The void
        // burst (big) lives much longer so its collapse is observable.
        val pops = ArrayList<Pop>(s.pops.size + 4)
        for (p in s.pops) {
            val cap = if (p.big) VOID_POP_MAX else POP_MAX
            if (p.age + 1 < cap) pops.add(p.copy(age = p.age + 1))
        }
        // Age orbs spiralling into a forming void.
        val consuming = ArrayList<ConsumingOrb>(s.consuming.size)
        for (c in s.consuming) if (c.age + 1 < CONSUME_MAX) consuming.add(c.copy(age = c.age + 1))
        if (slowmoTicks > 0) slowmoTicks--
        var currentTier = s.currentTier
        var nextTier = s.nextTier
        var upcoming = s.upcoming.ifEmpty { listOf(randomDrop(), randomDrop()) }

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
            nextTier = upcoming.getOrNull(0) ?: randomDrop()
            upcoming = (if (upcoming.size > 1) upcoming.subList(1, upcoming.size) else emptyList()) + randomDrop()
            cooldown = 26
        }
        if (cooldown > 0) cooldown--

        // ── Integrate (2 substeps) ─────────────────────────────────────────
        // Slow-mo scales the timestep so the whole board drifts cinematically
        // while a void is collapsing; collisions/merges still run each frame.
        // The simulation runs on reusable float buffers (loaded once, written back
        // once) instead of copying every Orb on every substep and collision pass —
        // that was ~16 copies per orb per tick, the main source of GC churn.
        val timeScale = if (slowmoTicks > 0) 0.4f else 1f
        val dt = (1f / 60f) / 2f * timeScale
        val n = orbs.size
        loadBodies(orbs)
        repeat(2) {
            for (i in 0 until n) {
                var vx = bVx[i]
                var vy = bVy[i] + gravity * dt
                vx *= airDrag
                vy *= airDrag
                val sp = hypot(vx, vy)
                if (sp > maxSpeed) {
                    val k = maxSpeed / sp
                    vx *= k; vy *= k
                }
                val omega = (bOmega[i] * angularDrag).coerceIn(-maxOmega, maxOmega)
                bX[i] = bX[i] + vx * dt
                bY[i] = bY[i] + vy * dt
                bVx[i] = vx
                bVy[i] = vy
                bAngle[i] = bAngle[i] + omega * dt
                bOmega[i] = omega
            }
            resolveWalls(n)
            repeat(3) { resolveCollisions(n) }
        }
        for (i in 0 until n) {
            orbs[i] = orbs[i].copy(
                x = bX[i],
                y = bY[i],
                vx = bVx[i],
                vy = bVy[i],
                angle = bAngle[i],
                omega = bOmega[i],
            )
        }

        // ── Merge pass ──────────────────────────────────────────────────────
        // A short cooldown after each merge lets a cascade resolve one step
        // at a time so each coalescence is individually visible.
        if (mergeCooldown > 0) {
            mergeCooldown--
        } else {
        val merged = mergedFlags(orbs.size)
        val survivors = ArrayList<Orb>(orbs.size)
        val spawned = ArrayList<Orb>()
        var mergedAny = false
        for (i in orbs.indices) {
            val a = orbs[i]
            if (merged[i]) continue
            var didMerge = false
            for (j in i + 1 until orbs.size) {
                val b = orbs[j]
                if (b.tier != a.tier || merged[j]) continue
                val ra = radiusOf(a.tier)
                val rb = radiusOf(b.tier)
                val d = hypot(b.x - a.x, b.y - a.y)
                // The collision solver settles same-tier orbs near (ra + rb),
                // but neighbours in a crowded cluster can wedge a pair a few
                // pixels farther apart. A purely relative tolerance gives tiny
                // orbs almost no slack (6% of a ~60px sum ≈ 3px), so they
                // collide visibly yet never merge. Add a size-independent
                // pixel floor so small orbs get the same effective slack.
                val mergeGap = (ra + rb) * 1.06f + vesselWidth * 0.012f
                if (d < mergeGap) {
                    merged[i] = true
                    merged[j] = true
                    val mx = (a.x + b.x) / 2f
                    val my = (a.y + b.y) / 2f
                    val formed = a.tier.next
                    if (formed == MergeTier.BLACK_HOLE) {
                        // Void Consumption — the inevitable void. The pair and
                        // everything in range spiral inward over CONSUME_MAX
                        // frames while the world drifts in slow-motion.
                        consuming.add(ConsumingOrb(a.tier, a.x, a.y, mx, my))
                        consuming.add(ConsumingOrb(b.tier, b.x, b.y, mx, my))
                        val voidR = radiusOf(MergeTier.BLACK_HOLE) * 2.9f
                        var consumed = 0
                        for (k in orbs.indices) {
                            val c = orbs[k]
                            if (merged[k]) continue
                            if (hypot(c.x - mx, c.y - my) < voidR + radiusOf(c.tier)) {
                                merged[k] = true
                                consuming.add(ConsumingOrb(c.tier, c.x, c.y, mx, my))
                                score += (c.tier.ordinal + 1) * 8
                                consumed++
                            }
                        }
                        score += 250 + consumed * 30
                        mergeName = "THE VOID CONSUMES."
                        voidFlash = true
                        slowmoTicks = VOID_POP_MAX
                        pops.add(
                            Pop(nextOrbId(), mx, my, radiusOf(MergeTier.BLACK_HOLE),
                                MergeTier.BLACK_HOLE, big = true),
                        )
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
                        pops.add(Pop(nextOrbId(), mx, my, radiusOf(formed), formed))
                    }
                    didMerge = true
                    mergedAny = true
                    break
                }
            }
        }
        // Everything not merged or consumed survives, in board order.
        for (i in orbs.indices) if (!merged[i]) survivors.add(orbs[i])
        survivors.addAll(spawned)
        orbs = survivors
        // Pause the next merge step so a cascade is seen, not blurred.
        if (mergedAny) mergeCooldown = 8
        }

        // ── Game over: a slow orb resting above the capacity line ──────────
        val slowAbove = orbs.any { o ->
            (o.y - radiusOf(o.tier)) < vesselTop && hypot(o.vx, o.vy) < screenHeight * 0.18f
        }
        val fastAbove = !slowAbove && orbs.any { o -> (o.y - radiusOf(o.tier)) < vesselTop }
        overflowTicks = when {
            slowAbove -> overflowTicks + 1
            fastAbove -> (overflowTicks + 1).coerceAtMost(109)
            else -> 0
        }
        val phase = if (overflowTicks > 110) MergePhase.GAME_OVER else MergePhase.PLAYING

        s = s.copy(
            orbs = orbs,
            pops = pops,
            consuming = consuming,
            score = score,
            currentTier = currentTier,
            nextTier = nextTier,
            upcoming = upcoming,
            canDrop = cooldown <= 0 && phase == MergePhase.PLAYING,
            lastMergeName = mergeName,
            voidFlash = voidFlash,
            phase = phase,
        )
        return s
    }

    // ── Simulation buffers ──────────────────────────────────────────────────
    // One slot per orb, reused every tick. Per-tier constants are cached per slot
    // (the same values the per-call radiusOf/massOf/inertiaOf would give).
    private var bX = FloatArray(0)
    private var bY = FloatArray(0)
    private var bVx = FloatArray(0)
    private var bVy = FloatArray(0)
    private var bAngle = FloatArray(0)
    private var bOmega = FloatArray(0)
    private var bR = FloatArray(0)
    private var bMass = FloatArray(0)
    private var bInertia = FloatArray(0)
    private var bMu = FloatArray(0)
    private var bMerged = BooleanArray(0)

    private fun loadBodies(orbs: List<Orb>) {
        val n = orbs.size
        if (n > bX.size) {
            val cap = maxOf(n, bX.size * 2, 32)
            bX = FloatArray(cap); bY = FloatArray(cap)
            bVx = FloatArray(cap); bVy = FloatArray(cap)
            bAngle = FloatArray(cap); bOmega = FloatArray(cap)
            bR = FloatArray(cap); bMass = FloatArray(cap)
            bInertia = FloatArray(cap); bMu = FloatArray(cap)
        }
        for (i in 0 until n) {
            val o = orbs[i]
            bX[i] = o.x; bY[i] = o.y
            bVx[i] = o.vx; bVy[i] = o.vy
            bAngle[i] = o.angle; bOmega[i] = o.omega
            bR[i] = radiusOf(o.tier)
            bMass[i] = massOf(o.tier)
            bInertia[i] = inertiaOf(o.tier)
            bMu[i] = o.tier.mu
        }
    }

    /** Cleared merge flags, one per orb index. */
    private fun mergedFlags(n: Int): BooleanArray {
        if (n > bMerged.size) bMerged = BooleanArray(maxOf(n, bMerged.size * 2, 32))
        bMerged.fill(false, 0, n)
        return bMerged
    }

    private fun resolveWalls(n: Int) {
        for (i in 0 until n) {
            val r = bR[i]
            val m = bMass[i]
            val inertia = bInertia[i]
            var x = bX[i]; var y = bY[i]
            var vx = bVx[i]; var vy = bVy[i]; var omega = bOmega[i]
            val hitsSide = x - r < vesselLeft || x + r > vesselRight
            val hitsFloor = y + r > vesselBottom
            if (!hitsSide && !hitsFloor) continue

            // Side walls — bounce + a little wall friction → spin.
            if (hitsSide) {
                x = if (x - r < vesselLeft) vesselLeft + r else vesselRight - r
                val impactN = m * kotlin.math.abs(vx)
                vx = -vx * wallDamp
                // Tangent is vertical; contact at ±r on x.
                val slip = vy - omega * r
                val mu = (floorMu * bMu[i]).coerceAtMost(1.2f)
                val jn = impactN + m * gravity * (1f / 60f) * 0.4f
                val kt = 3f / m
                var jt = -slip / kt
                val maxJt = mu * jn
                jt = jt.coerceIn(-maxJt, maxJt)
                vy += jt / m
                omega += -(r * jt) / inertia
            }

            // Floor — bounce + Coulomb friction that converts slip to roll.
            if (hitsFloor) {
                val impactN = m * kotlin.math.abs(vy)
                y = vesselBottom - r
                vy = -vy * wallDamp
                // Contact tangential slip at the bottom point.
                val slip = vx - omega * r
                val mu = (floorMu * bMu[i]).coerceAtMost(1.2f)
                val jn = impactN + m * gravity * (1f / 60f)
                val kt = 3f / m                    // 1/m + r²/I  (I = ½mr²)
                var jt = -slip / kt
                val maxJt = mu * jn
                jt = jt.coerceIn(-maxJt, maxJt)
                vx += jt / m
                omega += -(r * jt) / inertia
            }

            bX[i] = x; bY[i] = y
            bVx[i] = vx; bVy[i] = vy; bOmega[i] = omega
        }
    }

    private fun resolveCollisions(n: Int) {
        for (i in 0 until n) {
            for (j in i + 1 until n) {
                val ra = bR[i]
                val rb = bR[j]
                val dx = bX[j] - bX[i]
                val dy = bY[j] - bY[i]
                val minDist = ra + rb
                // Most pairs are far apart: reject them before paying for the sqrt.
                val distSq = dx * dx + dy * dy
                if (distSq >= minDist * minDist) continue
                val dist = sqrt(distSq)
                if (dist > 0.0001f) {
                    val nx = dx / dist
                    val ny = dy / dist
                    val overlap = (minDist - dist) / 2f
                    val ax = bX[i] - nx * overlap
                    val ay = bY[i] - ny * overlap
                    val bx = bX[j] + nx * overlap
                    val by = bY[j] + ny * overlap
                    // Equal-mass impulse along the contact normal (keeps the
                    // tuned stacking/bounce feel unchanged).
                    val rvx = bVx[j] - bVx[i]
                    val rvy = bVy[j] - bVy[i]
                    val relN = rvx * nx + rvy * ny
                    var avx = bVx[i]; var avy = bVy[i]
                    var bvx = bVx[j]; var bvy = bVy[j]
                    var aw = bOmega[i]; var bw = bOmega[j]
                    if (relN < 0f) {
                        val jImp = -(1f + restitution) * relN / 2f
                        avx -= jImp * nx; avy -= jImp * ny
                        bvx += jImp * nx; bvy += jImp * ny

                        // ── Tangential (Coulomb) friction + torque ──────────
                        val ma = bMass[i]
                        val mb = bMass[j]
                        val ia = bInertia[i]
                        val ib = bInertia[j]
                        // Tangent perpendicular to the contact normal.
                        val tx = -ny
                        val ty = nx
                        // Relative tangential surface speed (incl. spin).
                        val vt = (bVx[i] - bVx[j]) * tx + (bVy[i] - bVy[j]) * ty +
                            aw * ra + bw * rb
                        // k_t = 1/ma+1/mb + ra²/Ia + rb²/Ib  (= 3/ma+3/mb).
                        val kt = 3f / ma + 3f / mb
                        // Physically-correct normal impulse for the cap.
                        val mEff = (ma * mb) / (ma + mb)
                        val jnMag = mEff * (1f + restitution) * (-relN)
                        val muPair = (bMu[i] + bMu[j]).coerceAtMost(1.4f)
                        var jt = -vt / kt
                        val maxJt = muPair * jnMag
                        jt = jt.coerceIn(-maxJt, maxJt)
                        avx += jt / ma * tx; avy += jt / ma * ty
                        bvx -= jt / mb * tx; bvy -= jt / mb * ty
                        aw += ra * jt / ia
                        bw += rb * jt / ib
                    }
                    bX[i] = ax; bY[i] = ay; bVx[i] = avx; bVy[i] = avy; bOmega[i] = aw
                    bX[j] = bx; bY[j] = by; bVx[j] = bvx; bVy[j] = bvy; bOmega[j] = bw
                } else {
                    // Perfectly coincident — nudge apart deterministically.
                    bX[j] = bX[j] + ra * 0.5f
                }
            }
        }
    }
}
