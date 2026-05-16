package com.quokkalabs.strangeplanet.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.quokkalabs.strangeplanet.data.model.MergePhase
import com.quokkalabs.strangeplanet.data.model.MergeState
import com.quokkalabs.strangeplanet.data.model.MergeTier
import com.quokkalabs.strangeplanet.data.model.Orb
import com.quokkalabs.strangeplanet.data.model.nextOrbId
import com.quokkalabs.strangeplanet.domain.MergeEngine
import org.json.JSONArray
import org.json.JSONObject
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
    private var highScore: Int = prefs.getInt(KEY_HIGH_SCORE, 0)

    private val _state = MutableStateFlow(MergeState(highScore = highScore))
    val state: StateFlow<MergeState> = _state.asStateFlow()

    @Volatile
    private var spoutX: Float = 0f

    @Volatile
    private var dropRequested = false

    private fun commitHighScore(score: Int) {
        if (score > highScore) {
            highScore = score
            prefs.edit().putInt(KEY_HIGH_SCORE, highScore).apply()
        }
    }

    fun initGame(screenWidth: Float, screenHeight: Float) {
        if (engine != null) return
        val eng = MergeEngine(screenWidth, screenHeight)
        engine = eng
        val initial = eng.createInitialState(highScore)
        spoutX = initial.spoutX
        _state.value = initial

        viewModelScope.launch {
            while (isActive) {
                delay(16)
                val e = engine ?: continue
                val drop = dropRequested
                dropRequested = false
                val updated = e.update(_state.value, spoutX, drop)
                _state.value = updated
                if (updated.phase == MergePhase.GAME_OVER) {
                    commitHighScore(updated.score)
                    _state.value = updated.copy(highScore = highScore)
                }
            }
        }
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
            })
        }
        root.put("orbs", arr)
        prefs.edit().putString(KEY_SESSION, root.toString()).apply()
    }

    fun resumeSession() {
        val e = engine ?: return
        val raw = prefs.getString(KEY_SESSION, null) ?: return
        runCatching {
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
        }
        discardSavedSession()
    }

    fun discardSavedSession() {
        prefs.edit().remove(KEY_SESSION).apply()
    }

    private companion object {
        const val KEY_HIGH_SCORE = "high_score"
        const val KEY_SESSION = "saved_session"
    }
}
