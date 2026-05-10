package com.quokkalabs.strangeplanet.data.model

enum class GamePhase {
    READY, SERVING, PLAYING, POINT_SCORED, GAME_OVER
}

enum class GameSide {
    PLAYER, AI
}

enum class GameMode {
    SINGLE_PLAYER, TWO_PLAYER
}

data class BallTrailPoint(val x: Float, val y: Float)

data class PongGameState(
    val ballX: Float = 0f,
    val ballY: Float = 0f,
    val ballVx: Float = 0f,
    val ballVy: Float = 0f,
    val ballRadius: Float = 0f,
    val playerPaddleX: Float = 0f,
    val playerPaddleY: Float = 0f,
    val aiPaddleX: Float = 0f,
    val aiPaddleY: Float = 0f,
    val paddleWidth: Float = 0f,
    val paddleHeight: Float = 0f,
    val playerScore: Int = 0,
    val aiScore: Int = 0,
    val phase: GamePhase = GamePhase.READY,
    val lastScorer: GameSide? = null,
    val rally: Int = 0,
    val trail: List<BallTrailPoint> = emptyList(),
    val playerHitPulse: Float = 0f,
    val aiHitPulse: Float = 0f,
    val pointPauseTimer: Int = 0,
    val activeSaying: Pair<GameSide, String>? = null,
    val screenWidth: Float = 0f,
    val screenHeight: Float = 0f,
    val gameMode: GameMode = GameMode.SINGLE_PLAYER,
)
