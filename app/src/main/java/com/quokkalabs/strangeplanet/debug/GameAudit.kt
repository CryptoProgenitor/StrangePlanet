package com.quokkalabs.strangeplanet.debug

import android.util.Log
import com.quokkalabs.strangeplanet.BuildConfig
import com.quokkalabs.strangeplanet.data.model.AsteroidGameState
import com.quokkalabs.strangeplanet.data.model.AsteroidInput
import com.quokkalabs.strangeplanet.data.model.AsteroidPhase
import com.quokkalabs.strangeplanet.data.model.MergePhase
import com.quokkalabs.strangeplanet.data.model.MergeState
import com.quokkalabs.strangeplanet.data.model.PacDir
import com.quokkalabs.strangeplanet.data.model.PacGameState
import com.quokkalabs.strangeplanet.data.model.PacMode
import com.quokkalabs.strangeplanet.data.model.PacPhase
import com.quokkalabs.strangeplanet.data.model.PongGameState
import com.quokkalabs.strangeplanet.data.model.GamePhase
import com.quokkalabs.strangeplanet.data.model.SIPhase
import com.quokkalabs.strangeplanet.data.model.SpaceInvadersState
import com.quokkalabs.strangeplanet.data.model.StrangeMatchPhase
import com.quokkalabs.strangeplanet.data.model.StrangeMatchState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import kotlin.random.Random

/**
 * Built-in gameplay error-detection harness (NOT a fun/balance tester).
 *
 * Two independent capabilities, both gated to debug builds:
 *
 *  - MONITOR: a passive watchdog. Every game's `StateFlow` is polled and a set
 *    of hard invariants is asserted (finite positions, monotonic score, bounded
 *    counts, no permanently-stuck phase). Violations are logged once (then
 *    every 200th) under tag `GameAudit` with a structured, greppable line.
 *    Zero gameplay impact — safe to leave on.
 *
 *  - AUTOPLAY: a dumb deterministic bot drives each game through its existing
 *    public input methods so the state machine is exercised unattended. The
 *    bot is intentionally NOT skilled — its only job is to keep the game
 *    progressing (start, play, die, restart, level-up) so the monitor sees
 *    real transitions. It does not measure fun.
 *
 * On the `claude/game-test-harness` branch both default to BuildConfig.DEBUG,
 * so a debug build is a self-driving error-detection rig: install, open each
 * game once, then `adb logcat -s GameAudit`.  Flip [AUTOPLAY] to false to play
 * manually while keeping the monitor.
 */
object GameAudit {

    private const val TAG = "GameAudit"

    @JvmField var MONITOR: Boolean = BuildConfig.DEBUG
    @JvmField var AUTOPLAY: Boolean = BuildConfig.DEBUG

    /** Poll period for the monitor/bot loop. */
    private const val POLL_MS = 100L

    /** A phase-signature unchanged for this long while "active" = a stall. */
    private const val STALL_MS = 6_000L
    private val STALL_POLLS = (STALL_MS / POLL_MS).toInt()

    private val violationCounts = ConcurrentHashMap<String, Int>()
    private val lastScore = ConcurrentHashMap<String, Int>()
    private val lastSig = ConcurrentHashMap<String, String>()
    private val sigStablePolls = ConcurrentHashMap<String, Int>()
    private val attached = ConcurrentHashMap<String, Boolean>()

    private fun fail(game: String, key: String, detail: String) {
        val k = "$game/$key"
        val n = (violationCounts[k] ?: 0) + 1
        violationCounts[k] = n
        if (n == 1 || n % 200 == 0) {
            Log.w(TAG, "VIOLATION game=$game check=$key n=$n $detail")
        }
    }

    private fun info(msg: String) = Log.i(TAG, msg)

    private fun finite(game: String, key: String, vararg v: Float): Boolean {
        for (f in v) {
            if (f.isNaN() || f.isInfinite()) {
                fail(game, key, "non-finite value=$f")
                return false
            }
        }
        return true
    }

    /** Flag a non-restart score regression. Callers pass true for [freshRun]
     *  on the polls where a new game legitimately reset the score to 0. */
    private fun monotonicScore(game: String, score: Int, freshRun: Boolean) {
        val prev = lastScore[game]
        if (!freshRun && prev != null && score < prev) {
            fail(game, "score_regression", "prev=$prev now=$score")
        }
        lastScore[game] = score
    }

    /**
     * Stall watchdog. [sig] must encode every field that changes each frame
     * during normal play (phase + score + a moving coordinate). If it is
     * identical for STALL_POLLS while [active] is true, the game is frozen.
     */
    private fun stallCheck(game: String, sig: String, active: Boolean) {
        if (!active) {
            lastSig[game] = sig
            sigStablePolls[game] = 0
            return
        }
        if (sig == lastSig[game]) {
            val c = (sigStablePolls[game] ?: 0) + 1
            sigStablePolls[game] = c
            if (c == STALL_POLLS) {
                fail(game, "stall", "frozen ${STALL_MS}ms in active phase sig=[$sig]")
            }
        } else {
            lastSig[game] = sig
            sigStablePolls[game] = 0
        }
    }

    private fun heartbeatDue(game: String, poll: Int): Boolean = poll % 100 == 0

    private fun once(game: String): Boolean = attached.putIfAbsent(game, true) == null

    // ── Merge / Spherical Agglomeration ────────────────────────────────────

    fun attachMerge(
        scope: CoroutineScope,
        state: StateFlow<MergeState>,
        onAim: (Float) -> Unit,
        onRelease: () -> Unit,
    ) {
        if (!MONITOR && !AUTOPLAY) return
        if (!once("merge")) return
        scope.launch {
            var poll = 0
            var dropPhase = 0
            while (isActive) {
                delay(POLL_MS)
                val s = state.value
                poll++
                if (MONITOR) {
                    val playing = s.phase == MergePhase.PLAYING
                    monotonicScore("merge", s.score, s.phase != MergePhase.PLAYING && s.score == 0)
                    if (s.orbs.size > 220) fail("merge", "orb_count", "orbs=${s.orbs.size}")
                    for (o in s.orbs) {
                        if (!finite("merge", "orb_pos", o.x, o.y, o.vx, o.vy)) break
                    }
                    stallCheck(
                        "merge",
                        "${s.phase}|${s.score}|${s.orbs.size}|${s.orbs.firstOrNull()?.y?.toInt()}",
                        playing,
                    )
                    if (heartbeatDue("merge", poll)) {
                        info("merge hb phase=${s.phase} score=${s.score} orbs=${s.orbs.size} hi=${s.highScore}")
                    }
                }
                if (AUTOPLAY) {
                    when (s.phase) {
                        MergePhase.READY, MergePhase.GAME_OVER -> onRelease()
                        MergePhase.PLAYING -> {
                            dropPhase++
                            if (dropPhase % 8 == 0) {
                                val lo = if (s.vesselRight > s.vesselLeft) s.vesselLeft else s.screenWidth * 0.2f
                                val hi = if (s.vesselRight > s.vesselLeft) s.vesselRight else s.screenWidth * 0.8f
                                onAim(lo + Random.nextFloat() * (hi - lo).coerceAtLeast(1f))
                            }
                            if (dropPhase % 8 == 1) onRelease()
                        }
                    }
                }
            }
        }
    }

    // ── Asteroid ───────────────────────────────────────────────────────────

    fun attachAsteroid(
        scope: CoroutineScope,
        state: StateFlow<AsteroidGameState>,
        setInput: ((AsteroidInput) -> AsteroidInput) -> Unit,
        onTap: () -> Unit,
    ) {
        if (!MONITOR && !AUTOPLAY) return
        if (!once("asteroid")) return
        scope.launch {
            var poll = 0
            while (isActive) {
                delay(POLL_MS)
                val s = state.value
                poll++
                if (MONITOR) {
                    val playing = s.phase == AsteroidPhase.PLAYING
                    monotonicScore("asteroid", s.score, s.phase == AsteroidPhase.READY && s.score == 0)
                    if (s.lives < -2 || s.lives > 99) fail("asteroid", "lives_range", "lives=${s.lives}")
                    s.ship?.let { finite("asteroid", "ship_pos", it.x, it.y, it.vx, it.vy) }
                    if (s.rocks.size > 200) fail("asteroid", "rock_count", "rocks=${s.rocks.size}")
                    val transient = s.phase == AsteroidPhase.DYING || s.phase == AsteroidPhase.LEVEL_CLEARED
                    stallCheck(
                        "asteroid",
                        "${s.phase}|${s.score}|${s.ship?.x?.toInt()}|${s.rocks.size}",
                        playing || transient,
                    )
                    if (heartbeatDue("asteroid", poll)) {
                        info("asteroid hb phase=${s.phase} score=${s.score} lives=${s.lives} lvl=${s.level} rocks=${s.rocks.size}")
                    }
                }
                if (AUTOPLAY) {
                    when (s.phase) {
                        AsteroidPhase.READY, AsteroidPhase.GAME_OVER, AsteroidPhase.PAUSED -> onTap()
                        AsteroidPhase.PLAYING ->
                            setInput {
                                it.copy(
                                    thrust = poll % 6 < 2,
                                    rotRight = poll % 4 < 2,
                                    rotLeft = false,
                                    fire = true,
                                    hyperspace = false,
                                )
                            }
                        else -> setInput { AsteroidInput() }
                    }
                }
            }
        }
    }

    // ── Space Invaders ─────────────────────────────────────────────────────

    fun attachSpaceInvaders(
        scope: CoroutineScope,
        state: StateFlow<SpaceInvadersState>,
        onTouch: (Float) -> Unit,
        onTap: () -> Unit,
    ) {
        if (!MONITOR && !AUTOPLAY) return
        if (!once("si")) return
        scope.launch {
            var poll = 0
            while (isActive) {
                delay(POLL_MS)
                val s = state.value
                poll++
                if (MONITOR) {
                    val playing = s.phase == SIPhase.PLAYING
                    monotonicScore("si", s.score, s.phase == SIPhase.READY && s.score == 0)
                    if (s.lives < -1 || s.lives > 99) fail("si", "lives_range", "lives=${s.lives}")
                    if (s.screenWidth > 0f && (s.playerX < -1f || s.playerX > s.screenWidth + 1f)) {
                        fail("si", "player_oob", "x=${s.playerX} w=${s.screenWidth}")
                    }
                    val transient = s.phase == SIPhase.WAVE_CLEAR || s.phase == SIPhase.PLAYER_HIT
                    stallCheck(
                        "si",
                        "${s.phase}|${s.score}|${s.invaders.size}|${s.enemyProjectiles.size}",
                        playing || transient,
                    )
                    if (heartbeatDue("si", poll)) {
                        info("si hb phase=${s.phase} score=${s.score} lives=${s.lives} wave=${s.wave} inv=${s.invaders.size}")
                    }
                }
                if (AUTOPLAY) {
                    when (s.phase) {
                        SIPhase.READY, SIPhase.GAME_OVER -> onTap()
                        SIPhase.PLAYING -> {
                            val w = s.screenWidth.coerceAtLeast(1f)
                            // Track the lowest live invader's column; fall back to a sweep.
                            val target = s.invaders.minByOrNull { it.y }?.x
                                ?: (w * (0.5f + 0.45f * kotlin.math.sin(poll * 0.15f)))
                            onTouch(target)
                        }
                        else -> {}
                    }
                }
            }
        }
    }

    // ── Pac-Man ────────────────────────────────────────────────────────────

    fun attachPac(
        scope: CoroutineScope,
        state: StateFlow<PacGameState>,
        onSwipe: (PacDir) -> Unit,
        onTap: () -> Unit,
    ) {
        if (!MONITOR && !AUTOPLAY) return
        if (!once("pac")) return
        val dirs = arrayOf(PacDir.RIGHT, PacDir.DOWN, PacDir.LEFT, PacDir.UP)
        scope.launch {
            var poll = 0
            var levelPellets = -1
            var levelKey = -1
            while (isActive) {
                delay(POLL_MS)
                val s = state.value
                poll++
                if (MONITOR) {
                    val playing = s.phase == PacPhase.PLAYING
                    monotonicScore("pac", s.score, s.phase == PacPhase.READY && s.score == 0)
                    if (s.lives < -1 || s.lives > 99) fail("pac", "lives_range", "lives=${s.lives}")
                    if (s.cols > 0 && s.rows > 0) {
                        if (s.being.col !in 0 until s.cols || s.being.row !in 0 until s.rows) {
                            fail("pac", "being_oob", "col=${s.being.col} row=${s.being.row} cols=${s.cols} rows=${s.rows}")
                        }
                    }
                    // Pellets must never increase within a level.
                    if (s.level != levelKey) { levelKey = s.level; levelPellets = s.pellets.size }
                    else if (s.pellets.size > levelPellets) {
                        fail("pac", "pellets_grew", "was=$levelPellets now=${s.pellets.size} lvl=${s.level}")
                    } else levelPellets = s.pellets.size
                    val transient = s.phase == PacPhase.DYING || s.phase == PacPhase.LEVEL_CLEARED
                    stallCheck(
                        "pac",
                        "${s.phase}|${s.score}|${s.being.col},${s.being.row}|${s.pellets.size}",
                        playing || transient,
                    )
                    if (heartbeatDue("pac", poll)) {
                        info("pac hb phase=${s.phase} mode=${s.mode} score=${s.score} lives=${s.lives} pel=${s.pellets.size}/${s.totalPellets}")
                    }
                }
                if (AUTOPLAY && s.mode == PacMode.SOLO) {
                    when (s.phase) {
                        PacPhase.READY, PacPhase.GAME_OVER -> onTap()
                        PacPhase.PLAYING -> if (poll % 7 == 0) onSwipe(dirs[(poll / 7) % 4])
                        else -> {}
                    }
                }
            }
        }
    }

    // ── Pong ───────────────────────────────────────────────────────────────

    fun attachPong(
        scope: CoroutineScope,
        state: StateFlow<PongGameState>,
        enableBot: () -> Unit,
    ) {
        if (!MONITOR && !AUTOPLAY) return
        if (!once("pong")) return
        if (AUTOPLAY) enableBot()
        scope.launch {
            var poll = 0
            while (isActive) {
                delay(POLL_MS)
                val s = state.value
                poll++
                if (!MONITOR) continue
                val r = s.ballRadius
                finite("pong", "ball", s.ballX, s.ballY, s.ballVx, s.ballVy)
                if (s.screenWidth > 0f &&
                    (s.ballX < -4 * r - 1 || s.ballX > s.screenWidth + 4 * r + 1)
                ) {
                    fail("pong", "ball_oob_x", "x=${s.ballX} w=${s.screenWidth} r=$r")
                }
                if (s.screenWidth > 0f) {
                    if (s.playerPaddleX < -1f || s.playerPaddleX > s.screenWidth + 1f) {
                        fail("pong", "paddle_oob", "px=${s.playerPaddleX} w=${s.screenWidth}")
                    }
                }
                if (s.playerScore < 0 || s.aiScore < 0 || s.playerScore > 99 || s.aiScore > 99) {
                    fail("pong", "score_range", "p=${s.playerScore} ai=${s.aiScore}")
                }
                // SERVING is meant to be transient; PLAYING/SERVING are active.
                val active = s.phase == GamePhase.PLAYING || s.phase == GamePhase.SERVING
                stallCheck(
                    "pong",
                    "${s.phase}|${s.playerScore}-${s.aiScore}|${s.ballX.toInt()},${s.ballY.toInt()}|${s.rally}",
                    active,
                )
                if (heartbeatDue("pong", poll)) {
                    info("pong hb phase=${s.phase} mode=${s.gameMode} score=${s.playerScore}-${s.aiScore} rally=${s.rally}")
                }
            }
        }
    }

    // ── Strange Match ──────────────────────────────────────────────────────

    fun attachStrangeMatch(
        scope: CoroutineScope,
        state: StateFlow<StrangeMatchState>,
        onSwipe: (Int, Int, Int, Int) -> Unit,
        onStart: () -> Unit,
    ) {
        if (!MONITOR && !AUTOPLAY) return
        if (!once("match")) return
        scope.launch {
            var poll = 0
            while (isActive) {
                delay(POLL_MS)
                val s = state.value
                poll++
                if (MONITOR) {
                    val playing = s.phase == StrangeMatchPhase.PLAYING
                    monotonicScore("match", s.score, s.phase == StrangeMatchPhase.READY && s.score == 0)
                    if (s.movesLeft < 0) fail("match", "moves_negative", "movesLeft=${s.movesLeft}")
                    if (playing && s.grid.isNotEmpty() && s.grid.any { row -> row.any { it == null } }) {
                        fail("match", "null_tile_in_play", "grid has null while PLAYING")
                    }
                    // ANIMATING is the cascade window; it must not last forever.
                    stallCheck(
                        "match",
                        "${s.phase}|${s.score}|${s.movesLeft}|${s.level}",
                        s.phase == StrangeMatchPhase.ANIMATING,
                    )
                    if (heartbeatDue("match", poll)) {
                        info("match hb phase=${s.phase} score=${s.score} moves=${s.movesLeft} lvl=${s.level}")
                    }
                }
                if (AUTOPLAY) {
                    when (s.phase) {
                        StrangeMatchPhase.READY, StrangeMatchPhase.GAME_OVER -> onStart()
                        StrangeMatchPhase.PLAYING -> if (poll % 6 == 0 && s.grid.isNotEmpty()) {
                            val rows = s.grid.size
                            val cols = s.grid[0].size
                            val r = Random.nextInt(rows)
                            val c = Random.nextInt(cols)
                            // Pick an in-bounds orthogonal neighbour.
                            val (dr, dc) = when (Random.nextInt(4)) {
                                0 -> 1 to 0
                                1 -> -1 to 0
                                2 -> 0 to 1
                                else -> 0 to -1
                            }
                            val nr = r + dr
                            val nc = c + dc
                            if (nr in 0 until rows && nc in 0 until cols) onSwipe(r, c, nr, nc)
                        }
                        else -> {}
                    }
                }
            }
        }
    }
}
