package com.quokkalabs.strangeplanet.domain

import com.quokkalabs.strangeplanet.data.model.BallTrailPoint
import com.quokkalabs.strangeplanet.data.model.DifficultyLevel
import com.quokkalabs.strangeplanet.data.model.GameMode
import com.quokkalabs.strangeplanet.data.model.GamePhase
import com.quokkalabs.strangeplanet.data.model.GameSide
import com.quokkalabs.strangeplanet.data.model.PongGameState
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

class PongEngine(
    private val screenWidth: Float,
    private val screenHeight: Float,
    private val difficulty: DifficultyLevel = DifficultyLevel.STANDARD,
) {
    companion object {
        private const val TRAIL_LENGTH = 10
        private const val POINT_PAUSE_FRAMES = 90
        private const val WIN_SCORE = 7
        private const val MAX_DEFLECTION_DEG = 55f
    }

    val spinFactor = 0.35f          // paddle-velocity contribution to ball vx on hit
    private val maxSpinRatio = 0.92f // |vx| capped at speed × this (≈ 67° effective angle)

    val playerPaddleY = screenHeight * 0.85f
    val aiPaddleY = screenHeight * 0.15f
    val paddleHeight = screenHeight * 0.012f
    val ballRadius = screenWidth * 0.025f

    val paddleWidth = screenWidth * when (difficulty) {
        DifficultyLevel.GENTLE -> 0.30f
        DifficultyLevel.STANDARD -> 0.25f
        DifficultyLevel.AGGRESSIVE -> 0.18f
    }

    val ballBaseSpeed = screenHeight * when (difficulty) {
        DifficultyLevel.GENTLE -> 0.007f
        DifficultyLevel.STANDARD -> 0.009f
        DifficultyLevel.AGGRESSIVE -> 0.012f
    }

    val ballMaxSpeed = screenHeight * when (difficulty) {
        DifficultyLevel.GENTLE -> 0.013f
        DifficultyLevel.STANDARD -> 0.018f
        DifficultyLevel.AGGRESSIVE -> 0.024f
    }

    val speedRampPerHit = screenHeight * when (difficulty) {
        DifficultyLevel.GENTLE -> 0.0002f
        DifficultyLevel.STANDARD -> 0.0004f
        DifficultyLevel.AGGRESSIVE -> 0.0007f
    }

    private val aiTrackingFactor = when (difficulty) {
        DifficultyLevel.GENTLE -> 0.030f
        DifficultyLevel.STANDARD -> 0.045f
        DifficultyLevel.AGGRESSIVE -> 0.070f
    }

    val maxDeflection = MAX_DEFLECTION_DEG * PI.toFloat() / 180f

    private val playerScoreSayings = listOf(
        "I have deflected the sphere past your implement!",
        "The celestial object favours my technique!",
        "Your implement was inadequately positioned!",
        "I am experiencing deflection supremacy!",
    )

    private val aiScoreSayings = listOf(
        "Your deflection technique requires calibration.",
        "The sphere has eluded your implement.",
        "I have calculated a superior trajectory.",
        "Your reflexes appear suboptimal.",
    )

    private val longRallySayings = listOf(
        "This sphere refuses to cease its journey!",
        "The deflection exchange has become prolonged!",
        "Neither implement yields! Fascinating!",
    )

    // 2-player sayings (neutral, no "I" perspective)
    private val twoPlayerBottomScoreSayings = listOf(
        "The lower being has executed a superior trajectory!",
        "The ground-adjacent being scores!",
        "The upper being's implement was inadequately positioned!",
        "The lower being demonstrates deflection prowess!",
    )

    private val twoPlayerTopScoreSayings = listOf(
        "The upper being has calculated correctly!",
        "The sky-adjacent being's technique prevails!",
        "The lower being failed to intercept!",
        "The upper being achieves sphere superiority!",
    )

    fun createInitialState(gameMode: GameMode = GameMode.SINGLE_PLAYER): PongGameState = PongGameState(
        ballX = screenWidth / 2f,
        ballY = screenHeight / 2f,
        playerPaddleX = screenWidth / 2f,
        aiPaddleX = screenWidth / 2f,
        playerPaddleY = playerPaddleY,
        aiPaddleY = aiPaddleY,
        paddleWidth = paddleWidth,
        paddleHeight = paddleHeight,
        ballRadius = ballRadius,
        screenWidth = screenWidth,
        screenHeight = screenHeight,
        gameMode = gameMode,
    )

    fun update(
        state: PongGameState,
        playerTouchX: Float?,
        player2TouchX: Float? = null,
        remotePaddleLagFrames: Int = 0,
    ): PongGameState {
        return when (state.phase) {
            GamePhase.READY -> state
            GamePhase.SERVING -> serve(state)
            GamePhase.PLAYING -> updatePlaying(state, playerTouchX, player2TouchX, remotePaddleLagFrames)
            GamePhase.POINT_SCORED -> updatePointPause(state)
            GamePhase.GAME_OVER -> state
            GamePhase.PAUSED -> state
        }
    }

    fun startServe(state: PongGameState): PongGameState =
        state.copy(phase = GamePhase.SERVING)

    fun reset(gameMode: GameMode = GameMode.SINGLE_PLAYER): PongGameState =
        createInitialState(gameMode)

    private fun serve(state: PongGameState): PongGameState {
        val fromBottom = state.lastScorer != GameSide.PLAYER
        val by = if (fromBottom) playerPaddleY - 60f else aiPaddleY + 60f
        val angle = (Math.random().toFloat() - 0.5f) * 0.6f
        val speed = ballBaseSpeed
        val vy = if (fromBottom) -speed * cos(angle) else speed * cos(angle)
        val vx = speed * sin(angle)

        return state.copy(
            ballX = screenWidth / 2f,
            ballY = by,
            ballVx = vx,
            ballVy = vy,
            phase = GamePhase.PLAYING,
            rally = 0,
            trail = emptyList(),
            activeSaying = null,
        )
    }

    private fun updatePlaying(
        state: PongGameState,
        playerTouchX: Float?,
        player2TouchX: Float? = null,
        remotePaddleLagFrames: Int = 0,
    ): PongGameState {
        val halfPaddle = paddleWidth / 2f

        // Player paddle follows touch
        val newPlayerX = if (playerTouchX != null) {
            playerTouchX.coerceIn(halfPaddle, screenWidth - halfPaddle)
        } else {
            state.playerPaddleX
        }
        val playerPaddleVx = newPlayerX - state.playerPaddleX

        // Top paddle: AI in single player, player 2 touch in multiplayer
        val newAiX = if (state.gameMode != GameMode.SINGLE_PLAYER) {
            if (player2TouchX != null) {
                player2TouchX.coerceIn(halfPaddle, screenWidth - halfPaddle)
            } else {
                state.aiPaddleX
            }
        } else {
            // AI tracks ball with lag
            val aiTarget = state.ballX + (Math.random().toFloat() - 0.5f) * paddleWidth * 0.15f
            (state.aiPaddleX + (aiTarget - state.aiPaddleX) * aiTrackingFactor)
                .coerceIn(halfPaddle, screenWidth - halfPaddle)
        }
        val aiPaddleVx = newAiX - state.aiPaddleX

        // Move ball
        var bx = state.ballX + state.ballVx
        var by = state.ballY + state.ballVy
        var vx = state.ballVx
        var vy = state.ballVy
        var rally = state.rally
        var playerPulse = (state.playerHitPulse - 0.05f).coerceAtLeast(0f)
        var aiPulse = (state.aiHitPulse - 0.05f).coerceAtLeast(0f)
        var saying = state.activeSaying
        var wallBounced = false

        // Wall bounce
        if (bx - ballRadius < 0f) {
            bx = ballRadius
            vx = abs(vx)
            wallBounced = true
        } else if (bx + ballRadius > screenWidth) {
            bx = screenWidth - ballRadius
            vx = -abs(vx)
            wallBounced = true
        }

        // Player paddle collision
        if (vy > 0 && by + ballRadius >= playerPaddleY - paddleHeight &&
            state.ballY + ballRadius < playerPaddleY - paddleHeight
        ) {
            if (bx >= newPlayerX - halfPaddle - ballRadius &&
                bx <= newPlayerX + halfPaddle + ballRadius
            ) {
                val hitPos = ((bx - newPlayerX) / halfPaddle).coerceIn(-1f, 1f)
                val angle = hitPos * maxDeflection
                val speed = (ballBaseSpeed + rally * speedRampPerHit).coerceAtMost(ballMaxSpeed)
                vx = (speed * sin(angle) + playerPaddleVx * spinFactor)
                    .coerceIn(-speed * maxSpinRatio, speed * maxSpinRatio)
                vy = -speed * cos(angle)
                by = playerPaddleY - paddleHeight - ballRadius
                rally++
                playerPulse = 1f

                if (rally == 10) {
                    saying = GameSide.PLAYER to longRallySayings.random()
                }
            }
        }

        // AI paddle collision (with lag compensation for online multiplayer)
        val lagBuffer = if (remotePaddleLagFrames > 0) {
            remotePaddleLagFrames * abs(state.ballVy).coerceAtLeast(ballBaseSpeed * 0.3f)
        } else {
            0f
        }
        if (vy < 0 && by - ballRadius <= aiPaddleY + paddleHeight &&
            state.ballY - ballRadius > aiPaddleY + paddleHeight - lagBuffer
        ) {
            if (bx >= newAiX - halfPaddle - ballRadius &&
                bx <= newAiX + halfPaddle + ballRadius
            ) {
                val hitPos = ((bx - newAiX) / halfPaddle).coerceIn(-1f, 1f)
                val angle = hitPos * maxDeflection
                val speed = (ballBaseSpeed + rally * speedRampPerHit).coerceAtMost(ballMaxSpeed)
                vx = (speed * sin(angle) + aiPaddleVx * spinFactor)
                    .coerceIn(-speed * maxSpinRatio, speed * maxSpinRatio)
                vy = speed * cos(angle)
                by = aiPaddleY + paddleHeight + ballRadius
                rally++
                aiPulse = 1f
            }
        }

        // Scoring
        if (by < -ballRadius * 2) {
            return scorePoint(state.copy(playerPaddleX = newPlayerX, aiPaddleX = newAiX), GameSide.PLAYER)
        }
        if (by > screenHeight + ballRadius * 2) {
            return scorePoint(state.copy(playerPaddleX = newPlayerX, aiPaddleX = newAiX), GameSide.AI)
        }

        // Trail
        val trail = (listOf(BallTrailPoint(bx, by)) + state.trail).take(TRAIL_LENGTH)

        return state.copy(
            ballX = bx,
            ballY = by,
            ballVx = vx,
            ballVy = vy,
            playerPaddleX = newPlayerX,
            aiPaddleX = newAiX,
            rally = rally,
            playerHitPulse = playerPulse,
            aiHitPulse = aiPulse,
            trail = trail,
            activeSaying = saying,
            wallBounced = wallBounced,
        )
    }

    private fun scorePoint(state: PongGameState, scorer: GameSide): PongGameState {
        val pScore = if (scorer == GameSide.PLAYER) state.playerScore + 1 else state.playerScore
        val aScore = if (scorer == GameSide.AI) state.aiScore + 1 else state.aiScore

        val saying = if (state.gameMode != GameMode.SINGLE_PLAYER) {
            if (scorer == GameSide.PLAYER) {
                GameSide.PLAYER to twoPlayerBottomScoreSayings.random()
            } else {
                GameSide.AI to twoPlayerTopScoreSayings.random()
            }
        } else {
            if (scorer == GameSide.PLAYER) {
                GameSide.PLAYER to playerScoreSayings.random()
            } else {
                GameSide.AI to aiScoreSayings.random()
            }
        }

        val phase = if (pScore >= WIN_SCORE || aScore >= WIN_SCORE) {
            GamePhase.GAME_OVER
        } else {
            GamePhase.POINT_SCORED
        }

        return state.copy(
            playerScore = pScore,
            aiScore = aScore,
            phase = phase,
            lastScorer = scorer,
            pointPauseTimer = POINT_PAUSE_FRAMES,
            trail = emptyList(),
            activeSaying = saying,
            ballVx = 0f,
            ballVy = 0f,
        )
    }

    private fun updatePointPause(state: PongGameState): PongGameState {
        val remaining = state.pointPauseTimer - 1
        return if (remaining <= 0) serve(state) else state.copy(pointPauseTimer = remaining)
    }
}
