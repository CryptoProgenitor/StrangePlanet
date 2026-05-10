package com.quokkalabs.strangeplanet.ui.screen

import android.app.WallpaperManager
import android.content.ComponentName
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.quokkalabs.strangeplanet.data.FeatureToggle
import com.quokkalabs.strangeplanet.data.ToggleMode
import com.quokkalabs.strangeplanet.ui.theme.AlienPink
import com.quokkalabs.strangeplanet.ui.theme.CardPink
import com.quokkalabs.strangeplanet.ui.theme.CosmicGradient
import com.quokkalabs.strangeplanet.ui.theme.DeepNavy
import com.quokkalabs.strangeplanet.ui.theme.SoftPink
import com.quokkalabs.strangeplanet.ui.viewmodel.StrangePlanetViewModel
import com.quokkalabs.strangeplanet.wallpaper.StrangePlanetWallpaperService

@Composable
fun SettingsScreen(
    viewModel: StrangePlanetViewModel,
    onBack: () -> Unit,
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CosmicGradient)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
    ) {
        Text(
            "Creature Controls",
            color = DeepNavy,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
        )

        Spacer(modifier = Modifier.height(24.dp))

        SettingsModeSelector("Show Creatures", state.showCreatures) { viewModel.setShowCreatures(it) }
        SettingsModeSelector("Show Stars", state.showStars) { viewModel.setShowStars(it) }
        SettingsModeSelector("Speech Bubbles", state.showSpeechBubbles) { viewModel.setShowSpeechBubbles(it) }
        SettingsModeSelector("Sound Effects", state.soundEnabled) { viewModel.setSoundEnabled(it) }
        SettingsModeSelector("Voice (TTS)", state.ttsEnabled) { viewModel.setTtsEnabled(it) }

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
                value = state.creatureSizeScale,
                onValueChange = { viewModel.setCreatureSizeScale(it) },
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

        Spacer(modifier = Modifier.height(16.dp))

        TextButton(
            onClick = {
                viewModel.showPhysicsTuner()
                onBack()
            },
            modifier = Modifier
                .fillMaxWidth()
                .background(CardPink, RoundedCornerShape(12.dp)),
        ) {
            Text("Fundamental Adjustments", color = DeepNavy, fontWeight = FontWeight.Bold)
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            "Live Wallpaper",
            color = DeepNavy,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
        )

        Spacer(modifier = Modifier.height(12.dp))

        TextButton(
            onClick = {
                val intent = Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER).apply {
                    putExtra(
                        WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT,
                        ComponentName(context, StrangePlanetWallpaperService::class.java),
                    )
                }
                context.startActivity(intent)
            },
            modifier = Modifier
                .fillMaxWidth()
                .background(SoftPink, RoundedCornerShape(12.dp)),
        ) {
            Text("Set as Live Wallpaper", color = Color.White, fontWeight = FontWeight.Bold)
        }

        Spacer(modifier = Modifier.height(24.dp))

        TextButton(
            onClick = onBack,
            modifier = Modifier
                .fillMaxWidth()
                .background(CardPink.copy(alpha = 0.7f), RoundedCornerShape(12.dp)),
        ) {
            Text("Return to Creatures", color = DeepNavy, fontWeight = FontWeight.Bold)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsModeSelector(
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
