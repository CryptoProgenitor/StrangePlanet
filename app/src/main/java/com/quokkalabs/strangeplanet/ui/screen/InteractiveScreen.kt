package com.quokkalabs.strangeplanet.ui.screen

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.quokkalabs.strangeplanet.ui.components.CosmicBackground
import com.quokkalabs.strangeplanet.ui.components.CreatureSprite
import com.quokkalabs.strangeplanet.ui.components.PhysicsTunerSheet
import com.quokkalabs.strangeplanet.ui.components.Planet
import com.quokkalabs.strangeplanet.ui.components.SpeechBubble
import com.quokkalabs.strangeplanet.ui.theme.DeepNavy
import com.quokkalabs.strangeplanet.ui.theme.AlienPink
import com.quokkalabs.strangeplanet.ui.viewmodel.StrangePlanetViewModel
import com.quokkalabs.strangeplanet.ui.viewmodel.UiState
import kotlin.math.roundToInt

@Composable
fun InteractiveScreen(
    viewModel: StrangePlanetViewModel,
    onOpenSettings: () -> Unit = {},
    onOpenGame: () -> Unit = {},
) {
    // Creature positions change every display frame, but they are only read in the
    // layout/draw lambdas below. Everything else reads this copy with the motion
    // stripped out, so the screen recomposes when a setting, saying or orbit changes
    // instead of 120 times a second.
    val frame = viewModel.uiState.collectAsState()
    val state by remember { derivedStateOf { frame.value.withoutMotion() } }
    val density = LocalDensity.current
    val navBarPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    CosmicBackground(showStars = state.showStars.app) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val screenWidth = with(density) { maxWidth.toPx() }
            val screenHeight = with(density) { maxHeight.toPx() }

            LaunchedEffect(screenWidth, screenHeight) {
                viewModel.initCreatures(screenWidth, screenHeight)
                // Step physics once per display frame (vsync-aligned), using the real
                // frame time so speed is the same at 60/90/120 Hz.
                var lastFrameNanos = withFrameNanos { it }
                while (true) {
                    withFrameNanos { frameNanos ->
                        viewModel.onFrame((frameNanos - lastFrameNanos) / 1_000_000_000f)
                        lastFrameNanos = frameNanos
                    }
                }
            }

            @Composable
            fun Creatures(behindPlanet: Boolean) {
                state.creatures
                    .filter { (it.type in state.creaturesBehindPlanet) == behindPlanet }
                    .forEach { creature ->
                        key(creature.type) {
                            CreatureSprite(
                                creature = creature,
                                sizeScale = state.creatureSizeScale,
                                current = {
                                    frame.value.creatures.firstOrNull { it.type == creature.type } ?: creature
                                },
                                onTap = { viewModel.onCreatureTapped(creature.type) },
                            )
                        }
                    }
            }

            if (state.showCreatures.app) Creatures(behindPlanet = true)

            Planet(
                modifier = Modifier.align(Alignment.Center),
                size = 180.dp,
                glowBoost = state.planetGlowBoost,
                onTap = { viewModel.onPlanetTapped() },
            )

            if (state.showCreatures.app) Creatures(behindPlanet = false)

            if (state.showSpeechBubbles.app && state.showCreatures.app && state.activeSayings.isNotEmpty()) {
                SpeechBubbles(frame, screenWidth)
            }

            // Game button
            FloatingActionButton(
                onClick = onOpenGame,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(bottom = navBarPadding + 16.dp, start = 16.dp)
                    .size(48.dp),
                shape = CircleShape,
                containerColor = AlienPink.copy(alpha = 0.7f),
                contentColor = DeepNavy,
            ) {
                Text("🎮", fontSize = 20.sp)
            }

            // Settings button
            FloatingActionButton(
                onClick = onOpenSettings,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(bottom = navBarPadding + 16.dp, end = 16.dp)
                    .size(48.dp),
                shape = CircleShape,
                containerColor = AlienPink.copy(alpha = 0.7f),
                contentColor = DeepNavy,
            ) {
                Text("⚙", fontSize = 22.sp)
            }
        }
    }

    if (state.showPhysicsTuner) {
        PhysicsTunerSheet(
            physics = state.physics,
            onDismiss = { viewModel.hidePhysicsTuner() },
            onBaseSpeedChange = { viewModel.setBaseSpeed(it) },
            onRestitutionChange = { viewModel.setRestitution(it) },
            onOrbitDurationChange = { viewModel.setOrbitDuration(it) },
            onFlingMultChange = { viewModel.setFlingMult(it) },
            onLinearDragChange = { viewModel.setLinearDrag(it) },
            onSpinDampingChange = { viewModel.setSpinDamping(it) },
            onResetDefaults = { viewModel.resetPhysics() },
        )
    }
}

/** [UiState] without per-frame creature motion, for everything that isn't drawn per frame. */
private fun UiState.withoutMotion() = copy(
    creatures = creatures.map {
        it.copy(x = 0f, y = 0f, vx = 0f, vy = 0f, rotation = 0f, angularVelocity = 0f)
    },
)

/**
 * Bubbles follow their creature, so this reads the live frame and recomposes every
 * frame — but only while a bubble is showing, and only this part of the screen.
 */
@Composable
private fun SpeechBubbles(frame: State<UiState>, screenWidth: Float) {
    val state = frame.value
    val density = LocalDensity.current
    val bubbleMaxHalfWidthPx = 160f * density.density
    val marginPx = 16f * density.density
    val gapAboveCreaturePx = 12f * density.density
    val pointerHeightPx = 12f * density.density

    state.activeSayings.forEach { (type, saying) ->
        val creature = state.creatures.find { it.type == type } ?: return@forEach
        if (creature.type in state.creaturesBehindPlanet) return@forEach

        val creatureSizePx = creature.size * state.creatureSizeScale * density.density

        val anchorX = creature.x
            .coerceIn(
                bubbleMaxHalfWidthPx + marginPx,
                screenWidth - bubbleMaxHalfWidthPx - marginPx,
            )
        val anchorY =
            creature.y - creatureSizePx / 2 - gapAboveCreaturePx - pointerHeightPx

        val pointerOffsetPx = creature.x - anchorX

        SpeechBubble(
            text = saying,
            visible = true,
            spokenRange = state.ttsHighlight[type],
            pointerOffsetDp = with(density) { pointerOffsetPx.toDp() },
            modifier = Modifier
                .offset { IntOffset(anchorX.roundToInt(), anchorY.roundToInt()) }
                .layout { measurable, constraints ->
                    val placeable = measurable.measure(constraints)
                    layout(0, 0) {
                        placeable.place(
                            -placeable.width / 2,
                            -placeable.height,
                        )
                    }
                },
        )
    }
}
