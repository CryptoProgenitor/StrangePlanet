package com.quokkalabs.strangeplanet.data.model

enum class GamePhase {
    READY, SERVING, PLAYING, POINT_SCORED, GAME_OVER
}

enum class GameSide {
    PLAYER, AI
}

enum class GameMode {
    SINGLE_PLAYER, TWO_PLAYER, BLUETOOTH
}

enum class BtConnectionState {
    IDLE, HOSTING, SCANNING, CONNECTING, CONNECTED
}

enum class BtRole {
    HOST, CLIENT
}

data class BtDeviceInfo(val name: String, val address: String)

data class BluetoothLobbyState(
    val available: Boolean = false,
    val enabled: Boolean = false,
    val permissionsGranted: Boolean = false,
    val connectionState: BtConnectionState = BtConnectionState.IDLE,
    val pairedDevices: List<BtDeviceInfo> = emptyList(),
    val discoveredDevices: List<BtDeviceInfo> = emptyList(),
    val connectedDeviceName: String? = null,
    val role: BtRole? = null,
)

enum class DifficultyLevel(val label: String) {
    GENTLE("Gentle Sphere"),
    STANDARD("Standard"),
    AGGRESSIVE("Aggressive Sphere"),
}

data class PongSettings(
    val soundEnabled: Boolean = true,
    val showSayings: Boolean = true,
    val difficulty: DifficultyLevel = DifficultyLevel.STANDARD,
)

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
    val wallBounced: Boolean = false,
)
