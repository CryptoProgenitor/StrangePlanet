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

    fun initGame(screenWidth: Float, screenHeight: Float) {
        if (engine != null) return

        val eng = PongEngine(screenWidth, screenHeight, _pongSettings.value.difficulty)
        engine = eng
        _gameState.value = eng.createInitialState()

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

                val isMultiplayer = (mode == GameMode.BLUETOOTH || mode == GameMode.ONLINE)

                if (mpRole == BtRole.CLIENT && mpConnected) {
                    // ---- CLIENT MODE (BT or Online) ----
                    if (mpRemoteControl == BluetoothPongManager.CTRL_QUIT) {
                        clearMultiplayerControl(mode)
                        handleRemoteQuit(mode)
                        continue
                    }

                    if (mpRemoteState != null) {
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
            playerPaddleX = playerTouchX?.coerceIn(eng.paddleWidth / 2f, sw - eng.paddleWidth / 2f)
                ?: (net.clientPaddleX * sw),
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
