package com.quokkalabs.strangeplanet.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.quokkalabs.strangeplanet.R
import com.quokkalabs.strangeplanet.audio.PongSoundManager
import com.quokkalabs.strangeplanet.bluetooth.BluetoothPongManager
import com.quokkalabs.strangeplanet.data.model.BallTrailPoint
import com.quokkalabs.strangeplanet.data.model.BluetoothLobbyState
import com.quokkalabs.strangeplanet.data.model.BtConnectionState
import com.quokkalabs.strangeplanet.data.model.BtRole
import com.quokkalabs.strangeplanet.data.model.DifficultyLevel
import com.quokkalabs.strangeplanet.data.model.GameMode
import com.quokkalabs.strangeplanet.data.model.GamePhase
import com.quokkalabs.strangeplanet.data.model.GameSide
import com.quokkalabs.strangeplanet.data.model.PongGameState
import com.quokkalabs.strangeplanet.data.model.PongSettings
import com.quokkalabs.strangeplanet.domain.PongEngine
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

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

    private val pongSound = PongSoundManager()

    private var playerTouchX: Float? = null
    private var player2TouchX: Float? = null
    private var btManager: BluetoothPongManager? = null
    private var btObserveStarted = false

    // Client-side trail accumulation
    private val clientTrail = ArrayDeque<BallTrailPoint>()

    fun initGame(screenWidth: Float, screenHeight: Float) {
        if (engine != null) return

        val eng = PongEngine(screenWidth, screenHeight, _pongSettings.value.difficulty)
        engine = eng
        _gameState.value = eng.createInitialState()

        viewModelScope.launch {
            while (isActive) {
                delay(16)
                val e = engine ?: continue

                val bt = btManager
                val btConnected =
                    bt?.connectionState?.value == BtConnectionState.CONNECTED
                val btRole = bt?.role

                if (btRole == BtRole.CLIENT && btConnected) {
                    // ---- CLIENT MODE ----
                    val netState = bt.remoteGameState.value
                    if (netState != null) {
                        updateClientState(netState, e)
                    }
                    // Send local touch (normalized 0-1)
                    val sw = _gameState.value.screenWidth
                    val normX = if (sw > 0f) playerTouchX?.let { it / sw } else null
                    bt.sendTouch(normX)
                } else {
                    // ---- HOST / LOCAL MODE ----
                    val p2Touch = if (btRole == BtRole.HOST && btConnected) {
                        bt?.remoteTouchX?.value?.let { it * _gameState.value.screenWidth }
                    } else {
                        player2TouchX
                    }

                    // Check for remote control (client's tap-to-start)
                    if (btRole == BtRole.HOST && btConnected) {
                        val ctrl = bt?.remoteControl?.value
                        if (ctrl == BluetoothPongManager.CTRL_TAP_START) {
                            bt?.clearControl()
                            handleTapToStart()
                        }
                    }

                    val prevState = _gameState.value
                    _gameState.update { e.update(it, playerTouchX, p2Touch) }

                    val newState = _gameState.value
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

                    // Host: broadcast state to client
                    if (btRole == BtRole.HOST && btConnected) {
                        bt?.sendGameState(newState)
                    }
                }
            }
        }
    }

    private fun updateClientState(
        net: BluetoothPongManager.NetGameState,
        eng: PongEngine,
    ) {
        val sw = _gameState.value.screenWidth
        val sh = _gameState.value.screenHeight
        if (sw <= 0f || sh <= 0f) return

        // Client sees flipped perspective
        val localBallX = net.ballX * sw
        val localBallY = (1f - net.ballY) * sh

        val phase = GamePhase.entries.getOrElse(net.phaseOrdinal) { GamePhase.READY }

        // Build trail on client side
        if (phase == GamePhase.PLAYING || phase == GamePhase.POINT_SCORED) {
            clientTrail.addFirst(BallTrailPoint(localBallX, localBallY))
            while (clientTrail.size > 10) clientTrail.removeLast()
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
            ballX = localBallX,
            ballY = localBallY,
            playerPaddleX = net.clientPaddleX * sw,
            playerPaddleY = eng.playerPaddleY,
            aiPaddleX = net.hostPaddleX * sw,
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
            gameMode = GameMode.BLUETOOTH,
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
            else -> playerTouchX = x
        }
    }

    fun selectMode(mode: GameMode) {
        val e = engine ?: return
        if (mode == GameMode.BLUETOOTH) {
            initBluetooth()
        } else {
            btManager?.cleanup()
        }
        _gameState.value = e.createInitialState(mode)
        clientTrail.clear()
    }

    // ---- Settings ----

    fun setSoundEnabled(enabled: Boolean) {
        _pongSettings.update { it.copy(soundEnabled = enabled) }
    }

    fun setShowSayings(show: Boolean) {
        _pongSettings.update { it.copy(showSayings = show) }
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

    fun updateBtPermissions(granted: Boolean) {
        _btState.update { it.copy(permissionsGranted = granted) }
        if (granted) {
            val bt = btManager ?: return
            _btState.update { it.copy(enabled = bt.isEnabled) }
            if (bt.isEnabled) bt.loadPairedDevices()
        }
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
    }

    fun selectPlayer2Creature(resId: Int) {
        _player2Creature.value = resId
    }

    // ---- Reset ----

    fun resetGame() {
        val e = engine ?: return
        _gameState.value = e.createInitialState()
        clientTrail.clear()
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

    // ---- Game start ----

    fun onTapToStart() {
        if (_gameState.value.gameMode == GameMode.BLUETOOTH) {
            val bt = btManager ?: return
            val btConnected = bt.connectionState.value == BtConnectionState.CONNECTED
            if (!btConnected) return

            if (bt.role == BtRole.CLIENT) {
                bt.sendControl(BluetoothPongManager.CTRL_TAP_START)
                return
            }
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
        pongSound.release()
    }
}
