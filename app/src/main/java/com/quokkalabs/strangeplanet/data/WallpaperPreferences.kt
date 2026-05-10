package com.quokkalabs.strangeplanet.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.wallpaperDataStore: DataStore<Preferences> by preferencesDataStore(name = "wallpaper_settings")

data class FeatureToggle(
    val app: Boolean = true,
    val lwp: Boolean = true,
)

enum class ToggleMode(val label: String) {
    OFF("Off"),
    LWP("LWP"),
    APP("App"),
    BOTH("Both");

    companion object {
        fun from(toggle: FeatureToggle): ToggleMode = when {
            toggle.app && toggle.lwp -> BOTH
            toggle.app -> APP
            toggle.lwp -> LWP
            else -> OFF
        }
    }

    fun toToggle(): FeatureToggle = when (this) {
        OFF -> FeatureToggle(app = false, lwp = false)
        LWP -> FeatureToggle(app = false, lwp = true)
        APP -> FeatureToggle(app = true, lwp = false)
        BOTH -> FeatureToggle(app = true, lwp = true)
    }
}

data class WallpaperSettings(
    val showCreatures: FeatureToggle = FeatureToggle(),
    val showStars: FeatureToggle = FeatureToggle(),
    val showSpeechBubbles: FeatureToggle = FeatureToggle(),
    val soundEnabled: FeatureToggle = FeatureToggle(),
    val ttsEnabled: FeatureToggle = FeatureToggle(),
    val creatureSizeScale: Float = 1f,
    val physics: PhysicsSettings = PhysicsSettings(),
)

data class PhysicsSettings(
    val baseSpeed: Float = 2.5f,
    val restitution: Float = 1f,
    val orbitDurationSec: Float = 5f,
    val flingMult: Float = 4f,
    val linearDrag: Float = 0f,
    val spinDamping: Float = 0f,
)

object WallpaperPrefsKeys {
    val SHOW_CREATURES_APP = booleanPreferencesKey("show_creatures_app")
    val SHOW_CREATURES_LWP = booleanPreferencesKey("show_creatures_lwp")
    val SHOW_STARS_APP = booleanPreferencesKey("show_stars_app")
    val SHOW_STARS_LWP = booleanPreferencesKey("show_stars_lwp")
    val SHOW_SPEECH_BUBBLES_APP = booleanPreferencesKey("show_speech_bubbles_app")
    val SHOW_SPEECH_BUBBLES_LWP = booleanPreferencesKey("show_speech_bubbles_lwp")
    val SOUND_ENABLED_APP = booleanPreferencesKey("sound_enabled_app")
    val SOUND_ENABLED_LWP = booleanPreferencesKey("sound_enabled_lwp")
    val TTS_ENABLED_APP = booleanPreferencesKey("tts_enabled_app")
    val TTS_ENABLED_LWP = booleanPreferencesKey("tts_enabled_lwp")
    val CREATURE_SIZE_SCALE = floatPreferencesKey("creature_size_scale")
    val PHYSICS_BASE_SPEED = floatPreferencesKey("physics_base_speed")
    val PHYSICS_RESTITUTION = floatPreferencesKey("physics_restitution")
    val PHYSICS_ORBIT_DURATION = floatPreferencesKey("physics_orbit_duration")
    val PHYSICS_FLING_MULT = floatPreferencesKey("physics_fling_mult")
    val PHYSICS_LINEAR_DRAG = floatPreferencesKey("physics_linear_drag")
    val PHYSICS_SPIN_DAMPING = floatPreferencesKey("physics_spin_damping")
}

fun DataStore<Preferences>.wallpaperSettings(): Flow<WallpaperSettings> =
    data.map { prefs ->
        WallpaperSettings(
            showCreatures = FeatureToggle(
                app = prefs[WallpaperPrefsKeys.SHOW_CREATURES_APP] ?: true,
                lwp = prefs[WallpaperPrefsKeys.SHOW_CREATURES_LWP] ?: true,
            ),
            showStars = FeatureToggle(
                app = prefs[WallpaperPrefsKeys.SHOW_STARS_APP] ?: true,
                lwp = prefs[WallpaperPrefsKeys.SHOW_STARS_LWP] ?: true,
            ),
            showSpeechBubbles = FeatureToggle(
                app = prefs[WallpaperPrefsKeys.SHOW_SPEECH_BUBBLES_APP] ?: true,
                lwp = prefs[WallpaperPrefsKeys.SHOW_SPEECH_BUBBLES_LWP] ?: true,
            ),
            soundEnabled = FeatureToggle(
                app = prefs[WallpaperPrefsKeys.SOUND_ENABLED_APP] ?: true,
                lwp = prefs[WallpaperPrefsKeys.SOUND_ENABLED_LWP] ?: true,
            ),
            ttsEnabled = FeatureToggle(
                app = prefs[WallpaperPrefsKeys.TTS_ENABLED_APP] ?: true,
                lwp = prefs[WallpaperPrefsKeys.TTS_ENABLED_LWP] ?: true,
            ),
            creatureSizeScale = prefs[WallpaperPrefsKeys.CREATURE_SIZE_SCALE] ?: 1f,
            physics = PhysicsSettings(
                baseSpeed = prefs[WallpaperPrefsKeys.PHYSICS_BASE_SPEED] ?: 2.5f,
                restitution = prefs[WallpaperPrefsKeys.PHYSICS_RESTITUTION] ?: 1f,
                orbitDurationSec = prefs[WallpaperPrefsKeys.PHYSICS_ORBIT_DURATION] ?: 5f,
                flingMult = prefs[WallpaperPrefsKeys.PHYSICS_FLING_MULT] ?: 4f,
                linearDrag = prefs[WallpaperPrefsKeys.PHYSICS_LINEAR_DRAG] ?: 0f,
                spinDamping = prefs[WallpaperPrefsKeys.PHYSICS_SPIN_DAMPING] ?: 0f,
            ),
        )
    }

suspend fun DataStore<Preferences>.setFeatureToggle(
    appKey: Preferences.Key<Boolean>,
    lwpKey: Preferences.Key<Boolean>,
    mode: ToggleMode,
) {
    val toggle = mode.toToggle()
    edit { prefs ->
        prefs[appKey] = toggle.app
        prefs[lwpKey] = toggle.lwp
    }
}

suspend fun DataStore<Preferences>.setCreatureSizeScale(scale: Float) {
    edit { prefs -> prefs[WallpaperPrefsKeys.CREATURE_SIZE_SCALE] = scale.coerceIn(0.5f, 2f) }
}

suspend fun DataStore<Preferences>.setPhysicsParam(key: Preferences.Key<Float>, value: Float) {
    edit { prefs -> prefs[key] = value }
}

suspend fun DataStore<Preferences>.resetPhysicsDefaults() {
    val defaults = PhysicsSettings()
    edit { prefs ->
        prefs[WallpaperPrefsKeys.PHYSICS_BASE_SPEED] = defaults.baseSpeed
        prefs[WallpaperPrefsKeys.PHYSICS_RESTITUTION] = defaults.restitution
        prefs[WallpaperPrefsKeys.PHYSICS_ORBIT_DURATION] = defaults.orbitDurationSec
        prefs[WallpaperPrefsKeys.PHYSICS_FLING_MULT] = defaults.flingMult
        prefs[WallpaperPrefsKeys.PHYSICS_LINEAR_DRAG] = defaults.linearDrag
        prefs[WallpaperPrefsKeys.PHYSICS_SPIN_DAMPING] = defaults.spinDamping
    }
}
