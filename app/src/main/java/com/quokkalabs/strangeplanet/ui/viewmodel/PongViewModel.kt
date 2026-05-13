package com.quokkalabs.strangeplanet.ui.viewmodel

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.quokkalabs.strangeplanet.R
import com.quokkalabs.strangeplanet.audio.PongSoundManager
import com.quokkalabs.strangeplanet.bluetooth.BluetoothPongManager
import com.quokkalabs.strangeplanet.data.model.BallTrailPoint
import com.quokkalabs.strangeplanet.data.model.BluetoothLobbyState
import com.quokkalabs.strangeplanet.data.model.BtConnectionState
import com.quokkalabs.strangeplanet.data.model.BtDeviceInfo
import com.quokkalabs.strangeplanet.data.model.BtRole
import com.quokkalabs.strangeplanet.data.model.DifficultyLevel
import com.quokkalabs.strangeplanet.data.model.GameMode
import com.quokkalabs.strangeplanet.data.model.GamePhase
import com.quokkalabs.strangeplanet.data.model.GameSide
import com.quokkalabs.strangeplanet.data.model.OnlineConnectionState
import com.quokkalabs.strangeplanet.data.model.OnlineLobbyState
import com.quokkalabs.strangeplanet.data.model.PongGameState
import com.quokkalabs.strangeplanet.data.model.PongSettings
import com.quokkalabs.strangeplanet.domain.PongEngine
import com.quokkalabs.strangeplanet.firebase.FirebasePongManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.random.Random
import com.quokkalabs.strangeplanet.debug.PongDebugMetrics

class PongViewModel(application: Application) : AndroidViewModel(application) {

    private var engine: PongEngine? = null
    private val _gameState = MutableStateFlow(PongGameState())
    val gameState: StateFlow<PongGameState> = _gameState.asStateFlow()

    private val _pongSettings = MutableStateFlow(PongSettings())
    val pongSettings: StateFlow<PongSettings> = _pongSettings.asStateFlow()

    private val _btState = MutableStateFlow(BluetoothLobbyState())
    val btState: StateFlow<BluetoothLobbyState> = _btState.asStateFlow()

    private val _playerCreature = MutableStateFlow(R.drawable.sp_pong_player)
    val playerCreature: StateFlow<Int> = _playerCreature.asStateFlow()

    private val _opponentCreature = MutableStateFlow(R.drawable.sp_pong_cat)
    val opponentCreature: StateFlow<Int> = _opponentCreature.asStateFlow()

    private val _player2Creature = MutableStateFlow(R.drawable.sp_pong_cat)
    val player2Creature: StateFlow<Int> = _player2Creature.asStateFlow()

    private val _onlineState = MutableStateFlow(OnlineLobbyState())
    val onlineState: StateFlow<OnlineLobbyState> = _onlineState.asStateFlow()

    private val pongSound = PongSoundManager()

    private var playerTouchX: Float? = null
    private var player2TouchX: Float? = null
    private var btManager: BluetoothPongManager? = null
    private var btObserveStarted = false
    private var firebaseManager: FirebasePongManager? = null
    private var onlineObserveStarted = false

    // Client-side trail accumulation
    private val clientTrail = ArrayDeque<BallTrailPoint>()

    // Online dead-reckoning: ball position/velocity advanced each frame between Firebase packets
    private var drBallX = 0f
    private var drBallY = 0f
    private var drVx = 0f
    private var drVy = 0f
    private var drLastRally = -1
    private var clientHitSentThisRally = false
    private var lastProcessedRemoteState: BluetoothPongManager.NetGameState? = null

    // Lerp for opponent (host) paddle – still useful at 30 Hz
    private var smoothAiPaddleX = 0f

    // Bot auto-play: counts down to zero before triggering tap-to-start
    private var botStartCountdown = 0

    // Bot sweep: cycles the paddle contact point across the full paddle width and into miss zones.
    // Offsets are fractions of halfPaddle for hit phases.
    // |offset| >= 1.85f → near-miss sentinel: pad positioned (halfPaddle + ballRadius + 2px) away.
    // |offset| >= 2.00f → full miss: paddle parked at opposite wall.
    // playerTouchX = drBallX - sweepOffset * halfPaddle → hitPos == sweepOffset at contact time.
    private var botSweepPhase = 0
    private var botSweepLastRally = -1
    private val BOT_SWEEP_OFFSETS = floatArrayOf(
        0.00f,   // phase 0: center hit
       -0.75f,   // phase 1: hit 75% toward left edge
        0.75f,   // phase 2: hit 75% toward right edge
       -0.95f,   // phase 3: hit near left edge (95%)
        0.95f,   // phase 4: hit near right edge (95%)
        1.90f,   // phase 5: near-miss LEFT  — paddle 2px beyond right of ball
       -1.90f,   // phase 6: near-miss RIGHT — paddle 2px beyond left of ball
        2.00f,   // phase 7: full miss       — paddle at opposite wall
    )

    fun initGame(screenWidth: Float, screenHeight: Float) {
        if (engine != null) return

        val eng = PongEngine(screenWidth, screenHeight, _pongSettings.value.difficulty)
        engine = eng
        _gameState.value = eng.createInitialState()

        viewModelScope.launch {
            while (isActive) {
                delay(1000)
                if (_gameState.value.gameMode == GameMode.ONLINE) {
                    PongDebugMetrics.tickSecond()
                    if (_pongSettings.value.debugOverlayEnabled) PongDebugMetrics.emitLogcat()
                }
            }
        }

        viewModelScope.launch {
            while (isActive) {
                delay(16)
                val e = engine ?: continue

                // Determine active multiplayer manager
                val mode = _gameState.value.gameMode
                val mpRole: BtRole?
                val mpConnected: Boolean
                val mpRemoteTouchX: Float?
                val mpRemoteState: BluetoothPongManager.NetGameState?
                val mpRemoteControl: Byte?

                when (mode) {
                    GameMode.BLUETOOTH -> {
                        val bt = btManager
                        mpRole = bt?.role
                        mpConnected = bt?.connectionState?.value == BtConnectionState.CONNECTED
                        mpRemoteTouchX = bt?.remoteTouchX?.value
                        mpRemoteState = bt?.remoteGameState?.value
                        mpRemoteControl = bt?.remoteControl?.value
                    }
                    GameMode.ONLINE -> {
                        val fb = firebaseManager
                        mpRole = fb?.role
                        mpConnected = fb?.isConnected == true
                        mpRemoteTouchX = fb?.remoteTouchX?.value
                        mpRemoteState = fb?.remoteGameState?.value
                        mpRemoteControl = fb?.remoteControl?.value
                    }
                    else -> {
                        mpRole = null
                        mpConnected = false
                        mpRemoteTouchX = null
                        mpRemoteState = null
                        mpRemoteControl = null
                    }
                }

                // Bot auto-play: systematic sweep across full paddle width + miss zones
                if (_pongSettings.value.botModeEnabled) {
                    val botPhase = _gameState.value.phase
                    when (botPhase) {
                        GamePhase.READY, GamePhase.GAME_OVER -> {
                            if (botStartCountdown == 0) botStartCountdown = 60
                            if (--botStartCountdown == 0) onTapToStart()
                        }
                        GamePhase.PLAYING -> {
                            val sw = _gameState.value.screenWidth
                            val currentRally = _gameState.value.rally
                            if (currentRally != botSweepLastRally && currentRally >= 0) {
                                botSweepLastRally = currentRally
                                botSweepPhase++
                            }
                            if (sw > 0f) {
                                val halfPaddle = e.paddleWidth / 2f
                                val offset = BOT_SWEEP_OFFSETS[botSweepPhase % BOT_SWEEP_OFFSETS.size]
                                val ballX = _gameState.value.ballX
                                val absOffset = abs(offset)
                                playerTouchX = when {
                                    absOffset >= 2.0f -> {
                                        // Full miss: park at opposite wall
                                        if (ballX < sw / 2f) sw - halfPaddle else halfPaddle
                                    }
                                    absOffset >= 1.85f -> {
                                        // Near-miss: position paddle exactly (halfPaddle + ballRadius + 2px)
                                        // away from the ball — just outside the hit detection threshold.
                                        val missMargin = halfPaddle + e.ballRadius + 2f
                                        if (offset > 0f)
                                            (ballX + missMargin).coerceIn(halfPaddle, sw - halfPaddle)
                                        else
                                            (ballX - missMargin).coerceIn(halfPaddle, sw - halfPaddle)
                                    }
                                    else -> {
                                        // Hit at a specific fraction of halfPaddle
                                        (ballX - offset * halfPaddle).coerceIn(halfPaddle, sw - halfPaddle)
                                    }
                                }
                                PongDebugMetrics.botSweepPhase.set(botSweepPhase % BOT_SWEEP_OFFSETS.size)
                            }
                        }
                        else -> {
                            botStartCountdown = 60
                            botSweepLastRally = -1
                        }
                    }
                }

                val isMultiplayer = (mode == GameMode.BLUETOOTH || mode == GameMode.ONLINE)

                if (mpRole == BtRole.CLIENT && mpConnected) {
                    // ---- CLIENT MODE (BT or Online) ----
                    if (mpRemoteControl == BluetoothPongManager.CTRL_QUIT) {
                        clearMultiplayerControl(mode)
                        handleRemoteQuit(mode)
                        continue
                    }

                    if (mode == GameMode.ONLINE) {
                        tickOnlineClient(mpRemoteState, e)
                    } else if (mpRemoteState != null) {
                        updateClientState(mpRemoteState, e, mode)
                    }
                    // Send local touch (normalized 0-1)
                    val sw = _gameState.value.screenWidth
                    val normX = if (sw > 0f) playerTouchX?.let { it / sw } else null
                    when (mode) {
                        GameMode.BLUETOOTH -> btManager?.sendTouch(normX)
                        GameMode.ONLINE -> firebaseManager?.sendTouch(normX)
                        else -> {}
                    }
                } else {
                    // ---- HOST / LOCAL MODE ----
                    val p2Touch = if (mpRole == BtRole.HOST && mpConnected) {
                        mpRemoteTouchX?.let { it * _gameState.value.screenWidth }
                    } else {
                        player2TouchX
                    }

                    // Check for remote control (client's tap-to-start or quit)
                    if (mpRole == BtRole.HOST && mpConnected) {
                        when (mpRemoteControl) {
                            BluetoothPongManager.CTRL_TAP_START -> {
                                clearMultiplayerControl(mode)
                                handleTapToStart()
                            }
                            BluetoothPongManager.CTRL_QUIT -> {
                                clearMultiplayerControl(mode)
                                handleRemoteQuit(mode)
                                continue
                            }
                            BluetoothPongManager.CTRL_PAUSE -> {
                                clearMultiplayerControl(mode)
                                togglePause()
                            }
                        }
                    }

                    val prevState = _gameState.value
                    val lagFrames = if (mode == GameMode.ONLINE && mpRole == BtRole.HOST) 10 else 0
                    _gameState.update { e.update(it, playerTouchX, p2Touch, lagFrames) }

                    var newState = _gameState.value

                    // Online host: adopt client's claimed hit to eliminate ghost paddles
                    if (mode == GameMode.ONLINE && mpRole == BtRole.HOST && mpConnected) {
                        val pendingHit = firebaseManager?.remoteClientHit?.value
                        if (pendingHit != null) {
                            val sw = newState.screenWidth
                            val sh = newState.screenHeight
                            val rallyMatch = pendingHit.rally == prevState.rally
                            if (!rallyMatch) {
                                // Stale event (rally already moved on) — discard without adopting
                                firebaseManager?.clearClientHit()
                            } else {
                                val hostJustHit = newState.aiHitPulse > prevState.aiHitPulse
                                if (!hostJustHit && newState.phase == GamePhase.PLAYING) {
                                    _gameState.update { s ->
                                        s.copy(
                                            ballVx = pendingHit.vx * sw,
                                            ballVy = pendingHit.vy * sh,
                                            ballX = (pendingHit.bx * sw).coerceIn(e.ballRadius, sw - e.ballRadius),
                                            ballY = (pendingHit.by * sh).coerceIn(0f, sh),
                                            aiHitPulse = 1f,
                                            rally = s.rally + 1,
                                        )
                                    }
                                    newState = _gameState.value
                                    firebaseManager?.sendAdoptionAck(pendingHit.rally)
                                    firebaseManager?.clearClientHit()
                                }
                                // else: ball not yet in playable state — keep pending hit for next frame
                            }
                        }
                    }

                    val soundOn = _pongSettings.value.soundEnabled

                    // Paddle hit sounds
                    if (soundOn && newState.playerHitPulse > prevState.playerHitPulse) {
                        pongSound.playPlayerHit()
                    }
                    if (soundOn && newState.aiHitPulse > prevState.aiHitPulse) {
                        pongSound.playAiHit()
                    }
                    // Wall bounce sound
                    if (soundOn && newState.wallBounced) {
                        pongSound.playWallBounce()
                    }
                    // Score sound
                    if (soundOn && (newState.playerScore > prevState.playerScore ||
                                newState.aiScore > prevState.aiScore)
                    ) {
                        pongSound.playScore()
                    }

                    // Host: broadcast state to remote
                    if (mpRole == BtRole.HOST && mpConnected) {
                        val hostIdx = allCreatures.indexOf(_playerCreature.value).coerceAtLeast(0)
                        val clientIdx = allCreatures.indexOf(_player2Creature.value).coerceAtLeast(0)
                        when (mode) {
                            GameMode.BLUETOOTH -> btManager?.sendGameState(newState, hostIdx, clientIdx)
                            GameMode.ONLINE -> firebaseManager?.sendGameState(newState, hostIdx, clientIdx)
                            else -> {}
                        }
                    }
                }
            }
        }
    }

    /**
     * Online-only client tick: dead-reckons the ball every frame and processes new Firebase
     * packets when they arrive. Replaces the lerp-based updateClientState for ONLINE mode.
     */
    private fun tickOnlineClient(
        net: BluetoothPongManager.NetGameState?,
        eng: PongEngine,
    ) {
        val sw = _gameState.value.screenWidth
        val sh = _gameState.value.screenHeight
        if (sw <= 0f || sh <= 0f || net == null) return

        val isNewPacket = net !== lastProcessedRemoteState
        // Ball position/velocity in client coordinate frame (Y flipped vs host)
        val localBallX = net.ballX * sw
        val localBallY = (1f - net.ballY) * sh
        val localVx = net.ballVx * sw
        val localVy = -(net.ballVy * sh)

        if (isNewPacket) {
            lastProcessedRemoteState = net
            PongDebugMetrics.packetsReceived.incrementAndGet()
            val rallyChanged = net.rally != drLastRally && drLastRally >= 0
            when {
                drLastRally < 0 -> {
                    // First packet: initialise dead-reckoning state
                    drBallX = localBallX; drBallY = localBallY
                    drVx = localVx; drVy = localVy
                }
                rallyChanged -> {
                    // Only measure snap on hits (rally increments), not on serve resets after
                    // a point (where ball teleports to centre, inflating the metric).
                    val wasHit = net.rally > drLastRally
                    if (wasHit) {
                        val snapPx = hypot(localBallX - drBallX, localBallY - drBallY)
                        PongDebugMetrics.lastSnapPx.set(snapPx)
                        PongDebugMetrics.snapCount.incrementAndGet()
                        PongDebugMetrics.totalSnapPx.set(PongDebugMetrics.totalSnapPx.get() + snapPx)
                    }
                    drBallX = localBallX; drBallY = localBallY
                    drVx = localVx; drVy = localVy
                    clientHitSentThisRally = false
                }
                clientHitSentThisRally -> {
                    // Client hit in-flight, not yet confirmed: keep client velocity,
                    // apply only a very gentle positional correction to absorb drift
                    drBallX += (localBallX - drBallX) * 0.04f
                    drBallY += (localBallY - drBallY) * 0.04f
                }
                else -> {
                    // Normal mid-flight packet: gentle correction + adopt host velocity
                    drBallX += (localBallX - drBallX) * 0.08f
                    drBallY += (localBallY - drBallY) * 0.08f
                    drVx = localVx; drVy = localVy
                }
            }
            drLastRally = net.rally
        } else {
            // Between packets: advance ball using last known velocity (dead reckoning)
            val phase = GamePhase.entries.getOrElse(net.phaseOrdinal) { GamePhase.READY }
            if (phase == GamePhase.PLAYING) {
                drBallX += drVx
                drBallY += drVy
                val r = eng.ballRadius
                if (drBallX - r < 0f) { drBallX = r; drVx = abs(drVx) }
                else if (drBallX + r > sw) { drBallX = sw - r; drVx = -abs(drVx) }
            }
        }

        // Client-side hit detection (every frame, not just on new packets)
        val phase = GamePhase.entries.getOrElse(net.phaseOrdinal) { GamePhase.READY }
        if (phase == GamePhase.PLAYING && !clientHitSentThisRally) {
            tryDetectClientHit(net, eng, sw, sh)
        } else if (phase != GamePhase.PLAYING) {
            clientHitSentThisRally = false
        }

        // Opponent (host) paddle: lerp is still beneficial at 30 Hz
        val localAiPaddleX = net.hostPaddleX * sw
        smoothAiPaddleX += (localAiPaddleX - smoothAiPaddleX) * 0.3f

        // Sync creature selections driven by host
        val newPlayerCreature = allCreatures.getOrElse(net.clientCreatureIdx) { R.drawable.sp_pong_player }
        val newOpponentCreature = allCreatures.getOrElse(net.hostCreatureIdx) { R.drawable.sp_pong_cat }
        if (_playerCreature.value != newPlayerCreature) _playerCreature.value = newPlayerCreature
        if (_player2Creature.value != newOpponentCreature) _player2Creature.value = newOpponentCreature

        // Trail built from dead-reckoned position
        if (phase == GamePhase.PLAYING || phase == GamePhase.POINT_SCORED) {
            val last = clientTrail.firstOrNull()
            if (last == null) {
                clientTrail.addFirst(BallTrailPoint(drBallX, drBallY))
            } else {
                val dx = drBallX - last.x
                val dy = drBallY - last.y
                if (dx * dx + dy * dy > 1f) {
                    clientTrail.addFirst(BallTrailPoint(drBallX, drBallY))
                    while (clientTrail.size > 10) clientTrail.removeLast()
                }
            }
        } else {
            clientTrail.clear()
        }

        val saying = if (net.sayingSide >= 0 && net.sayingText.isNotEmpty()) {
            val side = if (net.sayingSide == 0) GameSide.AI else GameSide.PLAYER
            side to net.sayingText
        } else null

        val prevState = _gameState.value
        _gameState.value = PongGameState(
            ballX = drBallX,
            ballY = drBallY,
            playerPaddleX = playerTouchX?.coerceIn(eng.paddleWidth / 2f, sw - eng.paddleWidth / 2f)
                ?: (net.clientPaddleX * sw),
            playerPaddleY = eng.playerPaddleY,
            aiPaddleX = smoothAiPaddleX,
            aiPaddleY = eng.aiPaddleY,
            paddleWidth = eng.paddleWidth,
            paddleHeight = eng.paddleHeight,
            ballRadius = eng.ballRadius,
            playerScore = net.clientScore,
            aiScore = net.hostScore,
            phase = phase,
            playerHitPulse = net.clientHitPulse,
            aiHitPulse = net.hostHitPulse,
            rally = net.rally,
            trail = clientTrail.toList(),
            screenWidth = sw,
            screenHeight = sh,
            gameMode = GameMode.ONLINE,
            activeSaying = saying,
        )

        val soundOn = _pongSettings.value.soundEnabled
        val newState = _gameState.value
        if (soundOn && newState.playerHitPulse > prevState.playerHitPulse) pongSound.playPlayerHit()
        if (soundOn && newState.aiHitPulse > prevState.aiHitPulse) pongSound.playAiHit()
        if (soundOn && (newState.playerScore > prevState.playerScore ||
                    newState.aiScore > prevState.aiScore)
        ) pongSound.playScore()
    }

    /**
     * Detects whether the dead-reckoned ball has entered the client's paddle zone, computes the
     * deflection locally, applies it to the dead-reckoned state, and sends the hit claim to the
     * host so it can adopt the result and eliminate the ghost-paddle effect.
     */
    private fun tryDetectClientHit(
        net: BluetoothPongManager.NetGameState,
        eng: PongEngine,
        sw: Float,
        sh: Float,
    ) {
        if (drVy <= 0f) return                             // not moving toward player paddle
        val paddleSurface = eng.playerPaddleY - eng.paddleHeight
        if (drBallY + eng.ballRadius < paddleSurface) return // not yet at paddle surface

        val halfPaddle = eng.paddleWidth / 2f
        val clientPaddleX = playerTouchX?.coerceIn(halfPaddle, sw - halfPaddle)
            ?: (net.clientPaddleX * sw)
        if (drBallX < clientPaddleX - halfPaddle - eng.ballRadius ||
            drBallX > clientPaddleX + halfPaddle + eng.ballRadius
        ) return                                            // missed paddle — genuine miss

        // Ball is within paddle bounds: compute deflection (mirrors PongEngine logic)
        val hitPos = ((drBallX - clientPaddleX) / halfPaddle).coerceIn(-1f, 1f)
        val angle = hitPos * eng.maxDeflection
        val speed = (eng.ballBaseSpeed + drLastRally * eng.speedRampPerHit)
            .coerceAtMost(eng.ballMaxSpeed)
        drVx = speed * sin(angle)
        drVy = -speed * cos(angle)                         // going up in client frame
        drBallY = paddleSurface - eng.ballRadius
        clientHitSentThisRally = true
        PongDebugMetrics.clientHitsSent.incrementAndGet()
        PongDebugMetrics.pendingHitSentAtMs.set(System.currentTimeMillis())
        PongDebugMetrics.pendingHitRally.set(drLastRally)

        // Send hit claim to host in host coordinate frame (Y flipped back)
        firebaseManager?.sendClientHit(
            bx = drBallX / sw,
            by = 1f - (drBallY / sh),
            vx = drVx / sw,
            vy = -(drVy / sh),                             // flip: client up → host positive-Y
            rally = drLastRally,
        )
        if (_pongSettings.value.soundEnabled) pongSound.playPlayerHit()
    }

    private fun updateClientState(
        net: BluetoothPongManager.NetGameState,
        eng: PongEngine,
        mode: GameMode = GameMode.BLUETOOTH,
    ) {
        val sw = _gameState.value.screenWidth
        val sh = _gameState.value.screenHeight
        if (sw <= 0f || sh <= 0f) return

        // Sync creatures from host's selection
        val newPlayerCreature = allCreatures.getOrElse(net.clientCreatureIdx) { R.drawable.sp_pong_player }
        val newOpponentCreature = allCreatures.getOrElse(net.hostCreatureIdx) { R.drawable.sp_pong_cat }
        if (_playerCreature.value != newPlayerCreature) _playerCreature.value = newPlayerCreature
        if (_player2Creature.value != newOpponentCreature) _player2Creature.value = newOpponentCreature

        // Client sees flipped perspective
        val localBallX = net.ballX * sw
        val localBallY = (1f - net.ballY) * sh
        val localAiPaddleX = net.hostPaddleX * sw

        val phase = GamePhase.entries.getOrElse(net.phaseOrdinal) { GamePhase.READY }

        // BT mode: instant snap (BT latency is low enough that smoothing is unnecessary)
        val displayBallX = localBallX
        val displayBallY = localBallY
        val displayAiPaddleX = localAiPaddleX

        // Build trail on client side (movement-gated to prevent clustering)
        if (phase == GamePhase.PLAYING || phase == GamePhase.POINT_SCORED) {
            val last = clientTrail.firstOrNull()
            if (last == null) {
                clientTrail.addFirst(BallTrailPoint(displayBallX, displayBallY))
            } else {
                val dx = displayBallX - last.x
                val dy = displayBallY - last.y
                if (dx * dx + dy * dy > 1f) {
                    clientTrail.addFirst(BallTrailPoint(displayBallX, displayBallY))
                    while (clientTrail.size > 10) clientTrail.removeLast()
                }
            }
        } else {
            clientTrail.clear()
        }

        // Flip saying side
        val saying = if (net.sayingSide >= 0 && net.sayingText.isNotEmpty()) {
            val side = if (net.sayingSide == 0) GameSide.AI else GameSide.PLAYER
            side to net.sayingText
        } else {
            null
        }

        val prevState = _gameState.value
        _gameState.value = PongGameState(
            ballX = displayBallX,
            ballY = displayBallY,
            playerPaddleX = playerTouchX?.coerceIn(eng.paddleWidth / 2f, sw - eng.paddleWidth / 2f)
                ?: (net.clientPaddleX * sw),
            playerPaddleY = eng.playerPaddleY,
            aiPaddleX = displayAiPaddleX,
            aiPaddleY = eng.aiPaddleY,
            paddleWidth = eng.paddleWidth,
            paddleHeight = eng.paddleHeight,
            ballRadius = eng.ballRadius,
            playerScore = net.clientScore,
            aiScore = net.hostScore,
            phase = phase,
            playerHitPulse = net.clientHitPulse,
            aiHitPulse = net.hostHitPulse,
            rally = net.rally,
            trail = clientTrail.toList(),
            screenWidth = sw,
            screenHeight = sh,
            gameMode = mode,
            activeSaying = saying,
        )

        // Sound on client side
        val newState = _gameState.value
        val soundOn = _pongSettings.value.soundEnabled
        if (soundOn && newState.playerHitPulse > prevState.playerHitPulse) pongSound.playPlayerHit()
        if (soundOn && newState.aiHitPulse > prevState.aiHitPulse) pongSound.playAiHit()
        if (soundOn && (newState.playerScore > prevState.playerScore ||
                    newState.aiScore > prevState.aiScore)
        ) {
            pongSound.playScore()
        }
    }

    fun onTouch(x: Float, y: Float) {
        val state = _gameState.value
        when (state.gameMode) {
            GameMode.TWO_PLAYER -> {
                if (y < state.screenHeight / 2f) {
                    player2TouchX = x
                } else {
                    playerTouchX = x
                }
            }

            GameMode.BLUETOOTH -> playerTouchX = x
            GameMode.ONLINE -> playerTouchX = x
            else -> playerTouchX = x
        }
    }

    fun selectMode(mode: GameMode) {
        val e = engine ?: return
        when (mode) {
            GameMode.BLUETOOTH -> {
                firebaseManager?.cleanup()
                initBluetooth()
            }
            GameMode.ONLINE -> {
                btManager?.cleanup()
                initOnline()
            }
            else -> {
                btManager?.cleanup()
                firebaseManager?.cleanup()
            }
        }
        _gameState.value = e.createInitialState(mode)
        resetDeadReckoning()
    }

    // ---- Settings ----

    fun setSoundEnabled(enabled: Boolean) {
        _pongSettings.update { it.copy(soundEnabled = enabled) }
    }

    fun setShowSayings(show: Boolean) {
        _pongSettings.update { it.copy(showSayings = show) }
    }

    fun setBotMode(enabled: Boolean) {
        _pongSettings.update { it.copy(botModeEnabled = enabled) }
        if (!enabled) botStartCountdown = 0
    }

    fun setDebugOverlay(enabled: Boolean) {
        _pongSettings.update { it.copy(debugOverlayEnabled = enabled) }
    }

    fun setDifficulty(level: DifficultyLevel) {
        _pongSettings.update { it.copy(difficulty = level) }
        // Recreate engine with new difficulty
        val sw = _gameState.value.screenWidth
        val sh = _gameState.value.screenHeight
        if (sw <= 0f) return
        val mode = _gameState.value.gameMode
        val eng = PongEngine(sw, sh, level)
        engine = eng
        _gameState.value = eng.createInitialState(mode)
    }

    // ---- Bluetooth lifecycle ----

    private fun initBluetooth() {
        val bt = btManager ?: BluetoothPongManager(getApplication()).also { btManager = it }
        _btState.value = BluetoothLobbyState(
            available = bt.isAvailable,
            enabled = bt.isEnabled,
            lastConnectedDevice = loadLastBtDevice(),
        )
        if (bt.isAvailable && bt.isEnabled) {
            bt.loadPairedDevices()
        }
        observeBtFlows(bt)
    }

    private fun observeBtFlows(bt: BluetoothPongManager) {
        if (btObserveStarted) return
        btObserveStarted = true

        viewModelScope.launch {
            bt.connectionState.collect { conn ->
                _btState.update { it.copy(connectionState = conn) }
                if (conn == BtConnectionState.CONNECTED) {
                    val name = bt.connectedDeviceName.value ?: "Unknown Being"
                    val address = bt.connectedDeviceAddress.value
                    if (address != null) {
                        val device = BtDeviceInfo(name, address)
                        saveLastBtDevice(device)
                        _btState.update { it.copy(lastConnectedDevice = device) }
                    }
                }
            }
        }
        viewModelScope.launch {
            bt.pairedDevices.collect { devs ->
                _btState.update { it.copy(pairedDevices = devs) }
            }
        }
        viewModelScope.launch {
            bt.discoveredDevices.collect { devs ->
                _btState.update { it.copy(discoveredDevices = devs) }
            }
        }
        viewModelScope.launch {
            bt.connectedDeviceName.collect { name ->
                _btState.update { it.copy(connectedDeviceName = name, role = bt.role) }
            }
        }
    }

    fun btHost() {
        btManager?.startHosting()
    }

    fun btScan() {
        btManager?.startScanning()
    }

    fun btStopScan() {
        btManager?.stopScanning()
    }

    fun btConnect(address: String) {
        btManager?.connectToDevice(address)
    }

    fun btDisconnect() {
        btManager?.cleanup()
        _btState.value = BluetoothLobbyState()
    }

    /** Local player quits mid-game in BT mode — notify remote, then clean up. */
    fun quitBtGame() {
        btManager?.sendControl(BluetoothPongManager.CTRL_QUIT)
        btManager?.cleanup()
        _btState.value = BluetoothLobbyState()
        resetGame()
    }

    /** Local player quits mid-game in online mode. */
    fun quitOnlineGame() {
        firebaseManager?.sendControl(BluetoothPongManager.CTRL_QUIT)
        firebaseManager?.cleanup()
        _onlineState.value = OnlineLobbyState()
        resetGame()
    }

    /** Remote player quit — end the game and tear down. */
    private fun handleRemoteQuit(mode: GameMode = GameMode.BLUETOOTH) {
        _gameState.update { state ->
            state.copy(
                phase = GamePhase.GAME_OVER,
                activeSaying = GameSide.AI to "The distant being has departed!",
            )
        }
        when (mode) {
            GameMode.BLUETOOTH -> {
                btManager?.cleanup()
                _btState.value = BluetoothLobbyState()
            }
            GameMode.ONLINE -> {
                firebaseManager?.cleanup()
                _onlineState.value = OnlineLobbyState()
            }
            else -> {}
        }
    }

    private fun clearMultiplayerControl(mode: GameMode) {
        when (mode) {
            GameMode.BLUETOOTH -> btManager?.clearControl()
            GameMode.ONLINE -> firebaseManager?.clearControl()
            else -> {}
        }
    }

    fun updateBtPermissions(granted: Boolean) {
        _btState.update { it.copy(permissionsGranted = granted) }
        if (granted) {
            val bt = btManager ?: return
            _btState.update { it.copy(enabled = bt.isEnabled) }
            if (bt.isEnabled) bt.loadPairedDevices()
        }
    }

    // ---- Online (Firebase) lifecycle ----

    private fun initOnline() {
        val fb = firebaseManager ?: FirebasePongManager().also { firebaseManager = it }
        _onlineState.value = OnlineLobbyState()
        observeOnlineFlows(fb)
    }

    private fun observeOnlineFlows(fb: FirebasePongManager) {
        if (onlineObserveStarted) return
        onlineObserveStarted = true

        viewModelScope.launch {
            fb.connectionState.collect { conn ->
                _onlineState.update { it.copy(connectionState = conn) }
            }
        }
        viewModelScope.launch {
            fb.roomCode.collect { code ->
                _onlineState.update { it.copy(roomCode = code) }
            }
        }
        viewModelScope.launch {
            fb.connectedPlayerName.collect { name ->
                _onlineState.update { it.copy(connectedPlayerName = name, role = fb.role) }
            }
        }
        viewModelScope.launch {
            fb.remoteAdoptedRally.collect { adoptedRally ->
                if (adoptedRally != null &&
                    adoptedRally == PongDebugMetrics.pendingHitRally.get()
                ) {
                    PongDebugMetrics.clientHitsAdopted.incrementAndGet()
                    val rtt = System.currentTimeMillis() - PongDebugMetrics.pendingHitSentAtMs.get()
                    PongDebugMetrics.lastRoundTripMs.set(rtt)
                }
            }
        }
    }

    fun onlineCreateRoom() {
        firebaseManager?.createRoom()
    }

    fun onlineJoinRoom(code: String) {
        firebaseManager?.joinRoom(code)
    }

    fun onlineDisconnect() {
        firebaseManager?.cleanup()
        _onlineState.value = OnlineLobbyState()
    }

    // ---- Last BT device persistence ----

    private fun saveLastBtDevice(device: BtDeviceInfo) {
        getApplication<Application>()
            .getSharedPreferences("pong_bt", Context.MODE_PRIVATE)
            .edit()
            .putString("last_bt_name", device.name)
            .putString("last_bt_address", device.address)
            .apply()
    }

    private fun loadLastBtDevice(): BtDeviceInfo? {
        val prefs = getApplication<Application>()
            .getSharedPreferences("pong_bt", Context.MODE_PRIVATE)
        val name = prefs.getString("last_bt_name", null) ?: return null
        val address = prefs.getString("last_bt_address", null) ?: return null
        return BtDeviceInfo(name, address)
    }

    // ---- Creature selection ----

    private val allCreatures = listOf(
        R.drawable.sp_pong_player,
        R.drawable.sp_pong_cat,
        R.drawable.sp_pong_dog,
    )

    fun selectCreature(resId: Int) {
        _playerCreature.value = resId
        _opponentCreature.value = allCreatures.filter { it != resId }.random()
        // Embargo: if player2 had the same, reassign
        if (_player2Creature.value == resId) {
            _player2Creature.value = allCreatures.first { it != resId }
        }
    }

    fun selectPlayer2Creature(resId: Int) {
        // Embargo: can't pick same as player 1
        if (resId != _playerCreature.value) {
            _player2Creature.value = resId
        }
    }

    // ---- Reset ----

    fun resetGame() {
        val e = engine ?: return
        _gameState.value = e.createInitialState()
        resetDeadReckoning()
    }

    private fun resetDeadReckoning() {
        clientTrail.clear()
        drBallX = 0f; drBallY = 0f
        drVx = 0f; drVy = 0f
        drLastRally = -1
        clientHitSentThisRally = false
        lastProcessedRemoteState = null
        smoothAiPaddleX = 0f
        botStartCountdown = 0
        botSweepPhase = 0
        botSweepLastRally = -1
        PongDebugMetrics.reset()
    }

    // ---- Pause ----

    fun togglePause() {
        _gameState.update { state ->
            when (state.phase) {
                GamePhase.PLAYING -> state.copy(phase = GamePhase.PAUSED)
                GamePhase.PAUSED -> state.copy(phase = GamePhase.PLAYING)
                else -> state
            }
        }
    }

    /** Pause/unpause with multiplayer sync — client signals host, host toggles authoritatively. */
    fun requestPause() {
        togglePause()
        val mode = _gameState.value.gameMode
        when (mode) {
            GameMode.BLUETOOTH -> {
                val bt = btManager
                if (bt?.connectionState?.value == BtConnectionState.CONNECTED &&
                    bt.role == BtRole.CLIENT
                ) {
                    bt.sendControl(BluetoothPongManager.CTRL_PAUSE)
                }
            }
            GameMode.ONLINE -> {
                val fb = firebaseManager
                if (fb?.isConnected == true && fb.role == BtRole.CLIENT) {
                    fb.sendControl(BluetoothPongManager.CTRL_PAUSE)
                }
            }
            else -> {}
        }
    }

    // ---- Game start ----

    fun onTapToStart() {
        val mode = _gameState.value.gameMode
        when (mode) {
            GameMode.BLUETOOTH -> {
                val bt = btManager ?: return
                if (bt.connectionState.value != BtConnectionState.CONNECTED) return
                if (bt.role == BtRole.CLIENT) {
                    bt.sendControl(BluetoothPongManager.CTRL_TAP_START)
                    return
                }
            }
            GameMode.ONLINE -> {
                val fb = firebaseManager ?: return
                if (!fb.isConnected) return
                if (fb.role == BtRole.CLIENT) {
                    fb.sendControl(BluetoothPongManager.CTRL_TAP_START)
                    return
                }
            }
            else -> {}
        }
        handleTapToStart()
    }

    private fun handleTapToStart() {
        val e = engine ?: return
        val state = _gameState.value
        when (state.phase) {
            GamePhase.READY -> _gameState.value = e.startServe(state)
            GamePhase.GAME_OVER -> {
                _gameState.value = e.reset(state.gameMode)
                _gameState.value = e.startServe(_gameState.value)
                clientTrail.clear()
            }

            else -> {}
        }
    }

    override fun onCleared() {
        super.onCleared()
        btManager?.cleanup()
        firebaseManager?.cleanup()
        pongSound.release()
    }
}
