package com.quokkalabs.strangeplanet.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.quokkalabs.strangeplanet.data.PhysicsSettings
import com.quokkalabs.strangeplanet.ui.theme.AlienPink
import com.quokkalabs.strangeplanet.ui.theme.DeepNavy
import com.quokkalabs.strangeplanet.ui.theme.SoftPink

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhysicsTunerSheet(
    physics: PhysicsSettings,
    onDismiss: () -> Unit,
    onBaseSpeedChange: (Float) -> Unit,
    onRestitutionChange: (Float) -> Unit,
    onOrbitDurationChange: (Float) -> Unit,
    onFlingMultChange: (Float) -> Unit,
    onLinearDragChange: (Float) -> Unit,
    onSpinDampingChange: (Float) -> Unit,
    onResetDefaults: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = DeepNavy.copy(alpha = 0.92f),
        scrimColor = Color.Black.copy(alpha = 0.15f),
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        dragHandle = {
            Spacer(
                modifier = Modifier
                    .padding(vertical = 12.dp)
                    .background(AlienPink.copy(alpha = 0.6f), RoundedCornerShape(2.dp))
                    .height(4.dp)
                    .fillMaxWidth(0.1f),
            )
        },
    ) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .navigationBarsPadding(),
        ) {
            Text(
                "Fundamental Adjustments",
                color = Color.White,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
            )

            Spacer(modifier = Modifier.height(16.dp))

            TunerSlider(
                label = "Purposeless Momentum",
                subtitle = "Ambient drift speed through the void",
                value = physics.baseSpeed,
                range = 0.5f..6f,
                minLabel = "Serene",
                maxLabel = "Chaotic",
                onValueChange = onBaseSpeedChange,
            )

            TunerSlider(
                label = "Impact Enthusiasm",
                subtitle = "Energy retained upon surface collision",
                value = physics.restitution,
                range = 0.3f..1.5f,
                minLabel = "Absorb",
                maxLabel = "Ricochet",
                onValueChange = onRestitutionChange,
            )

            TunerSlider(
                label = "Gravitational Obligation",
                subtitle = "Duration of involuntary planetary captivity",
                value = physics.orbitDurationSec,
                range = 2f..10f,
                minLabel = "Fleeting",
                maxLabel = "Prolonged",
                onValueChange = onOrbitDurationChange,
            )

            TunerSlider(
                label = "Ejection Vigor",
                subtitle = "Liberation force after orbital captivity",
                value = physics.flingMult,
                range = 1f..8f,
                minLabel = "Nudge",
                maxLabel = "Cannon",
                onValueChange = onFlingMultChange,
            )

            TunerSlider(
                label = "Void Resistance",
                subtitle = "How the void gradually saps momentum",
                value = physics.linearDrag,
                range = 0f..0.05f,
                minLabel = "Vacuum",
                maxLabel = "Syrup",
                onValueChange = onLinearDragChange,
            )

            TunerSlider(
                label = "Rotational Fatigue",
                subtitle = "Rate at which spinning enthusiasm diminishes",
                value = physics.spinDamping,
                range = 0f..0.05f,
                minLabel = "Tireless",
                maxLabel = "Dizzy",
                onValueChange = onSpinDampingChange,
            )

            Spacer(modifier = Modifier.height(16.dp))

            TextButton(
                onClick = onResetDefaults,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        AlienPink.copy(alpha = 0.2f),
                        RoundedCornerShape(12.dp),
                    ),
            ) {
                Text(
                    "Restore Default Physics",
                    color = AlienPink,
                    fontWeight = FontWeight.Bold,
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun TunerSlider(
    label: String,
    subtitle: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    minLabel: String,
    maxLabel: String,
    onValueChange: (Float) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp)
            .background(AlienPink.copy(alpha = 0.1f), RoundedCornerShape(12.dp))
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Text(
            label,
            color = Color.White,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            subtitle,
            color = AlienPink.copy(alpha = 0.7f),
            fontSize = 12.sp,
        )
        Spacer(modifier = Modifier.height(2.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                minLabel,
                color = Color.White.copy(alpha = 0.5f),
                fontSize = 11.sp,
            )
            Slider(
                value = value,
                onValueChange = onValueChange,
                valueRange = range,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 8.dp),
                colors = SliderDefaults.colors(
                    thumbColor = SoftPink,
                    activeTrackColor = SoftPink,
                    inactiveTrackColor = AlienPink.copy(alpha = 0.25f),
                ),
            )
            Text(
                maxLabel,
                color = Color.White.copy(alpha = 0.5f),
                fontSize = 11.sp,
            )
        }
    }
}
