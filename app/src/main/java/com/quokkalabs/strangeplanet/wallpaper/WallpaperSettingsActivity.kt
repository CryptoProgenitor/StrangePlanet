package com.quokkalabs.strangeplanet.wallpaper

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.quokkalabs.strangeplanet.data.FeatureToggle
import com.quokkalabs.strangeplanet.data.ToggleMode
import com.quokkalabs.strangeplanet.data.WallpaperPrefsKeys
import com.quokkalabs.strangeplanet.data.WallpaperSettings
import com.quokkalabs.strangeplanet.data.setCreatureSizeScale
import com.quokkalabs.strangeplanet.data.setFeatureToggle
import com.quokkalabs.strangeplanet.data.wallpaperDataStore
import com.quokkalabs.strangeplanet.data.wallpaperSettings
import com.quokkalabs.strangeplanet.ui.theme.AlienPink
import com.quokkalabs.strangeplanet.ui.theme.CardPink
import com.quokkalabs.strangeplanet.ui.theme.CosmicGradient
import com.quokkalabs.strangeplanet.ui.theme.DeepNavy
import com.quokkalabs.strangeplanet.ui.theme.SoftPink
import com.quokkalabs.strangeplanet.ui.theme.StrangePlanetTheme
import kotlinx.coroutines.launch

class WallpaperSettingsActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            StrangePlanetTheme {
                val settings by wallpaperDataStore.wallpaperSettings()
                    .collectAsState(initial = WallpaperSettings())
                val scope = rememberCoroutineScope()
                val store = wallpaperDataStore

                WallpaperSettingsScreen(
                    settings = settings,
                    onSetCreatures = { scope.launch { store.setFeatureToggle(WallpaperPrefsKeys.SHOW_CREATURES_APP, WallpaperPrefsKeys.SHOW_CREATURES_LWP, it) } },
                    onSetStars = { scope.launch { store.setFeatureToggle(WallpaperPrefsKeys.SHOW_STARS_APP, WallpaperPrefsKeys.SHOW_STARS_LWP, it) } },
                    onSetBubbles = { scope.launch { store.setFeatureToggle(WallpaperPrefsKeys.SHOW_SPEECH_BUBBLES_APP, WallpaperPrefsKeys.SHOW_SPEECH_BUBBLES_LWP, it) } },
                    onSetSound = { scope.launch { store.setFeatureToggle(WallpaperPrefsKeys.SOUND_ENABLED_APP, WallpaperPrefsKeys.SOUND_ENABLED_LWP, it) } },
                    onSetTts = { scope.launch { store.setFeatureToggle(WallpaperPrefsKeys.TTS_ENABLED_APP, WallpaperPrefsKeys.TTS_ENABLED_LWP, it) } },
                    onSizeScaleChange = { scope.launch { store.setCreatureSizeScale(it) } },
                    onBack = { finish() },
                )
            }
        }
    }
}

@Composable
private fun WallpaperSettingsScreen(
    settings: WallpaperSettings,
    onSetCreatures: (ToggleMode) -> Unit,
    onSetStars: (ToggleMode) -> Unit,
    onSetBubbles: (ToggleMode) -> Unit,
    onSetSound: (ToggleMode) -> Unit,
    onSetTts: (ToggleMode) -> Unit,
    onSizeScaleChange: (Float) -> Unit,
    onBack: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CosmicGradient)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(24.dp),
    ) {
        Text(
            "Wallpaper Settings",
            color = DeepNavy,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
        )

        Spacer(modifier = Modifier.height(24.dp))

        WpModeSelector("Show Creatures", settings.showCreatures, onSetCreatures)
        WpModeSelector("Show Stars", settings.showStars, onSetStars)
        WpModeSelector("Speech Bubbles", settings.showSpeechBubbles, onSetBubbles)
        WpModeSelector("Sound Effects", settings.soundEnabled, onSetSound)
        WpModeSelector("Voice (TTS)", settings.ttsEnabled, onSetTts)

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            "Creature Size",
            color = DeepNavy,
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 4.dp),
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("S", color = DeepNavy, fontSize = 12.sp)
            Slider(
                value = settings.creatureSizeScale,
                onValueChange = onSizeScaleChange,
                valueRange = 0.5f..2f,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 8.dp),
                colors = SliderDefaults.colors(
                    thumbColor = SoftPink,
                    activeTrackColor = SoftPink,
                    inactiveTrackColor = AlienPink.copy(alpha = 0.4f),
                ),
            )
            Text("L", color = DeepNavy, fontSize = 12.sp)
        }

        Spacer(modifier = Modifier.weight(1f))

        TextButton(
            onClick = onBack,
            modifier = Modifier
                .fillMaxWidth()
                .background(CardPink.copy(alpha = 0.7f), RoundedCornerShape(12.dp)),
        ) {
            Text("Done", color = DeepNavy, fontWeight = FontWeight.Bold)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WpModeSelector(
    label: String,
    toggle: FeatureToggle,
    onModeChange: (ToggleMode) -> Unit,
) {
    val currentMode = ToggleMode.from(toggle)
    val modes = ToggleMode.entries

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .background(AlienPink.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Text(
            label,
            color = DeepNavy,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(bottom = 6.dp),
        )
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            modes.forEachIndexed { index, mode ->
                SegmentedButton(
                    selected = currentMode == mode,
                    onClick = { onModeChange(mode) },
                    shape = SegmentedButtonDefaults.itemShape(index, modes.size),
                    colors = SegmentedButtonDefaults.colors(
                        activeContainerColor = SoftPink,
                        activeContentColor = Color.White,
                        inactiveContainerColor = Color.White.copy(alpha = 0.5f),
                        inactiveContentColor = DeepNavy,
                        activeBorderColor = SoftPink,
                        inactiveBorderColor = AlienPink.copy(alpha = 0.6f),
                    ),
                    icon = {},
                ) {
                    Text(mode.label, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                }
            }
        }
    }
}
