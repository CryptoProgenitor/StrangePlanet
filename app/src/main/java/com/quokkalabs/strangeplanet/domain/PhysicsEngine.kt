package com.quokkalabs.strangeplanet.domain

import com.quokkalabs.strangeplanet.data.model.CreatureState
import com.quokkalabs.strangeplanet.data.model.CreatureType
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

data class OrbitParams(
    val semiMajor: Float,
    val semiMinor: Float,
    val angularSpeed: Float,
    val phase: Float,
    val tilt: Float,
)

class PhysicsEngine(
    private val screenWidth: Float,
    private val screenHeight: Float,
) {
    companion object {
        private const val MIN_SPEED = 0.3f
        private const val CAPTURE_DURATION_MS = 600L
        private const val CHASE_DURATION_MS = 5000L
        private const val BINARY_ORBIT_DURATION_MS = 2000L
    }

    var baseSpeed = 2.5f
    var restitution = 1f
    var orbitDurationMs = 5000L
    var flingSpeedMult = 4f
    var linearDrag = 0f
    var spinDamping = 0f

    private var chaseStartTime = 0L
    private var binaryOrbitStartTime = 0L
    private var binaryCenterX = 0f
    private var binaryCenterY = 0f
    private var binaryStartAngle = 0f
    private var binaryOrbitRadius = 0f

    val isChasing: Boolean
        get() = chaseStartTime > 0L

    val isBinaryOrbiting: Boolean
        get() = binaryOrbitStartTime > 0L

    fun startChase() {
        if (isOrbiting || isBinaryOrbiting) return
        chaseStartTime = System.currentTimeMillis()
    }

    private var orbitStartTime = 0L
    private var orbitParams = emptyMap<Int, OrbitParams>()
    private var preOrbitPositions = emptyMap<Int, Pair<Float, Float>>()

    val isOrbiting: Boolean
        get() = orbitStartTime > 0L

    val orbitProgress: Float
        get() {
            if (orbitStartTime == 0L) return 0f
            val elapsed = System.currentTimeMillis() - orbitStartTime
            return (elapsed.toFloat() / (CAPTURE_DURATION_MS + orbitDurationMs)).coerceIn(0f, 1f)
        }

    val planetCenter: Pair<Float, Float>
        get() = screenWidth / 2f to screenHeight / 2f

    fun isCreatureBehindPlanet(creature: CreatureState): Boolean {
        if (!isOrbiting) return false
        val cx = screenWidth / 2f
        val cy = screenHeight / 2f
        val planetRadius = screenWidth.coerceAtMost(screenHeight) * 0.12f
        val dx = creature.x - cx
        val dy = creature.y - cy
        val dist = sqrt(dx * dx + dy * dy)
        return dist < planetRadius * 2.5f && creature.y < cy
    }

    fun startOrbit(creatures: List<CreatureState>) {
        if (isOrbiting) return
        orbitStartTime = System.currentTimeMillis()

        val cx = screenWidth / 2f
        val cy = screenHeight / 2f
        val planetRadius = screenWidth.coerceAtMost(screenHeight) * 0.12f

        preOrbitPositions = creatures.mapIndexed { i, c ->
            i to (c.x to c.y)
        }.toMap()

        orbitParams = creatures.mapIndexed { i, c ->
            val angleTo = kotlin.math.atan2(c.y - cy, c.x - cx)
            val layer = 1.8f + i * 0.35f
            val semiMajor = planetRadius * layer
            val semiMinor = semiMajor * (0.35f + Math.random().toFloat() * 0.15f)
            val speed = 1.2f + Math.random().toFloat() * 0.8f
            val direction = if (i % 2 == 0) 1f else -1f
            val tilt = (Math.random().toFloat() - 0.5f) * 0.3f

            i to OrbitParams(
                semiMajor = semiMajor,
                semiMinor = semiMinor,
                angularSpeed = speed * direction,
                phase = angleTo,
                tilt = tilt,
            )
        }.toMap()
    }

    fun update(creatures: List<CreatureState>): List<CreatureState> {
        if (isOrbiting) {
            return updateOrbiting(creatures)
        }

        val updated = creatures.map { it.copy() }.toMutableList()

        applyMovement(updated)
        applyChaseForces(updated)
        resolveWallCollisions(updated)
        resolveCreatureCollisions(updated)
        nudgeStoppedCreatures(updated)

        return updated
    }

    private fun orbitPosition(params: OrbitParams, t: Float, cx: Float, cy: Float): Pair<Float, Float> {
        val angle = params.phase + params.angularSpeed * t
        val ox = cos(angle) * params.semiMajor
        val oy = sin(angle) * params.semiMinor
        val cosT = cos(params.tilt)
        val sinT = sin(params.tilt)
        return (cx + ox * cosT - oy * sinT) to (cy + ox * sinT + oy * cosT)
    }

    private fun updateOrbiting(creatures: List<CreatureState>): List<CreatureState> {
        val elapsed = System.currentTimeMillis() - orbitStartTime
        val cx = screenWidth / 2f
        val cy = screenHeight / 2f

        if (elapsed > CAPTURE_DURATION_MS + orbitDurationMs) {
            return flingOut(creatures)
        }

        return creatures.mapIndexed { i, c ->
            val params = orbitParams[i] ?: return@mapIndexed c
            val t = elapsed.toFloat() / 1000f
            val (targetX, targetY) = orbitPosition(params, t, cx, cy)

            if (elapsed < CAPTURE_DURATION_MS) {
                val captureT = elapsed.toFloat() / CAPTURE_DURATION_MS
                val eased = captureT * captureT * (3f - 2f * captureT)
                val startPos = preOrbitPositions[i] ?: (c.x to c.y)

                c.copy(
                    x = startPos.first + (targetX - startPos.first) * eased,
                    y = startPos.second + (targetY - startPos.second) * eased,
                    vx = 0f,
                    vy = 0f,
                    rotation = c.rotation + params.angularSpeed * 2f,
                )
            } else {
                c.copy(
                    x = targetX,
                    y = targetY,
                    vx = 0f,
                    vy = 0f,
                    rotation = c.rotation + params.angularSpeed * 2f,
                )
            }
        }
    }

    private fun flingOut(creatures: List<CreatureState>): List<CreatureState> {
        val cx = screenWidth / 2f
        val cy = screenHeight / 2f

        orbitStartTime = 0L

        return creatures.mapIndexed { i, c ->
            val params = orbitParams[i] ?: return@mapIndexed c

            val dx = c.x - cx
            val dy = c.y - cy
            val dist = sqrt(dx * dx + dy * dy).coerceAtLeast(1f)
            val nx = dx / dist
            val ny = dy / dist

            val tangentX = -ny * abs(params.angularSpeed)
            val tangentY = nx * abs(params.angularSpeed)

            c.copy(
                vx = (nx * flingSpeedMult + tangentX * 2f) * baseSpeed,
                vy = (ny * flingSpeedMult + tangentY * 2f) * baseSpeed,
                angularVelocity = params.angularSpeed * 3f,
            )
        }
    }

    private fun applyChaseForces(creatures: MutableList<CreatureState>) {
        if (isBinaryOrbiting) {
            applyBinaryOrbit(creatures)
            return
        }

        if (!isChasing) return

        val elapsed = System.currentTimeMillis() - chaseStartTime
        if (elapsed > CHASE_DURATION_MS) {
            chaseStartTime = 0L
            return
        }

        val rollIdx = creatures.indexOfFirst { it.type == CreatureType.ROLLSUCK }
        val sockIdx = creatures.indexOfFirst { it.type == CreatureType.SOCKS }
        if (rollIdx < 0 || sockIdx < 0) return

        val rollsuck = creatures[rollIdx]
        val socks = creatures[sockIdx]

        val dx = socks.x - rollsuck.x
        val dy = socks.y - rollsuck.y
        val dist = sqrt(dx * dx + dy * dy).coerceAtLeast(1f)
        val nx = dx / dist
        val ny = dy / dist

        if (dist < (rollsuck.radius + socks.radius) * 1.5f) {
            binaryOrbitStartTime = System.currentTimeMillis()
            binaryCenterX = (rollsuck.x * rollsuck.mass + socks.x * socks.mass) /
                    (rollsuck.mass + socks.mass)
            binaryCenterY = (rollsuck.y * rollsuck.mass + socks.y * socks.mass) /
                    (rollsuck.mass + socks.mass)
            binaryStartAngle = atan2(
                rollsuck.y - binaryCenterY,
                rollsuck.x - binaryCenterX,
            )
            binaryOrbitRadius = (rollsuck.radius + socks.radius) * 1.3f
            return
        }

        val chaseForce = baseSpeed * 1.8f
        val fleeForce = baseSpeed * 2.2f

        creatures[rollIdx] = rollsuck.copy(
            vx = rollsuck.vx * 0.85f + nx * chaseForce * 0.15f,
            vy = rollsuck.vy * 0.85f + ny * chaseForce * 0.15f,
        )

        creatures[sockIdx] = socks.copy(
            vx = socks.vx * 0.85f - nx * fleeForce * 0.15f,
            vy = socks.vy * 0.85f - ny * fleeForce * 0.15f,
            angularVelocity = socks.angularVelocity + (Math.random().toFloat() - 0.5f) * 1.5f,
        )
    }

    private fun applyBinaryOrbit(creatures: MutableList<CreatureState>) {
        val elapsed = System.currentTimeMillis() - binaryOrbitStartTime

        val rollIdx = creatures.indexOfFirst { it.type == CreatureType.ROLLSUCK }
        val sockIdx = creatures.indexOfFirst { it.type == CreatureType.SOCKS }
        if (rollIdx < 0 || sockIdx < 0) return

        if (elapsed > BINARY_ORBIT_DURATION_MS) {
            flingFromBinaryOrbit(creatures, rollIdx, sockIdx)
            return
        }

        val t = elapsed / 1000f
        val angle = binaryStartAngle + 4f * t + 1.5f * t * t
        val shrink = 1f - (elapsed.toFloat() / BINARY_ORBIT_DURATION_MS) * 0.3f
        val r = binaryOrbitRadius * shrink

        val cx = binaryCenterX.coerceIn(r + 10f, screenWidth - r - 10f)
        val cy = binaryCenterY.coerceIn(r + 10f, screenHeight - r - 10f)

        val spinRate = 4f + t * 6f

        creatures[rollIdx] = creatures[rollIdx].copy(
            x = cx + cos(angle) * r,
            y = cy + sin(angle) * r,
            vx = 0f,
            vy = 0f,
            rotation = creatures[rollIdx].rotation + spinRate,
        )

        creatures[sockIdx] = creatures[sockIdx].copy(
            x = cx + cos(angle + PI.toFloat()) * r,
            y = cy + sin(angle + PI.toFloat()) * r,
            vx = 0f,
            vy = 0f,
            rotation = creatures[sockIdx].rotation - spinRate * 1.5f,
        )
    }

    private fun flingFromBinaryOrbit(
        creatures: MutableList<CreatureState>,
        rollIdx: Int,
        sockIdx: Int,
    ) {
        val t = BINARY_ORBIT_DURATION_MS / 1000f
        val finalAngle = binaryStartAngle + 4f * t + 1.5f * t * t
        val speed = baseSpeed * 5f

        val tangentX = -sin(finalAngle)
        val tangentY = cos(finalAngle)

        creatures[rollIdx] = creatures[rollIdx].copy(
            vx = tangentX * speed + (Math.random().toFloat() - 0.5f) * speed * 0.4f,
            vy = tangentY * speed + (Math.random().toFloat() - 0.5f) * speed * 0.4f,
            angularVelocity = (Math.random().toFloat() - 0.5f) * 15f,
        )

        creatures[sockIdx] = creatures[sockIdx].copy(
            vx = -tangentX * speed + (Math.random().toFloat() - 0.5f) * speed * 0.4f,
            vy = -tangentY * speed + (Math.random().toFloat() - 0.5f) * speed * 0.4f,
            angularVelocity = (Math.random().toFloat() - 0.5f) * 15f,
        )

        binaryOrbitStartTime = 0L
        chaseStartTime = 0L
    }

    private fun applyMovement(creatures: MutableList<CreatureState>) {
        creatures.forEachIndexed { i, c ->
            val dragFactor = 1f - linearDrag
            val spinFactor = 1f - spinDamping
            creatures[i] = c.copy(
                x = c.x + c.vx,
                y = c.y + c.vy,
                vx = c.vx * dragFactor,
                vy = c.vy * dragFactor,
                rotation = c.rotation + c.angularVelocity,
                angularVelocity = c.angularVelocity * spinFactor,
            )
        }
    }

    private fun resolveWallCollisions(creatures: MutableList<CreatureState>) {
        creatures.forEachIndexed { i, c ->
            var vx = c.vx
            var vy = c.vy
            var x = c.x
            var y = c.y
            var angVel = c.angularVelocity

            if (x - c.radius < 0) {
                x = c.radius
                vx = abs(c.vx) * restitution
                angVel = -angVel * 0.8f + c.vy * 0.1f
            } else if (x + c.radius > screenWidth) {
                x = screenWidth - c.radius
                vx = -abs(c.vx) * restitution
                angVel = -angVel * 0.8f - c.vy * 0.1f
            }

            if (y - c.radius < 0) {
                y = c.radius
                vy = abs(c.vy) * restitution
                angVel = -angVel * 0.8f - c.vx * 0.1f
            } else if (y + c.radius > screenHeight) {
                y = screenHeight - c.radius
                vy = -abs(c.vy) * restitution
                angVel = -angVel * 0.8f + c.vx * 0.1f
            }

            creatures[i] = c.copy(x = x, y = y, vx = vx, vy = vy, angularVelocity = angVel)
        }
    }

    private fun resolveCreatureCollisions(creatures: MutableList<CreatureState>) {
        for (i in creatures.indices) {
            for (j in i + 1 until creatures.size) {
                val a = creatures[i]
                val b = creatures[j]

                val dx = b.x - a.x
                val dy = b.y - a.y
                val dist = sqrt(dx * dx + dy * dy)
                val minDist = a.radius + b.radius

                if (dist >= minDist || dist < 0.001f) continue

                val nx = dx / dist
                val ny = dy / dist

                val dvx = a.vx - b.vx
                val dvy = a.vy - b.vy
                val dvn = dvx * nx + dvy * ny

                if (dvn > 0) continue

                val impulse = (2f * dvn) / (a.mass + b.mass) * restitution

                val tangentImpulse = dvx * (-ny) + dvy * nx

                val overlap = minDist - dist
                val sepX = overlap * nx * 0.5f
                val sepY = overlap * ny * 0.5f

                creatures[i] = a.copy(
                    x = a.x - sepX,
                    y = a.y - sepY,
                    vx = a.vx - impulse * b.mass * nx,
                    vy = a.vy - impulse * b.mass * ny,
                    angularVelocity = a.angularVelocity +
                            tangentImpulse * (b.mass / (a.mass + b.mass)) * 0.5f,
                )
                creatures[j] = b.copy(
                    x = b.x + sepX,
                    y = b.y + sepY,
                    vx = b.vx + impulse * a.mass * nx,
                    vy = b.vy + impulse * a.mass * ny,
                    angularVelocity = b.angularVelocity -
                            tangentImpulse * (a.mass / (a.mass + b.mass)) * 0.5f,
                )
            }
        }
    }

    private fun nudgeStoppedCreatures(creatures: MutableList<CreatureState>) {
        creatures.forEachIndexed { i, c ->
            val speed = sqrt(c.vx * c.vx + c.vy * c.vy)
            if (speed < MIN_SPEED) {
                val angle = Math.random().toFloat() * 2f * PI.toFloat()
                creatures[i] = c.copy(
                    vx = cos(angle) * baseSpeed,
                    vy = sin(angle) * baseSpeed,
                )
            }
        }
    }
}
