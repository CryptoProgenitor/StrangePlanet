package com.quokkalabs.strangeplanet.ui.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.quokkalabs.strangeplanet.data.model.MergePhase
import com.quokkalabs.strangeplanet.data.model.MergeState
import com.quokkalabs.strangeplanet.data.model.MergeTier
import com.quokkalabs.strangeplanet.data.model.Orb
import com.quokkalabs.strangeplanet.data.model.nextOrbId
import com.quokkalabs.strangeplanet.debug.GameAudit
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

    @Volatile
    private var spoutX: Float = 0f

    @Volatile
    private var dropRequested = false

    @Volatile
    private var paused = false

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
        GameAudit.attachMerge(viewModelScope, state, ::onAim, ::onRelease)
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
                    val updated = e.update(_state.value, spoutX, drop)
                    _state.value = updated
                    if (updated.phase == MergePhase.GAME_OVER) {
                        commitHighScore(updated.score)
                        _state.value = updated.copy(highScore = highScore)
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
            MergePhase.READY, MergePhase.GAME_OVER -> startGame()
        }
    }

    private fun startGame() {
        val e = engine ?: return
        dropRequested = false
        _state.value = e.startGame(_state.value.copy(highScore = highScore))
    }

    fun resetGame() {
        val e = engine ?: return
        commitHighScore(_state.value.score)
        dropRequested = false
        val s = e.createInitialState(highScore)
        spoutX = s.spoutX
        _state.value = s
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

    private companion object {
        const val KEY_HIGH_SCORE = "high_score"
        const val KEY_SESSION = "saved_session"
    }
}
