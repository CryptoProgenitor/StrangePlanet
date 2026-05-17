package com.quokkalabs.strangeplanet.ui.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.quokkalabs.strangeplanet.bluetooth.BluetoothMergeManager
import com.quokkalabs.strangeplanet.bluetooth.BtHistory
import com.quokkalabs.strangeplanet.data.model.BluetoothLobbyState
import com.quokkalabs.strangeplanet.data.model.BtConnectionState
import com.quokkalabs.strangeplanet.data.model.BtDeviceInfo
import com.quokkalabs.strangeplanet.data.model.MatchResult
import com.quokkalabs.strangeplanet.data.model.MergeMode
import com.quokkalabs.strangeplanet.data.model.MergePhase
import com.quokkalabs.strangeplanet.data.model.MergeState
import com.quokkalabs.strangeplanet.data.model.MergeTier
import com.quokkalabs.strangeplanet.data.model.Orb
import com.quokkalabs.strangeplanet.data.model.nextOrbId
import com.quokkalabs.strangeplanet.domain.MergeEngine
import org.json.JSONArray
import org.json.JSONObject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class MergeViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs =
        application.getSharedPreferences("merge_prefs", Application.MODE_PRIVATE)

    private var engine: MergeEngine? = null
    private var loopJob: Job? = null
    private var highScore: Int = prefs.getInt(KEY_HIGH_SCORE, 0)

    private val _state = MutableStateFlow(MergeState(highScore = highScore))
    val state: StateFlow<MergeState> = _state.asStateFlow()

    @Volatile private var preDropSnapshot: MergeState? = null
    private val _canUndo = MutableStateFlow(false)
    val canUndo: StateFlow<Boolean> = _canUndo.asStateFlow()
    private val _undoPenalty = MutableStateFlow(0)
    val undoPenalty: StateFlow<Int> = _undoPenalty.asStateFlow()

    @Volatile
    private var spoutX: Float = 0f

    @Volatile
    private var dropRequested = false

    @Volatile
    private var paused = false

    // ── Competitive 1v1 (Bluetooth) ─────────────────────────────────────────
    private var btManager: BluetoothMergeManager? = null
    private var btPermissionsGranted = false

    private val _btState = MutableStateFlow(BluetoothLobbyState())
    val btState: StateFlow<BluetoothLobbyState> = _btState.asStateFlow()

    private val _btLobbyActive = MutableStateFlow(false)
    val btLobbyActive: StateFlow<Boolean> = _btLobbyActive.asStateFlow()

    private val _mode = MutableStateFlow(MergeMode.SOLO)
    val mode: StateFlow<MergeMode> = _mode.asStateFlow()

    private val _matchDuration = MutableStateFlow(120)
    val matchDuration: StateFlow<Int> = _matchDuration.asStateFlow()

    private val _timeRemaining = MutableStateFlow(0)
    val timeRemaining: StateFlow<Int> = _timeRemaining.asStateFlow()

    private val _opponentScore = MutableStateFlow(0)
    val opponentScore: StateFlow<Int> = _opponentScore.asStateFlow()

    private val _matchActive = MutableStateFlow(false)
    val matchActive: StateFlow<Boolean> = _matchActive.asStateFlow()

    private val _matchResult = MutableStateFlow<MatchResult?>(null)
    val matchResult: StateFlow<MatchResult?> = _matchResult.asStateFlow()

    @Volatile private var selfDone = false
    @Volatile private var opponentDone = false
    private var matchJob: Job? = null

    /** Freeze physics while a modal (exit / resume prompt) is showing. */
    fun setPaused(value: Boolean) {
        paused = value
    }

    private fun commitHighScore(score: Int) {
        if (score > highScore) {
            highScore = score
            prefs.edit().putInt(KEY_HIGH_SCORE, highScore).apply()
        }
    }

    /**
     * Called on every screen entry. The ViewModel is Activity-scoped and
     * outlives the screen, so this must be re-entrant: it (re)starts the
     * physics loop and, when a preserved snapshot exists, presents a clean
     * READY board so the resume prompt can appear instead of stale state.
     */
    fun initGame(screenWidth: Float, screenHeight: Float) {
        val firstInit = engine == null
        val eng = engine ?: MergeEngine(screenWidth, screenHeight).also { engine = it }

        if (firstInit || hasSavedSession()) {
            val initial = eng.createInitialState(highScore)
            spoutX = initial.spoutX
            _state.value = initial
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
                    val drop = dropRequested
                    dropRequested = false
                    if (drop && _state.value.canDrop) {
                        preDropSnapshot = _state.value
                        _undoPenalty.value = (_state.value.currentTier.ordinal + 1) * 5
                        _canUndo.value = true
                    }
                    val updated = e.update(_state.value, spoutX, drop)
                    _state.value = updated
                    if (updated.phase == MergePhase.GAME_OVER) {
                        commitHighScore(updated.score)
                        _state.value = updated.copy(highScore = highScore)
                        // VS: overflow locks your score; you're out until the
                        // opponent is also done. No restart.
                        if (_mode.value != MergeMode.SOLO && !selfDone) markSelfDone()
                    }
                } catch (t: Throwable) {
                    // A single bad frame must never permanently kill physics.
                    Log.e("MergeViewModel", "physics frame failed", t)
                }
            }
        }
    }

    /** Stop simulating when the screen leaves so the board can't run on. */
    fun stopLoop() {
        loopJob?.cancel()
        loopJob = null
    }

    /** Re-aim the spout (called continuously while dragging). */
    fun onAim(x: Float) {
        spoutX = x
    }

    /** Pointer released — drop in play, or start/restart otherwise. */
    fun onRelease() {
        when (_state.value.phase) {
            MergePhase.PLAYING -> dropRequested = true
            // VS matches are driven from the lobby; a tap must never start
            // or restart a competitive board (overflow = out, no restart).
            MergePhase.READY, MergePhase.GAME_OVER ->
                if (_mode.value == MergeMode.SOLO) startGame()
        }
    }

    private fun startGame() {
        val e = engine ?: return
        dropRequested = false
        preDropSnapshot = null
        _canUndo.value = false
        _undoPenalty.value = 0
        _state.value = e.startGame(_state.value.copy(highScore = highScore))
    }

    fun resetGame() {
        val e = engine ?: return
        commitHighScore(_state.value.score)
        dropRequested = false
        preDropSnapshot = null
        _canUndo.value = false
        _undoPenalty.value = 0
        val s = e.createInitialState(highScore)
        spoutX = s.spoutX
        _state.value = s
    }

    fun undo() {
        if (_mode.value != MergeMode.SOLO) return
        val snapshot = preDropSnapshot ?: return
        preDropSnapshot = null
        _canUndo.value = false
        val penalty = _undoPenalty.value
        _undoPenalty.value = 0
        _state.value = snapshot.copy(score = (snapshot.score - penalty).coerceAtLeast(0))
    }

    /**
     * Clear the two smallest tiers from the vessel. The cost equals the merge
     * value of everything swept (min [SWEEP_FLOOR]) so it never beats simply
     * merging them — it buys space, never points. Single-player only.
     */
    fun sweep() {
        if (_mode.value != MergeMode.SOLO) return
        val s = _state.value
        if (s.phase != MergePhase.PLAYING) return
        val swept = s.orbs.filter { it.tier in SWEEPABLE }
        if (swept.isEmpty()) return
        val cost = sweepCost(s.orbs)
        if (s.score < cost) return
        preDropSnapshot = null
        _canUndo.value = false
        _undoPenalty.value = 0
        _state.value = s.copy(
            orbs = s.orbs.filter { it.tier !in SWEEPABLE },
            score = s.score - cost,
        )
    }

    // ── Session preservation ────────────────────────────────────────────────

    fun hasSavedSession(): Boolean = prefs.contains(KEY_SESSION)

    fun saveSession() {
        val s = _state.value
        if (s.phase != MergePhase.PLAYING) return
        commitHighScore(s.score)
        val root = JSONObject().apply {
            put("score", s.score)
            put("currentTier", s.currentTier.name)
            put("nextTier", s.nextTier.name)
            put("spoutX", s.spoutX.toDouble())
        }
        val arr = JSONArray()
        for (o in s.orbs) {
            arr.put(JSONObject().apply {
                put("tier", o.tier.name)
                put("x", o.x.toDouble())
                put("y", o.y.toDouble())
                put("vx", o.vx.toDouble())
                put("vy", o.vy.toDouble())
                put("angle", o.angle.toDouble())
                put("omega", o.omega.toDouble())
            })
        }
        root.put("orbs", arr)
        prefs.edit().putString(KEY_SESSION, root.toString()).apply()
    }

    fun resumeSession() {
        val e = engine ?: return
        val raw = prefs.getString(KEY_SESSION, null) ?: return
        val restored = runCatching {
            val root = JSONObject(raw)
            val base = e.createInitialState(highScore)
            val arr = root.getJSONArray("orbs")
            val orbs = ArrayList<Orb>()
            for (i in 0 until arr.length()) {
                val j = arr.getJSONObject(i)
                orbs.add(
                    Orb(
                        id = nextOrbId(),
                        tier = MergeTier.valueOf(j.getString("tier")),
                        x = j.getDouble("x").toFloat(),
                        y = j.getDouble("y").toFloat(),
                        vx = j.getDouble("vx").toFloat(),
                        vy = j.getDouble("vy").toFloat(),
                        angle = j.optDouble("angle", 0.0).toFloat(),
                        omega = j.optDouble("omega", 0.0).toFloat(),
                    ),
                )
            }
            spoutX = root.getDouble("spoutX").toFloat()
            _state.value = base.copy(
                orbs = orbs,
                score = root.getInt("score"),
                currentTier = MergeTier.valueOf(root.getString("currentTier")),
                nextTier = MergeTier.valueOf(root.getString("nextTier")),
                spoutX = spoutX,
                phase = MergePhase.PLAYING,
                canDrop = true,
            )
        }.isSuccess
        // Only drop the snapshot if it actually restored; otherwise keep it
        // so a transient parse failure can't silently destroy progress.
        if (restored) discardSavedSession()
        startLoop()
    }

    fun discardSavedSession() {
        prefs.edit().remove(KEY_SESSION).apply()
    }

    // ── Bluetooth lobby ─────────────────────────────────────────────────────

    private var recentDevices: List<BtDeviceInfo> = emptyList()

    private fun ensureBt(): BluetoothMergeManager {
        btManager?.let { return it }
        val mgr = BluetoothMergeManager(getApplication())
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
        viewModelScope.launch { mgr.pairedDevices.collect { refreshBtState() } }
        viewModelScope.launch { mgr.discoveredDevices.collect { refreshBtState() } }
        viewModelScope.launch { mgr.connectedDeviceName.collect { refreshBtState() } }
        // Client: host pressed start — begin our local board.
        viewModelScope.launch {
            mgr.remoteStart.collect { dur ->
                if (dur != null) {
                    mgr.clearRemoteStart()
                    _matchDuration.value = dur
                    beginMatch(dur, MergeMode.BT_CLIENT)
                }
            }
        }
        // Either side: opponent score/done heartbeat.
        viewModelScope.launch {
            mgr.remoteScore.collect { ps ->
                if (ps != null) {
                    _opponentScore.value = ps.score
                    if (ps.done) opponentDone = true
                    checkMatchEnd()
                }
            }
        }
        // Opponent left / link dropped.
        viewModelScope.launch {
            mgr.remoteQuit.collect { quit -> if (quit) onOpponentLeft() }
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
        _mode.value = MergeMode.SOLO
        btDisconnect()
    }

    fun selectBtMode() {
        _btLobbyActive.value = true
        ensureBt()
        if (btPermissionsGranted) btManager?.loadPairedDevices()
    }

    fun updateBtPermissions(granted: Boolean) {
        btPermissionsGranted = granted
        if (granted) ensureBt().loadPairedDevices()
        refreshBtState()
    }

    fun btHost() = ensureBt().startHosting()
    fun btScan() = ensureBt().startScanning()
    fun btStopScan() {
        btManager?.stopScanning()
    }
    fun btConnect(address: String) = ensureBt().connectToDevice(address)

    fun btDisconnect() {
        matchJob?.cancel()
        matchJob = null
        _matchActive.value = false
        _matchResult.value = null
        btManager?.cleanup()
        refreshBtState()
    }

    fun setMatchDuration(seconds: Int) {
        _matchDuration.value = seconds.coerceIn(30, 600)
    }

    /** Host: tell the client to start, then begin our own board. */
    fun startBtMatch() {
        val mgr = btManager ?: return
        val dur = _matchDuration.value
        mgr.sendStart(dur)
        beginMatch(dur, MergeMode.BT_HOST)
    }

    // ── Match lifecycle ─────────────────────────────────────────────────────

    private fun beginMatch(durationSeconds: Int, m: MergeMode) {
        val e = engine ?: return
        _mode.value = m
        selfDone = false
        opponentDone = false
        _opponentScore.value = 0
        _matchResult.value = null
        _timeRemaining.value = durationSeconds
        _matchActive.value = true
        dropRequested = false
        preDropSnapshot = null
        _canUndo.value = false
        _state.value = e.startGame(_state.value.copy(highScore = highScore))

        matchJob?.cancel()
        matchJob = viewModelScope.launch {
            var msAccum = 0
            while (isActive && _matchActive.value) {
                delay(250)
                msAccum += 250
                // Heartbeat the opponent ~4x/sec.
                btManager?.sendScore(_state.value.score, selfDone)
                if (msAccum >= 1000) {
                    msAccum = 0
                    if (!selfDone) {
                        _timeRemaining.value = (_timeRemaining.value - 1).coerceAtLeast(0)
                        if (_timeRemaining.value == 0) markSelfDone()
                    }
                }
            }
        }
    }

    private fun markSelfDone() {
        if (selfDone) return
        selfDone = true
        commitHighScore(_state.value.score)
        btManager?.sendScore(_state.value.score, true)
        checkMatchEnd()
    }

    private fun checkMatchEnd() {
        if (!_matchActive.value) return
        if (selfDone && opponentDone) {
            val mine = _state.value.score
            val theirs = _opponentScore.value
            _matchResult.value = when {
                mine > theirs -> MatchResult.WIN
                mine < theirs -> MatchResult.LOSE
                else -> MatchResult.TIE
            }
            _matchActive.value = false
            matchJob?.cancel()
            matchJob = null
        }
    }

    private fun onOpponentLeft() {
        if (_matchActive.value) {
            // Abandonment forfeits the match to the player still present.
            opponentDone = true
            _matchResult.value = MatchResult.WIN
            _matchActive.value = false
            matchJob?.cancel()
            matchJob = null
        }
    }

    /**
     * Back pressed mid-match: an unconditional loss for the quitter no matter
     * the score. The opponent's [onOpponentLeft] turns the sent QUIT into a
     * win. The forfeiter sees the DEFEAT scoreboard, then departs.
     */
    fun forfeitMatch() {
        if (!_matchActive.value) return
        btManager?.sendQuit()
        matchJob?.cancel()
        matchJob = null
        _matchActive.value = false
        _matchResult.value = MatchResult.LOSE
    }

    /** Leave a finished/abandoned match and return to solo. */
    fun quitMatch() {
        btManager?.sendQuit()
        matchJob?.cancel()
        matchJob = null
        _matchActive.value = false
        _matchResult.value = null
        _mode.value = MergeMode.SOLO
        _btLobbyActive.value = false
        btManager?.cleanup()
        refreshBtState()
        resetGame()
    }

    override fun onCleared() {
        super.onCleared()
        matchJob?.cancel()
        btManager?.cleanup()
    }

    companion object {
        private const val KEY_HIGH_SCORE = "high_score"
        private const val KEY_SESSION = "saved_session"

        /** Tiers a sweep removes (the two smallest droppable spheres). */
        val SWEEPABLE = setOf(MergeTier.DUST_MOTE, MergeTier.PEBBLE)
        private const val SWEEP_FLOOR = 25

        /** Point cost to sweep the current board (min [SWEEP_FLOOR]). */
        fun sweepCost(orbs: List<Orb>): Int {
            val raw = orbs.filter { it.tier in SWEEPABLE }
                .sumOf { (it.tier.ordinal + 1) * 5 }
            return maxOf(SWEEP_FLOOR, raw)
        }
    }
}
