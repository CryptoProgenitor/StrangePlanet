package com.quokkalabs.strangeplanet.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.quokkalabs.strangeplanet.data.model.AsteroidGameState
import com.quokkalabs.strangeplanet.data.model.AsteroidInput
import com.quokkalabs.strangeplanet.data.model.AsteroidPhase
import com.quokkalabs.strangeplanet.data.model.AsteroidSettings
import com.quokkalabs.strangeplanet.domain.AsteroidEngine
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class AsteroidViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs =
        application.getSharedPreferences("asteroid_prefs", Application.MODE_PRIVATE)

    private var engine: AsteroidEngine? = null
    private var highScore: Int = prefs.getInt(KEY_HIGH_SCORE, 0)

    private val _state = MutableStateFlow(AsteroidGameState(highScore = highScore))
    val state: StateFlow<AsteroidGameState> = _state.asStateFlow()

    private val _settings = MutableStateFlow(
        AsteroidSettings(
            soundEnabled = prefs.getBoolean(KEY_SOUND, true),
            altLayout = prefs.getBoolean(KEY_ALT_LAYOUT, false),
        ),
    )
    val settings: StateFlow<AsteroidSettings> = _settings.asStateFlow()

    @Volatile
    private var input = AsteroidInput()

    private fun commitHighScore(score: Int) {
        if (score > highScore) {
            highScore = score
            prefs.edit().putInt(KEY_HIGH_SCORE, highScore).apply()
        }
    }

    fun initGame(screenWidth: Float, screenHeight: Float) {
        if (engine != null) return
        val eng = AsteroidEngine(screenWidth, screenHeight)
        engine = eng
        _state.value = eng.createInitialState(highScore = highScore)

        viewModelScope.launch {
            while (isActive) {
                delay(16)
                val e = engine ?: continue
                _state.value = e.update(_state.value, input)

                when (_state.value.phase) {
                    AsteroidPhase.LEVEL_CLEARED -> {
                        delay(1800)
                        val st = _state.value
                        _state.value = e.createInitialState(
                            level = st.level + 1,
                            score = st.score,
                            lives = st.lives,
                            highScore = highScore,
                            extraLifeAwarded = st.extraLifeAwarded,
                        ).copy(phase = AsteroidPhase.PLAYING)
                    }
                    AsteroidPhase.DYING -> {
                        delay(1600)
                        _state.value = e.respawnShip(_state.value)
                    }
                    AsteroidPhase.GAME_OVER -> {
                        commitHighScore(_state.value.score)
                        _state.value = _state.value.copy(highScore = highScore)
                    }
                    else -> {}
                }
            }
        }
    }

    fun setInput(transform: (AsteroidInput) -> AsteroidInput) {
        input = transform(input)
    }

    fun onTapToStart() {
        val e = engine ?: return
        when (_state.value.phase) {
            AsteroidPhase.READY -> _state.value = e.startGame(_state.value)
            AsteroidPhase.GAME_OVER -> {
                input = AsteroidInput()
                _state.value =
                    e.createInitialState(highScore = highScore)
                _state.value = e.startGame(_state.value)
            }
            AsteroidPhase.PAUSED -> _state.value = e.resumeGame(_state.value)
            else -> {}
        }
    }

    fun pauseGame() {
        val e = engine ?: return
        input = AsteroidInput()
        _state.value = e.pauseGame(_state.value)
    }

    fun resumeGame() {
        val e = engine ?: return
        _state.value = e.resumeGame(_state.value)
    }

    fun resetGame() {
        val e = engine ?: return
        commitHighScore(_state.value.score)
        input = AsteroidInput()
        _state.value = e.createInitialState(highScore = highScore)
    }

    // ── Session preservation (progress snapshot) ───────────────────────────

    fun hasSavedSession(): Boolean = prefs.getBoolean(KEY_SESSION, false)

    fun saveSession() {
        val s = _state.value
        if (s.phase != AsteroidPhase.PLAYING) return
        commitHighScore(s.score)
        prefs.edit()
            .putBoolean(KEY_SESSION, true)
            .putInt("$KEY_SESSION.score", s.score)
            .putInt("$KEY_SESSION.lives", s.lives)
            .putInt("$KEY_SESSION.level", s.level)
            .putInt("$KEY_SESSION.extra", s.extraLifeAwarded)
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
            extraLifeAwarded = prefs.getInt("$KEY_SESSION.extra", 0),
        )
        discardSavedSession()
    }

    fun discardSavedSession() {
        prefs.edit()
            .remove(KEY_SESSION)
            .remove("$KEY_SESSION.score")
            .remove("$KEY_SESSION.lives")
            .remove("$KEY_SESSION.level")
            .remove("$KEY_SESSION.extra")
            .apply()
    }

    fun setSoundEnabled(enabled: Boolean) {
        _settings.value = _settings.value.copy(soundEnabled = enabled)
        prefs.edit().putBoolean(KEY_SOUND, enabled).apply()
    }

    fun setAltLayout(enabled: Boolean) {
        _settings.value = _settings.value.copy(altLayout = enabled)
        prefs.edit().putBoolean(KEY_ALT_LAYOUT, enabled).apply()
    }

    private companion object {
        const val KEY_HIGH_SCORE = "high_score"
        const val KEY_SOUND = "sound_enabled"
        const val KEY_ALT_LAYOUT = "alt_layout"
        const val KEY_SESSION = "saved_session"
    }
}
