package com.quokkalabs.strangeplanet.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.quokkalabs.strangeplanet.data.model.PacAvatar
import com.quokkalabs.strangeplanet.data.model.PacDir
import com.quokkalabs.strangeplanet.data.model.PacGameState
import com.quokkalabs.strangeplanet.data.model.PacPhase
import com.quokkalabs.strangeplanet.data.model.PacSettings
import com.quokkalabs.strangeplanet.domain.MazeEngine
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class PacViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = application.getSharedPreferences("pac_prefs", Application.MODE_PRIVATE)

    private var engine: MazeEngine? = null

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

    private var screenW = 0f
    private var screenH = 0f

    private fun commitHighScore(score: Int) {
        if (score > highScore) {
            highScore = score
            prefs.edit().putInt(KEY_HIGH_SCORE, highScore).apply()
        }
    }

    fun initGame(screenWidth: Float, screenHeight: Float) {
        if (engine != null) return
        screenW = screenWidth
        screenH = screenHeight
        val eng = MazeEngine(screenWidth, screenHeight)
        engine = eng
        _state.value = eng.createInitialState(highScore = highScore)

        viewModelScope.launch {
            while (isActive) {
                delay(16)
                val e = engine ?: continue
                if (_pacSettings.value.joypadEnabled) {
                    val dir = joystickDir
                    val speed = joystickSpeed
                    _state.update { e.update(it, if (dir == PacDir.NONE) null else dir, speed) }
                } else {
                    val dir = pendingDir
                    pendingDir = null
                    _state.update { e.update(it, dir) }
                }

                when (_state.value.phase) {
                    PacPhase.LEVEL_CLEARED -> {
                        delay(1800)
                        val s = _state.value
                        _state.value = e.createInitialState(
                            level = s.level + 1,
                            score = s.score,
                            lives = s.lives,
                            highScore = highScore,
                        ).copy(phase = PacPhase.PLAYING)
                    }
                    PacPhase.DYING -> {
                        delay(2000)
                        val s = _state.value
                        if (s.lives <= 0) {
                            commitHighScore(s.score)
                            _state.update {
                                it.copy(phase = PacPhase.GAME_OVER, highScore = highScore)
                            }
                        } else {
                            _state.value = e.createInitialState(
                                level = s.level,
                                score = s.score,
                                lives = s.lives,
                                highScore = highScore,
                            ).copy(phase = PacPhase.PLAYING)
                        }
                    }
                    else -> {}
                }
            }
        }
    }

    /** A screen-wide swipe resolved to a cardinal direction → input queue. */
    fun onSwipe(dir: PacDir) {
        if (dir == PacDir.NONE) return
        pendingDir = dir
        // First swipe also kicks a READY game into motion.
        if (_state.value.phase == PacPhase.READY) onTapToStart()
    }

    fun onTapToStart() {
        val e = engine ?: return
        when (_state.value.phase) {
            PacPhase.READY -> _state.value = e.startGame(_state.value)
            PacPhase.GAME_OVER -> {
                _state.value = e.createInitialState(highScore = highScore)
                _state.value = e.startGame(_state.value)
            }
            else -> {}
        }
    }

    /**
     * Called continuously by the joystick composable. [dir] is the dominant-
     * axis direction (NONE in the dead zone), [speed] is 0..1 proportional to
     * displacement.
     */
    fun onJoystick(dir: PacDir, speed: Float) {
        joystickDir = dir
        joystickSpeed = speed
        if (speed > 0f && _state.value.phase == PacPhase.READY) onTapToStart()
    }

    fun resetGame() {
        val e = engine ?: return
        commitHighScore(_state.value.score)
        _state.value = e.createInitialState(highScore = highScore)
        pendingDir = null
        joystickDir = PacDir.NONE
        joystickSpeed = 0f
    }

    fun pauseGame() {
        val e = engine ?: return
        _state.update { e.pauseGame(it) }
    }

    fun resumeGame() {
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

    private companion object {
        const val KEY_HIGH_SCORE = "high_score"
        const val KEY_SOUND = "sound_enabled"
        const val KEY_SAYINGS = "show_sayings"
        const val KEY_AVATAR = "avatar"
        const val KEY_JOYPAD = "joypad_enabled"
    }
}
