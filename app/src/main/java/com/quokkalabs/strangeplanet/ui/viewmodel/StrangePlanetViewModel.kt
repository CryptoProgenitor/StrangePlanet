package com.quokkalabs.strangeplanet.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.quokkalabs.strangeplanet.StrangePlanetApp
import com.quokkalabs.strangeplanet.data.FeatureToggle
import com.quokkalabs.strangeplanet.data.PhysicsSettings
import com.quokkalabs.strangeplanet.data.ToggleMode
import com.quokkalabs.strangeplanet.data.WallpaperPrefsKeys
import com.quokkalabs.strangeplanet.data.model.CreatureDefaults
import com.quokkalabs.strangeplanet.data.model.CreatureState
import com.quokkalabs.strangeplanet.data.model.CreatureType
import com.quokkalabs.strangeplanet.data.resetPhysicsDefaults
import com.quokkalabs.strangeplanet.data.setCreatureSizeScale
import com.quokkalabs.strangeplanet.data.setFeatureToggle
import com.quokkalabs.strangeplanet.data.setPhysicsParam
import com.quokkalabs.strangeplanet.data.wallpaperDataStore
import com.quokkalabs.strangeplanet.data.wallpaperSettings
import com.quokkalabs.strangeplanet.domain.PhysicsEngine
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class UiState(
    val creatures: List<CreatureState> = emptyList(),
    val activeSayings: Map<CreatureType, String> = emptyMap(),
    val ttsHighlight: Map<CreatureType, IntRange> = emptyMap(),
    val showCreatures: FeatureToggle = FeatureToggle(),
    val showStars: FeatureToggle = FeatureToggle(),
    val showSpeechBubbles: FeatureToggle = FeatureToggle(),
    val soundEnabled: FeatureToggle = FeatureToggle(),
    val ttsEnabled: FeatureToggle = FeatureToggle(),
    val creatureSizeScale: Float = 1f,
    val planetGlowBoost: Float = 0f,
    val creaturesBehindPlanet: Set<CreatureType> = emptySet(),
    val physics: PhysicsSettings = PhysicsSettings(),
    val showPhysicsTuner: Boolean = false,
)

class StrangePlanetViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as StrangePlanetApp
    private val soundManager = app.soundManager
    private val ttsManager = app.ttsManager
    private val sayingsRepo = app.sayingsRepository
    private val dayContextResolver = app.dayContextResolver

    private val dataStore = application.wallpaperDataStore
    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private var physicsEngine: PhysicsEngine? = null

    init {
        viewModelScope.launch {
            dataStore.wallpaperSettings().collect { s ->
                _uiState.update {
                    it.copy(
                        showCreatures = s.showCreatures,
                        showStars = s.showStars,
                        showSpeechBubbles = s.showSpeechBubbles,
                        soundEnabled = s.soundEnabled,
                        ttsEnabled = s.ttsEnabled,
                        creatureSizeScale = s.creatureSizeScale,
                        physics = s.physics,
                    )
                }
                syncEngineParams(s.physics)
            }
        }
        viewModelScope.launch {
            ttsManager.progress.collect { progress ->
                _uiState.update { state ->
                    state.copy(
                        ttsHighlight = if (progress != null) {
                            mapOf(progress.creatureType to (progress.start until progress.end))
                        } else {
                            emptyMap()
                        },
                    )
                }
            }
        }
    }

    private fun syncEngineParams(p: PhysicsSettings) {
        val engine = physicsEngine ?: return
        engine.baseSpeed = p.baseSpeed
        engine.restitution = p.restitution
        engine.orbitDurationMs = (p.orbitDurationSec * 1000).toLong()
        engine.flingSpeedMult = p.flingMult
        engine.linearDrag = p.linearDrag
        engine.spinDamping = p.spinDamping
    }

    fun initCreatures(screenWidth: Float, screenHeight: Float) {
        if (_uiState.value.creatures.isNotEmpty()) return

        physicsEngine = PhysicsEngine(screenWidth, screenHeight)
        syncEngineParams(_uiState.value.physics)
        _uiState.update { it.copy(creatures = CreatureDefaults.create(screenWidth, screenHeight)) }

        viewModelScope.launch {
            while (isActive) {
                delay(16)
                val engine = physicsEngine ?: continue
                _uiState.update { state ->
                    val glowBoost = if (engine.isOrbiting) {
                        val p = engine.orbitProgress
                        if (p < 0.1f) p / 0.1f * 0.5f
                        else if (p < 0.85f) 0.35f
                        else (1f - p) / 0.15f * 0.35f
                    } else 0f

                    val updatedCreatures = engine.update(state.creatures)
                    val behind = if (engine.isOrbiting) {
                        updatedCreatures.filter { engine.isCreatureBehindPlanet(it) }
                            .map { it.type }.toSet()
                    } else emptySet()

                    state.copy(
                        creatures = updatedCreatures,
                        planetGlowBoost = glowBoost,
                        creaturesBehindPlanet = behind,
                    )
                }
            }
        }
    }

    fun onCreatureTapped(type: CreatureType) {
        val state = _uiState.value

        if (state.soundEnabled.app) {
            soundManager.play(type)
        }

        if (type == CreatureType.ROLLSUCK) {
            val engine = physicsEngine
            if (engine != null && !engine.isOrbiting && !engine.isChasing) {
                engine.startChase()
                if (state.showSpeechBubbles.app) {
                    val terrified = terrifiedSocksSayings.random()
                    _uiState.update {
                        it.copy(activeSayings = it.activeSayings + (CreatureType.SOCKS to terrified))
                    }
                    viewModelScope.launch {
                        delay(4000)
                        _uiState.update {
                            it.copy(activeSayings = it.activeSayings - CreatureType.SOCKS)
                        }
                    }
                }
            }
        }

        if (state.showSpeechBubbles.app) {
            val context = dayContextResolver.resolve()
            val saying = sayingsRepo.getSaying(type, context.timeOfDay, context.dayType)

            _uiState.update {
                it.copy(activeSayings = it.activeSayings + (type to saying))
            }

            if (state.ttsEnabled.app) {
                ttsManager.speak(saying, type)
            }

            viewModelScope.launch {
                delay(4000)
                _uiState.update {
                    it.copy(activeSayings = it.activeSayings - type)
                }
            }
        }
    }

    companion object {
        private val terrifiedSocksSayings = listOf(
            "NO! THE DEBRIS CONSUMER APPROACHES!",
            "MY FIBRES ARE NOT DEBRIS! I AM A GARMENT!",
            "IT CANNOT DISTINGUISH BETWEEN FLOOR DUST AND FOOT FABRIC!",
            "I REQUIRE IMMEDIATE ELEVATION FROM THE FLOOR!",
            "THE ROTATING BRISTLE CYLINDER! IT HUNGERS!",
            "I HAVE WITNESSED IT CONSUME MY COMPANION! FLEE!",
        )
    }

    fun setShowCreatures(mode: ToggleMode) {
        viewModelScope.launch {
            dataStore.setFeatureToggle(
                WallpaperPrefsKeys.SHOW_CREATURES_APP,
                WallpaperPrefsKeys.SHOW_CREATURES_LWP,
                mode,
            )
        }
    }

    fun setShowStars(mode: ToggleMode) {
        viewModelScope.launch {
            dataStore.setFeatureToggle(
                WallpaperPrefsKeys.SHOW_STARS_APP,
                WallpaperPrefsKeys.SHOW_STARS_LWP,
                mode,
            )
        }
    }

    fun setShowSpeechBubbles(mode: ToggleMode) {
        viewModelScope.launch {
            dataStore.setFeatureToggle(
                WallpaperPrefsKeys.SHOW_SPEECH_BUBBLES_APP,
                WallpaperPrefsKeys.SHOW_SPEECH_BUBBLES_LWP,
                mode,
            )
        }
    }

    fun setSoundEnabled(mode: ToggleMode) {
        viewModelScope.launch {
            dataStore.setFeatureToggle(
                WallpaperPrefsKeys.SOUND_ENABLED_APP,
                WallpaperPrefsKeys.SOUND_ENABLED_LWP,
                mode,
            )
        }
    }

    fun setTtsEnabled(mode: ToggleMode) {
        viewModelScope.launch {
            dataStore.setFeatureToggle(
                WallpaperPrefsKeys.TTS_ENABLED_APP,
                WallpaperPrefsKeys.TTS_ENABLED_LWP,
                mode,
            )
        }
    }

    fun setCreatureSizeScale(scale: Float) {
        viewModelScope.launch { dataStore.setCreatureSizeScale(scale) }
    }

    fun setBaseSpeed(value: Float) {
        viewModelScope.launch { dataStore.setPhysicsParam(WallpaperPrefsKeys.PHYSICS_BASE_SPEED, value) }
    }

    fun setRestitution(value: Float) {
        viewModelScope.launch { dataStore.setPhysicsParam(WallpaperPrefsKeys.PHYSICS_RESTITUTION, value) }
    }

    fun setOrbitDuration(value: Float) {
        viewModelScope.launch { dataStore.setPhysicsParam(WallpaperPrefsKeys.PHYSICS_ORBIT_DURATION, value) }
    }

    fun setFlingMult(value: Float) {
        viewModelScope.launch { dataStore.setPhysicsParam(WallpaperPrefsKeys.PHYSICS_FLING_MULT, value) }
    }

    fun setLinearDrag(value: Float) {
        viewModelScope.launch { dataStore.setPhysicsParam(WallpaperPrefsKeys.PHYSICS_LINEAR_DRAG, value) }
    }

    fun setSpinDamping(value: Float) {
        viewModelScope.launch { dataStore.setPhysicsParam(WallpaperPrefsKeys.PHYSICS_SPIN_DAMPING, value) }
    }

    fun resetPhysics() {
        viewModelScope.launch { dataStore.resetPhysicsDefaults() }
    }

    fun showPhysicsTuner() {
        _uiState.update { it.copy(showPhysicsTuner = true) }
    }

    fun hidePhysicsTuner() {
        _uiState.update { it.copy(showPhysicsTuner = false) }
    }

    fun onPlanetTapped() {
        val engine = physicsEngine ?: return
        if (engine.isOrbiting) return
        engine.startOrbit(_uiState.value.creatures)
    }

    override fun onCleared() {
        super.onCleared()
        soundManager.release()
        ttsManager.shutdown()
    }
}
