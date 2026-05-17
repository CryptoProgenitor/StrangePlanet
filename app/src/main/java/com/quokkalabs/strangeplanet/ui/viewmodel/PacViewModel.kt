package com.quokkalabs.strangeplanet.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.quokkalabs.strangeplanet.bluetooth.BluetoothPacManager
import com.quokkalabs.strangeplanet.bluetooth.BtHistory
import com.quokkalabs.strangeplanet.data.model.BtConnectionState
import com.quokkalabs.strangeplanet.data.model.BtDeviceInfo
import com.quokkalabs.strangeplanet.data.model.BluetoothLobbyState
import com.quokkalabs.strangeplanet.data.model.PacAvatar
import com.quokkalabs.strangeplanet.data.model.PacDir
import com.quokkalabs.strangeplanet.data.model.PacEntity
import com.quokkalabs.strangeplanet.data.model.PacGameState
import com.quokkalabs.strangeplanet.data.model.PacMode
import com.quokkalabs.strangeplanet.data.model.PacPhase
import com.quokkalabs.strangeplanet.data.model.PacSettings
import com.quokkalabs.strangeplanet.data.model.SeekerEntity
import com.quokkalabs.strangeplanet.data.model.SeekerMode
import com.quokkalabs.strangeplanet.data.model.SeekerType
import com.quokkalabs.strangeplanet.domain.MazeEngine
import android.util.Log
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class PacViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = application.getSharedPreferences("pac_prefs", Application.MODE_PRIVATE)

    private var engine: MazeEngine? = null
    private var loopJob: Job? = null
    @Volatile private var paused = false

    private var highScore: Int = prefs.getInt(KEY_HIGH_SCORE, 0)

    private val _state = MutableStateFlow(PacGameState(highScore = highScore))
    val state: StateFlow<PacGameState> = _state.asStateFlow()

    private val _pacSettings = MutableStateFlow(
        PacSettings(
            soundEnabled = prefs.getBoolean(KEY_SOUND, true),
            showSayings = prefs.getBoolean(KEY_SAYINGS, true),
            avatar = runCatching {
                PacAvatar.valueOf(prefs.getString(KEY_AVATAR, PacAvatar.BEING.name)!!)
            }.getOrDefault(PacAvatar.BEING),
            joypadEnabled = prefs.getBoolean(KEY_JOYPAD, false),
        ),
    )
    val pacSettings: StateFlow<PacSettings> = _pacSettings.asStateFlow()

    // Swipe mode: consumed once by the next tick.
    private var pendingDir: PacDir? = null

    // Joystick mode: persistent until the joystick moves; speed 0 = halted.
    @Volatile private var joystickDir: PacDir = PacDir.NONE
    @Volatile private var joystickSpeed: Float = 0f
    @Volatile private var lastBtTickMs = 0L

    private var screenW = 0f
    private var screenH = 0f

    // ── Bluetooth contest state ──────────────────────────────────────────────

    private var btManager: BluetoothPacManager? = null
    private var btPermissionsGranted = false

    private val _btState = MutableStateFlow(BluetoothLobbyState())
    val btState: StateFlow<BluetoothLobbyState> = _btState.asStateFlow()

    // The adversary's chosen seeker (client picks; host learns it on the wire).
    private val _pickedSeeker = MutableStateFlow(SeekerType.MINUTE_REMINDER)
    val pickedSeeker: StateFlow<SeekerType> = _pickedSeeker.asStateFlow()

    // True once "Nearby Adversary" is selected in the lobby (vs solo pursuit).
    private val _btLobbyActive = MutableStateFlow(false)
    val btLobbyActive: StateFlow<Boolean> = _btLobbyActive.asStateFlow()

    // Client-side latest steered direction (sent only when it changes).
    @Volatile private var clientSeekerDir: PacDir = PacDir.NONE

    // Host diff tracking for the per-tick consumed-tile deltas.
    private var lastSentPellets: Set<Int> = emptySet()
    private var lastSentSocks: Set<Int> = emptySet()
    private var needsInitBroadcast = false

    // Client working copies of the static maze sets (deltas applied per tick).
    private var clientPellets: MutableSet<Int> = mutableSetOf()
    private var clientSocks: MutableSet<Int> = mutableSetOf()

    private fun commitHighScore(score: Int) {
        if (score > highScore) {
            highScore = score
            prefs.edit().putInt(KEY_HIGH_SCORE, highScore).apply()
        }
    }

    /**
     * Re-entrant: the ViewModel is Activity-scoped and outlives the screen,
     * so this (re)starts the loop and, when a SOLO snapshot exists, presents
     * a clean READY board so the resume prompt can appear.
     */
    fun initGame(screenWidth: Float, screenHeight: Float) {
        val firstInit = engine == null
        screenW = screenWidth
        screenH = screenHeight
        val eng = engine ?: MazeEngine(screenWidth, screenHeight).also { engine = it }

        if (firstInit || hasSavedSession()) {
            _state.value = eng.createInitialState(highScore = highScore)
        }
        startLoop()
    }

    private fun startLoop() {
        if (loopJob?.isActive == true) return
        loopJob = viewModelScope.launch {
            while (isActive) {
                delay(16)
                val e = engine ?: continue
                if (paused) continue
                try {
                when (_state.value.mode) {
                    PacMode.BT_CLIENT -> {
                        // Client renders host snapshots only — no local sim.
                        val tickAge = lastBtTickMs
                        if (tickAge > 0L && System.currentTimeMillis() - tickAge > 10_000L &&
                            _state.value.phase == PacPhase.PLAYING
                        ) {
                            val eng = engine ?: continue
                            commitHighScore(_state.value.score)
                            _state.value = eng.createInitialState(highScore = highScore)
                        }
                    }

                    PacMode.BT_HOST -> {
                        if (_state.value.phase == PacPhase.PLAYING) {
                            val dir: PacDir?
                            val speed: Float
                            if (_pacSettings.value.joypadEnabled) {
                                dir = if (joystickDir == PacDir.NONE) null else joystickDir
                                speed = joystickSpeed
                            } else {
                                dir = pendingDir
                                pendingDir = null
                                speed = 1f
                            }
                            val seekerDir = btManager?.remoteSeekerDir?.value
                            val prev = _state.value
                            val next = e.update(prev, dir, speed, seekerDir)
                            _state.value = next
                            broadcastHost(prev, next)
                        }
                        handleHostPhase(e)
                    }

                    PacMode.SOLO -> {
                        if (_pacSettings.value.joypadEnabled) {
                            val dir = joystickDir
                            val speed = joystickSpeed
                            _state.update {
                                e.update(it, if (dir == PacDir.NONE) null else dir, speed)
                            }
                        } else {
                            val dir = pendingDir
                            pendingDir = null
                            _state.update { e.update(it, dir) }
                        }
                        handleSoloPhase(e)
                    }
                }
                } catch (t: Throwable) {
                    // A single bad frame must never permanently kill the loop.
                    Log.e("PacViewModel", "frame failed", t)
                }
            }
        }
    }

    /** Stop simulating when the screen leaves so the maze can't run on. */
    fun stopLoop() {
        loopJob?.cancel()
        loopJob = null
    }

    /** Freeze the loop while a modal (exit / resume prompt) is showing. */
    fun setPaused(value: Boolean) {
        paused = value
    }

    private suspend fun handleSoloPhase(e: MazeEngine) {
        when (_state.value.phase) {
            PacPhase.LEVEL_CLEARED -> {
                delay(1800)
                while (paused && currentCoroutineContext().isActive) delay(50)
                if (_state.value.phase == PacPhase.LEVEL_CLEARED) {
                    val s = _state.value
                    _state.value = e.createInitialState(
                        level = s.level + 1,
                        score = s.score,
                        lives = s.lives,
                        highScore = highScore,
                    ).copy(phase = PacPhase.PLAYING)
                }
            }
            PacPhase.DYING -> {
                delay(2000)
                while (paused && currentCoroutineContext().isActive) delay(50)
                val s = _state.value
                if (s.phase == PacPhase.DYING) {
                    if (s.lives <= 0) {
                        commitHighScore(s.score)
                        _state.update { it.copy(phase = PacPhase.GAME_OVER, highScore = highScore) }
                    } else {
                        _state.value = e.respawnEntities(s)
                    }
                }
            }
            else -> {}
        }
    }

    private suspend fun handleHostPhase(e: MazeEngine) {
        val s = _state.value
        when (s.phase) {
            PacPhase.LEVEL_CLEARED -> {
                btManager?.sendTick(s, emptyList(), emptyList())
                delay(1800)
                while (paused && currentCoroutineContext().isActive) delay(50)
                if (_state.value.phase == PacPhase.LEVEL_CLEARED) {
                    val cur = _state.value
                    _state.value = e.createInitialState(
                        level = cur.level + 1,
                        score = cur.score,
                        lives = cur.lives,
                        highScore = highScore,
                        mode = PacMode.BT_HOST,
                        controlledSeekerType = cur.controlledSeekerType,
                    ).copy(phase = PacPhase.PLAYING)
                    needsInitBroadcast = true
                    broadcastHost(_state.value, _state.value, force = true)
                }
            }
            PacPhase.DYING -> {
                btManager?.sendTick(s, emptyList(), emptyList())
                delay(2000)
                while (paused && currentCoroutineContext().isActive) delay(50)
                val cur = _state.value
                if (cur.phase == PacPhase.DYING) {
                    if (cur.lives <= 0) {
                        commitHighScore(cur.score)
                        _state.update { it.copy(phase = PacPhase.GAME_OVER, highScore = highScore) }
                        btManager?.sendTick(_state.value, emptyList(), emptyList())
                    } else {
                        // Pellets are preserved — no MSG_INIT needed; the client
                        // already holds the correct maze state via prior deltas.
                        _state.value = e.respawnEntities(cur)
                        broadcastHost(_state.value, _state.value, force = true)
                    }
                }
            }
            else -> {}
        }
    }

    // 30 Hz on the wire (every other 16 ms tick); resends static maze on demand.
    private var broadcastTick = 0
    private var resyncTimer = 0
    private fun broadcastHost(prev: PacGameState, next: PacGameState, force: Boolean = false) {
        val mgr = btManager ?: return
        resyncTimer++
        val needsResync = resyncTimer >= 600
        if (needsInitBroadcast || needsResync) {
            if (needsResync) { resyncTimer = 0; broadcastTick = -1 }
            mgr.sendInit(next)
            lastSentPellets = next.pellets
            lastSentSocks = next.socks
            needsInitBroadcast = false
        }
        broadcastTick++
        if (!force && broadcastTick % 2 != 0) return
        val eatenP = lastSentPellets - next.pellets
        val eatenS = lastSentSocks - next.socks
        lastSentPellets = next.pellets
        lastSentSocks = next.socks
        mgr.sendTick(next, eatenP, eatenS)
    }

    /** A screen-wide swipe resolved to a cardinal direction → input queue. */
    fun onSwipe(dir: PacDir) {
        if (dir == PacDir.NONE) return
        if (_state.value.mode == PacMode.BT_CLIENT) {
            onClientSeekerDir(dir)
            return
        }
        pendingDir = dir
        if (_state.value.phase == PacPhase.READY) onTapToStart()
    }

    /** Finger lifted in swipe mode — halt the being immediately. */
    fun haltBeing() {
        pendingDir = null
        if (_state.value.mode == PacMode.BT_CLIENT) {
            onClientSeekerDir(PacDir.NONE)
        } else {
            _state.update { s -> s.copy(being = s.being.copy(dir = PacDir.NONE)) }
        }
    }

    fun onTapToStart() {
        val e = engine ?: return
        // A contest is driven entirely from the lobby / back button.
        if (_state.value.mode != PacMode.SOLO) return
        when (_state.value.phase) {
            PacPhase.READY -> _state.value = e.startGame(_state.value)
            PacPhase.PAUSED -> _state.value = e.resumeGame(_state.value)
            PacPhase.GAME_OVER -> {
                _state.value = e.createInitialState(highScore = highScore)
                _state.value = e.startGame(_state.value)
            }
            else -> {}
        }
    }

    fun onJoystick(dir: PacDir, speed: Float) {
        if (_state.value.mode == PacMode.BT_CLIENT) {
            onClientSeekerDir(if (speed <= 0f) PacDir.NONE else dir)
            return
        }
        joystickDir = dir
        joystickSpeed = speed
        if (speed > 0f && _state.value.phase == PacPhase.READY) onTapToStart()
    }

    /** Client: steer the chosen seeker; transmit only when the heading flips. */
    private fun onClientSeekerDir(dir: PacDir) {
        if (dir == clientSeekerDir) return
        clientSeekerDir = dir
        btManager?.sendDir(dir)
    }

    fun resetGame() {
        val e = engine ?: return
        commitHighScore(_state.value.score)
        if (_state.value.mode != PacMode.SOLO) {
            btDisconnect()
        }
        _state.value = e.createInitialState(highScore = highScore)
        pendingDir = null
        joystickDir = PacDir.NONE
        joystickSpeed = 0f
        clientSeekerDir = PacDir.NONE
    }

    fun pauseGame() {
        if (_state.value.mode != PacMode.SOLO) return // No mid-contest pause.
        val e = engine ?: return
        _state.update { e.pauseGame(it) }
    }

    fun resumeGame() {
        if (_state.value.mode != PacMode.SOLO) return
        val e = engine ?: return
        _state.update { e.resumeGame(it) }
    }

    fun setSoundEnabled(enabled: Boolean) {
        _pacSettings.update { it.copy(soundEnabled = enabled) }
        prefs.edit().putBoolean(KEY_SOUND, enabled).apply()
    }

    fun setShowSayings(show: Boolean) {
        _pacSettings.update { it.copy(showSayings = show) }
        prefs.edit().putBoolean(KEY_SAYINGS, show).apply()
    }

    fun setAvatar(avatar: PacAvatar) {
        _pacSettings.update { it.copy(avatar = avatar) }
        prefs.edit().putString(KEY_AVATAR, avatar.name).apply()
    }

    fun setJoypadEnabled(enabled: Boolean) {
        _pacSettings.update { it.copy(joypadEnabled = enabled) }
        prefs.edit().putBoolean(KEY_JOYPAD, enabled).apply()
        joystickDir = PacDir.NONE
        joystickSpeed = 0f
    }

    // ── Bluetooth lobby API ──────────────────────────────────────────────────

    private var recentDevices: List<BtDeviceInfo> = emptyList()

    private fun ensureBt(): BluetoothPacManager {
        btManager?.let { return it }
        val mgr = BluetoothPacManager(getApplication())
        btManager = mgr
        recentDevices = BtHistory.load(getApplication())
        refreshBtState()

        viewModelScope.launch {
            mgr.connectionState.collect { conn ->
                if (conn == BtConnectionState.CONNECTED) {
                    val name = mgr.connectedDeviceName.value ?: "Unknown Being"
                    val addr = mgr.connectedDeviceAddress.value
                    if (addr != null) {
                        recentDevices =
                            BtHistory.remember(getApplication(), BtDeviceInfo(name, addr))
                    }
                }
                refreshBtState()
            }
        }
        viewModelScope.launch {
            mgr.pairedDevices.collect { refreshBtState() }
        }
        viewModelScope.launch {
            mgr.discoveredDevices.collect { refreshBtState() }
        }
        viewModelScope.launch {
            mgr.connectedDeviceName.collect { refreshBtState() }
        }
        // Host: learn which seeker the adversary chose.
        viewModelScope.launch {
            mgr.remoteSeekerPick.collect { picked ->
                if (picked != null) _pickedSeeker.value = picked
            }
        }
        // Host: client requested to abandon the contest.
        viewModelScope.launch {
            mgr.remoteControl.collect { ctrl ->
                if (ctrl == BluetoothPacManager.CTRL_QUIT) {
                    mgr.clearControl()
                    val e = engine ?: return@collect
                    _state.value = e.createInitialState(highScore = highScore)
                }
            }
        }
        // Client: rebuild the rendered state from host snapshots.
        viewModelScope.launch {
            mgr.remoteInit.collect { init -> if (init != null) applyClientInit(init) }
        }
        viewModelScope.launch {
            mgr.remoteTick.collect { tick -> if (tick != null) applyClientTick(tick) }
        }
        return mgr
    }

    private fun refreshBtState() {
        val mgr = btManager
        _btState.value = BluetoothLobbyState(
            available = mgr?.isAvailable == true,
            enabled = mgr?.isEnabled == true,
            permissionsGranted = btPermissionsGranted,
            connectionState = mgr?.connectionState?.value ?: BtConnectionState.IDLE,
            pairedDevices = mgr?.pairedDevices?.value ?: emptyList(),
            discoveredDevices = mgr?.discoveredDevices?.value ?: emptyList(),
            connectedDeviceName = mgr?.connectedDeviceName?.value,
            role = mgr?.role,
            recentDevices = recentDevices,
        )
    }

    fun selectSoloMode() {
        _btLobbyActive.value = false
        btDisconnect()
    }

    fun selectBtMode() {
        _btLobbyActive.value = true
        ensureBt()
        if (btPermissionsGranted) btManager?.loadPairedDevices()
    }

    fun updateBtPermissions(granted: Boolean) {
        btPermissionsGranted = granted
        if (granted) {
            ensureBt().loadPairedDevices()
        }
        refreshBtState()
    }

    fun btHost() {
        ensureBt().startHosting()
    }

    fun btScan() {
        ensureBt().startScanning()
    }

    fun btStopScan() {
        btManager?.stopScanning()
    }

    fun btConnect(address: String) {
        ensureBt().connectToDevice(address)
    }

    fun btDisconnect() {
        btManager?.cleanup()
        refreshBtState()
    }

    /** Client picks the seeker it will steer; transmit the choice to the host. */
    fun selectSeeker(type: SeekerType) {
        _pickedSeeker.value = type
        btManager?.sendPick(type)
    }

    /** Host: begin the contest with the adversary's chosen seeker. */
    fun startBtContest() {
        val e = engine ?: return
        val mgr = btManager ?: return
        if (mgr.role != com.quokkalabs.strangeplanet.data.model.BtRole.HOST) return
        val picked = mgr.remoteSeekerPick.value ?: _pickedSeeker.value
        _state.value = e.createInitialState(
            highScore = highScore,
            mode = PacMode.BT_HOST,
            controlledSeekerType = picked,
        ).let { e.startGame(it) }
        needsInitBroadcast = true
        broadcastHost(_state.value, _state.value, force = true)
    }

    private fun applyClientInit(init: BluetoothPacManager.NetInit) {
        clientPellets = init.pellets.toMutableSet()
        clientSocks = init.socks.toMutableSet()
        val controlled = SeekerType.entries.getOrNull(init.controlledSeekerOrdinal)
        _state.value = PacGameState(
            being = PacEntity(col = init.beingCol, row = init.beingRow),
            cols = init.cols,
            rows = init.rows,
            tileSize = init.tileSize,
            originX = init.originX,
            originY = init.originY,
            pellets = clientPellets.toSet(),
            totalPellets = init.pellets.size,
            socks = clientSocks.toSet(),
            walls = init.walls,
            score = init.score,
            highScore = init.highScore,
            lives = init.lives,
            level = init.level,
            phase = PacPhase.entries.getOrNull(init.phaseOrdinal) ?: PacPhase.READY,
            screenWidth = init.screenWidth,
            screenHeight = init.screenHeight,
            seekers = init.seekers.map { it.toEntity() },
            mode = PacMode.BT_CLIENT,
            controlledSeekerType = controlled,
        )
    }

    private fun applyClientTick(tick: BluetoothPacManager.NetTick) {
        if (_state.value.mode != PacMode.BT_CLIENT) return
        lastBtTickMs = System.currentTimeMillis()
        tick.eatenPellets.forEach { clientPellets.remove(it) }
        tick.eatenSocks.forEach { clientSocks.remove(it) }
        _state.update { cur ->
            cur.copy(
                being = cur.being.copy(
                    col = tick.beingCol,
                    row = tick.beingRow,
                    progress = tick.beingProgress,
                    dir = PacDir.entries.getOrNull(tick.beingDirOrdinal) ?: PacDir.NONE,
                ),
                seekers = tick.seekers.map { it.toEntity() },
                pellets = clientPellets.toSet(),
                socks = clientSocks.toSet(),
                score = tick.score,
                lives = tick.lives,
                level = tick.level,
                phase = PacPhase.entries.getOrNull(tick.phaseOrdinal) ?: PacPhase.PLAYING,
                frightenedTick = tick.frightenedTick,
                activeSaying = tick.saying,
            )
        }
    }

    private fun BluetoothPacManager.SeekerWire.toEntity() = SeekerEntity(
        type = SeekerType.entries.getOrNull(typeOrdinal) ?: SeekerType.MINUTE_REMINDER,
        col = col,
        row = row,
        progress = progress,
        dir = PacDir.entries.getOrNull(dirOrdinal) ?: PacDir.LEFT,
        mode = SeekerMode.entries.getOrNull(modeOrdinal) ?: SeekerMode.SCATTER,
        penTimer = penTimer,
    )

    override fun onCleared() {
        super.onCleared()
        btManager?.cleanup()
    }

    // ── Session preservation (progress snapshot, SOLO only) ────────────────

    fun hasSavedSession(): Boolean = prefs.getBoolean(KEY_SESSION, false)

    fun saveSession() {
        val s = _state.value
        if (s.mode != PacMode.SOLO || s.phase != PacPhase.PLAYING) return
        commitHighScore(s.score)
        prefs.edit()
            .putBoolean(KEY_SESSION, true)
            .putInt("$KEY_SESSION.score", s.score)
            .putInt("$KEY_SESSION.lives", s.lives)
            .putInt("$KEY_SESSION.level", s.level)
            .apply()
    }

    fun resumeSession() {
        val e = engine ?: return
        if (!prefs.getBoolean(KEY_SESSION, false)) return
        _state.value = e.createInitialState(
            level = prefs.getInt("$KEY_SESSION.level", 1),
            score = prefs.getInt("$KEY_SESSION.score", 0),
            lives = prefs.getInt("$KEY_SESSION.lives", 3),
            highScore = highScore,
        )
        pendingDir = null
        joystickDir = PacDir.NONE
        joystickSpeed = 0f
        discardSavedSession()
    }

    fun discardSavedSession() {
        prefs.edit()
            .remove(KEY_SESSION)
            .remove("$KEY_SESSION.score")
            .remove("$KEY_SESSION.lives")
            .remove("$KEY_SESSION.level")
            .apply()
    }

    private companion object {
        const val KEY_HIGH_SCORE = "high_score"
        const val KEY_SOUND = "sound_enabled"
        const val KEY_SAYINGS = "show_sayings"
        const val KEY_AVATAR = "avatar"
        const val KEY_JOYPAD = "joypad_enabled"
        const val KEY_SESSION = "saved_session"
    }
}
