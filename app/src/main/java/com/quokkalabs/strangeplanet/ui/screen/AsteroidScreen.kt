package com.quokkalabs.strangeplanet.ui.screen

import android.graphics.BitmapFactory
import android.media.AudioManager
import android.media.ToneGenerator
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.quokkalabs.strangeplanet.R
import com.quokkalabs.strangeplanet.data.model.AsteroidPhase
import com.quokkalabs.strangeplanet.data.model.RockSize
import com.quokkalabs.strangeplanet.ui.components.CosmicBackground
import com.quokkalabs.strangeplanet.ui.theme.AlienPink
import com.quokkalabs.strangeplanet.ui.theme.CosmicBlue
import com.quokkalabs.strangeplanet.ui.theme.DeepNavy
import com.quokkalabs.strangeplanet.ui.viewmodel.AsteroidViewModel

@Composable
fun AsteroidScreen(
    viewModel: AsteroidViewModel,
    onBack: () -> Unit,
) {
    val state by viewModel.state.collectAsState()
    val settings by viewModel.settings.collectAsState()
    val density = LocalDensity.current
    val view = LocalView.current
    val context = LocalContext.current

    var showSettings by remember { mutableStateOf(false) }

    DisposableEdgeToEdge(view)

    // Tone-based SFX (gated by the sound setting).
    val toneGen = remember {
        runCatching { ToneGenerator(AudioManager.STREAM_MUSIC, 80) }.getOrNull()
    }
    DisposableEffect(Unit) { onDispose { toneGen?.release() } }

    // "Woo-woo woo" when an uninvited oval vessel arrives.
    val ufoPresent = state.ufo != null
    LaunchedEffect(ufoPresent) {
        if (ufoPresent && settings.soundEnabled) {
            repeat(3) {
                runCatching {
                    toneGen?.startTone(ToneGenerator.TONE_CDMA_LOW_L, 200)
                }
                kotlinx.coroutines.delay(260)
            }
        }
    }

    BackHandler {
        viewModel.resetGame()
        onBack()
    }

    val shipBitmap = remember {
        BitmapFactory.decodeResource(context.resources, R.drawable.sp_rollsuck)
            .asImageBitmap()
    }
    val rockBitmap = remember {
        BitmapFactory.decodeResource(context.resources, R.drawable.sp_socks)
            .asImageBitmap()
    }

    CosmicBackground(showStars = true) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val screenWidth = with(density) { maxWidth.toPx() }
            val screenHeight = with(density) { maxHeight.toPx() }

            androidx.compose.runtime.LaunchedEffect(screenWidth, screenHeight) {
                viewModel.initGame(screenWidth, screenHeight)
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTapGestures(onTap = { viewModel.onTapToStart() })
                    },
            )

            Canvas(modifier = Modifier.fillMaxSize()) {
                val sMin = minOf(size.width, size.height)

                // Rocks (socks) — rotate the sprite around its centre.
                state.rocks.forEach { r ->
                    val px = when (r.size) {
                        RockSize.LARGE -> sMin * 0.144f
                        RockSize.MEDIUM -> sMin * 0.088f
                        RockSize.SMALL -> sMin * 0.052f
                    }
                    rotate(degrees = r.angleDeg, pivot = Offset(r.x, r.y)) {
                        drawImage(
                            image = rockBitmap,
                            dstOffset = IntOffset(
                                (r.x - px / 2f).toInt(),
                                (r.y - px / 2f).toInt(),
                            ),
                            dstSize = IntSize(px.toInt(), px.toInt()),
                        )
                    }
                }

                // Player bullets.
                state.bullets.forEach { b ->
                    drawCircle(AlienPink, sMin * 0.007f, Offset(b.x, b.y))
                }
                // UFO bullets — bright amber with a glow for high contrast
                // against the cosmic backdrop.
                val ufoBulletColor = Color(0xFFFFC400)
                state.ufoBullets.forEach { b ->
                    drawCircle(
                        ufoBulletColor.copy(alpha = 0.35f),
                        sMin * 0.018f,
                        Offset(b.x, b.y),
                    )
                    drawCircle(ufoBulletColor, sMin * 0.011f, Offset(b.x, b.y))
                }

                // UFO — procedural saucer. Warm colours + dark outline so it
                // never blends into the blue/purple backdrop.
                state.ufo?.let { u ->
                    val w = ufoR(sMin) * 2.4f
                    val h = ufoR(sMin) * 1.0f
                    val bodyColor = Color(0xFFFF6F3C)
                    val domeColor = Color(0xFFFFE08A)
                    val outline = Color(0xFF1A0E2E)
                    // Dark halo for separation from the background.
                    drawOval(
                        color = outline.copy(alpha = 0.55f),
                        topLeft = Offset(u.x - w * 0.58f, u.y - h * 0.62f),
                        size = Size(w * 1.16f, h * 1.24f),
                    )
                    drawOval(
                        color = bodyColor,
                        topLeft = Offset(u.x - w / 2f, u.y - h / 2f),
                        size = Size(w, h),
                    )
                    drawOval(
                        color = outline,
                        topLeft = Offset(u.x - w / 2f, u.y - h / 2f),
                        size = Size(w, h),
                        style = androidx.compose.ui.graphics.drawscope.Stroke(
                            width = sMin * 0.004f,
                        ),
                    )
                    drawArc(
                        color = domeColor,
                        startAngle = 180f,
                        sweepAngle = 180f,
                        useCenter = true,
                        topLeft = Offset(u.x - w * 0.28f, u.y - h * 0.9f),
                        size = Size(w * 0.56f, h * 1.1f),
                    )
                }

                // Particles — fading dots.
                state.particles.forEach { p ->
                    val a = (p.life.toFloat() / p.maxLife).coerceIn(0f, 1f)
                    val c = if (p.cosmic) CosmicBlue else AlienPink
                    drawCircle(
                        color = c.copy(alpha = a),
                        radius = sMin * 0.006f * (0.5f + a),
                        center = Offset(p.x, p.y),
                    )
                }

                // Ship (rollsuck). Flash while invincible.
                state.ship?.let { sh ->
                    val flashing = sh.invincibleTicks > 0 &&
                        (sh.invincibleTicks / 5) % 2 == 0
                    if (!flashing) {
                        val sz = sMin * 0.20f
                        if (sh.thrustOn) {
                            drawCircle(
                                color = AlienPink.copy(alpha = 0.35f),
                                radius = sz * 0.85f,
                                center = Offset(sh.x, sh.y),
                            )
                        }
                        rotate(
                            degrees = sh.angleDeg + 90f,
                            pivot = Offset(sh.x, sh.y),
                        ) {
                            drawImage(
                                image = shipBitmap,
                                dstOffset = IntOffset(
                                    (sh.x - sz / 2f).toInt(),
                                    (sh.y - sz / 2f).toInt(),
                                ),
                                dstSize = IntSize(sz.toInt(), sz.toInt()),
                            )
                        }
                    }
                }
            }

            // HUD pill.
            Row(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 76.dp)
                    .background(
                        DeepNavy.copy(alpha = 0.75f),
                        RoundedCornerShape(14.dp),
                    )
                    .padding(horizontal = 22.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(28.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                HudStat("DEBRIS", state.score.toString())
                HudStat("RECORD", state.highScore.toString())
                HudStat("TIER", state.level.toString())
                HudStat("ATTEMPTS", state.lives.coerceAtLeast(0).toString())
            }

            when (state.phase) {
                AsteroidPhase.READY -> CenterBanner(
                    "SPATIAL DEBRIS AVOIDANCE",
                    "Tap to deploy Rollsuck Supreme.",
                )
                AsteroidPhase.DYING -> CenterBanner(
                    "THIS IS NOT IDEAL",
                    "The conveyance was compromised. Reconstituting.",
                )
                AsteroidPhase.LEVEL_CLEARED -> CenterBanner(
                    "VIBRATION EMOTION",
                    state.activeSaying ?: "ALL DEBRIS NEUTRALIZED.",
                )
                AsteroidPhase.GAME_OVER -> CenterBanner(
                    "JOURNEY CONCLUDED",
                    "Debris ${state.score} · Record ${state.highScore}\n" +
                        "Tap to attempt the activity again.",
                )
                AsteroidPhase.PAUSED -> CenterBanner(
                    "ACTIVITY SUSPENDED",
                    "Tap to resume.",
                )
                else -> {}
            }

            // Back button.
            FloatingActionButton(
                onClick = { viewModel.resetGame(); onBack() },
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(top = 20.dp, start = 14.dp)
                    .size(44.dp),
                shape = CircleShape,
                containerColor = DeepNavy.copy(alpha = 0.75f),
                contentColor = AlienPink,
            ) {
                Text("←", fontSize = 22.sp)
            }

            // Pause button (during gameplay).
            if (state.phase == AsteroidPhase.PLAYING) {
                FloatingActionButton(
                    onClick = { viewModel.pauseGame() },
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(top = 20.dp, start = 66.dp)
                        .size(44.dp),
                    shape = CircleShape,
                    containerColor = DeepNavy.copy(alpha = 0.75f),
                    contentColor = AlienPink,
                ) {
                    Text("⏸", fontSize = 18.sp)
                }
            }

            // Control bar. Default order: ◀ ▲ ✦ ● ▶.
            // Alternate (settings): ● ▲ ✦ ◀ ▶ (fire, thrust, hyper, L, R).
            val rotLeft = ControlAction("◀", "LEFT",
                { viewModel.setInput { it.copy(rotLeft = true) } },
                { viewModel.setInput { it.copy(rotLeft = false) } })
            val thrust = ControlAction("▲", "THRUST",
                { viewModel.setInput { it.copy(thrust = true) } },
                { viewModel.setInput { it.copy(thrust = false) } })
            val hyper = ControlAction("✦", "HYPER",
                { viewModel.setInput { it.copy(hyperspace = true) } },
                { viewModel.setInput { it.copy(hyperspace = false) } })
            val fire = ControlAction("●", "FIRE",
                { viewModel.setInput { it.copy(fire = true) } },
                { viewModel.setInput { it.copy(fire = false) } })
            val rotRight = ControlAction("▶", "RIGHT",
                { viewModel.setInput { it.copy(rotRight = true) } },
                { viewModel.setInput { it.copy(rotRight = false) } })

            val controls = if (settings.altLayout)
                listOf(fire, thrust, hyper, rotLeft, rotRight)
            else
                listOf(rotLeft, thrust, hyper, fire, rotRight)

            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                controls.forEach { c ->
                    HoldButton(
                        glyph = c.glyph,
                        caption = c.caption,
                        modifier = Modifier.weight(1f),
                        onPress = c.onPress,
                        onRelease = c.onRelease,
                    )
                }
            }

            // Settings button.
            FloatingActionButton(
                onClick = {
                    showSettings = true
                    viewModel.pauseGame()
                },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 20.dp, end = 14.dp)
                    .size(44.dp),
                shape = CircleShape,
                containerColor = DeepNavy.copy(alpha = 0.75f),
                contentColor = AlienPink,
            ) {
                Text("⚙", fontSize = 20.sp)
            }

            if (showSettings) {
                AsteroidSettingsPanel(
                    soundEnabled = settings.soundEnabled,
                    altLayout = settings.altLayout,
                    onSound = viewModel::setSoundEnabled,
                    onAltLayout = viewModel::setAltLayout,
                    onClose = {
                        showSettings = false
                        viewModel.resumeGame()
                    },
                )
            }
        }
    }
}

private data class ControlAction(
    val glyph: String,
    val caption: String,
    val onPress: () -> Unit,
    val onRelease: () -> Unit,
)

@Composable
private fun BoxScope.AsteroidSettingsPanel(
    soundEnabled: Boolean,
    altLayout: Boolean,
    onSound: (Boolean) -> Unit,
    onAltLayout: (Boolean) -> Unit,
    onClose: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.6f))
            .clickable(onClick = onClose),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.86f)
                .background(DeepNavy.copy(alpha = 0.96f), RoundedCornerShape(20.dp))
                .clickable(enabled = false) {}
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                "ACTIVITY PARAMETERS",
                color = AlienPink,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(16.dp))
            AsteroidToggleRow("AUDIBLE FEEDBACK", soundEnabled) {
                onSound(!soundEnabled)
            }
            AsteroidToggleRow("ALTERNATE CONTROL LAYOUT", altLayout) {
                onAltLayout(!altLayout)
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "Alternate: fire · thrust · hyperspace · left · right",
                color = Color.White.copy(alpha = 0.4f),
                fontSize = 11.sp,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(18.dp))
            Text(
                "RESUME ACTIVITY",
                color = AlienPink,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .clickable(onClick = onClose)
                    .padding(8.dp),
            )
        }
    }
}

@Composable
private fun AsteroidToggleRow(label: String, on: Boolean, onToggle: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            color = Color.White.copy(alpha = 0.7f),
            fontSize = 14.sp,
            maxLines = 1,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(12.dp))
        Text(
            if (on) "ENABLED" else "DISABLED",
            color = if (on) AlienPink else Color.White.copy(alpha = 0.35f),
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            softWrap = false,
        )
    }
}

private fun ufoR(sMin: Float) = sMin * 0.050f

@Composable
private fun DisposableEdgeToEdge(view: android.view.View) {
    androidx.compose.runtime.DisposableEffect(Unit) {
        val window = (view.context as? android.app.Activity)?.window
        if (window != null) {
            val controller = WindowCompat.getInsetsController(window, view)
            controller.hide(WindowInsetsCompat.Type.systemBars())
            controller.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        onDispose {
            if (window != null) {
                val controller = WindowCompat.getInsetsController(window, view)
                controller.show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }
}

@Composable
private fun HoldButton(
    glyph: String,
    caption: String,
    modifier: Modifier,
    onPress: () -> Unit,
    onRelease: () -> Unit,
) {
    // rememberUpdatedState keeps the gesture coroutine stable while always
    // invoking the *current* handlers — without this, pointerInput(Unit)
    // would keep the stale lambdas after the layout is re-ordered.
    val press by rememberUpdatedState(onPress)
    val release by rememberUpdatedState(onRelease)
    Box(
        modifier = modifier
            .height(64.dp)
            .background(DeepNavy.copy(alpha = 0.6f), RoundedCornerShape(16.dp))
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        press()
                        tryAwaitRelease()
                        release()
                    },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                glyph,
                color = AlienPink,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                caption,
                color = Color.White.copy(alpha = 0.6f),
                fontSize = 9.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                softWrap = false,
            )
        }
    }
}

@Composable
private fun HudStat(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            label,
            color = Color.White.copy(alpha = 0.45f),
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium,
        )
        Text(
            value,
            color = AlienPink,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun BoxScope.CenterBanner(title: String, subtitle: String) {
    Column(
        modifier = Modifier
            .align(Alignment.Center)
            .background(DeepNavy.copy(alpha = 0.88f), RoundedCornerShape(20.dp))
            .padding(horizontal = 32.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            title,
            color = AlienPink,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            subtitle,
            color = Color.White.copy(alpha = 0.65f),
            fontSize = 13.sp,
            textAlign = TextAlign.Center,
        )
    }
}
