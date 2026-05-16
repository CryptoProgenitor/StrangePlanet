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
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
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
import com.quokkalabs.strangeplanet.data.model.Ufo
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import com.quokkalabs.strangeplanet.ui.components.CosmicBackground
import com.quokkalabs.strangeplanet.ui.components.PauseOnBackground
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

    var scorePopupText by remember { mutableStateOf("") }
    var showScorePopup by remember { mutableStateOf(false) }
    var prevScore by remember { mutableIntStateOf(0) }

    LaunchedEffect(state.score) {
        val gain = state.score - prevScore
        prevScore = state.score
        if (gain > 0 && state.phase == AsteroidPhase.PLAYING) {
            scorePopupText = "+$gain"
            showScorePopup = true
            kotlinx.coroutines.delay(900)
            showScorePopup = false
        }
    }

    DisposableEdgeToEdge(view)

    // Screen off / app backgrounded → suspend play.
    PauseOnBackground { viewModel.pauseGame() }

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

    val soundOn by rememberUpdatedState(settings.soundEnabled)
    fun tone(t: Int, ms: Int) {
        if (soundOn) runCatching { toneGen?.startTone(t, ms) }
    }

    // Event SFX: blaster fire, debris impact, hyperspace jump. Baselines reset
    // whenever play is not active so a fresh life never mis-detects.
    LaunchedEffect(Unit) {
        var pBullets = 0
        var pHyper = 0
        var pScore = 0
        snapshotFlow { state }.collect { s ->
            if (s.phase != AsteroidPhase.PLAYING) {
                pBullets = s.bullets.size
                pHyper = s.ship?.hyperspaceCooldown ?: 0
                pScore = s.score
                return@collect
            }
            val nb = s.bullets.size
            if (nb > pBullets) tone(ToneGenerator.TONE_PROP_BEEP, 26)
            pBullets = nb

            val nh = s.ship?.hyperspaceCooldown ?: 0
            if (nh > pHyper) tone(ToneGenerator.TONE_CDMA_HIGH_SS, 170)
            pHyper = nh

            if (s.score > pScore) tone(ToneGenerator.TONE_PROP_NACK, 90)
            pScore = s.score
        }
    }

    // Thrust rumble: a low pulse while the conveyance is accelerating.
    val thrusting = state.ship?.thrustOn == true && state.phase == AsteroidPhase.PLAYING
    LaunchedEffect(thrusting) {
        while (thrusting && soundOn) {
            runCatching { toneGen?.startTone(ToneGenerator.TONE_CDMA_LOW_L, 90) }
            kotlinx.coroutines.delay(120)
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
                val pulseT = (System.nanoTime() / 1_000_000L % 1400L) / 1400f

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

                // UFO — detailed warm saucer with dome highlight and pulsing
                // rim lights; layered so it never blends into the backdrop.
                state.ufo?.let { u -> drawUfo(u, sMin, pulseT) }

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

            AnimatedVisibility(
                visible = showScorePopup,
                enter = fadeIn(tween(120)),
                exit = fadeOut(tween(400)),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 136.dp),
            ) {
                Text(
                    scorePopupText,
                    color = AlienPink,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                )
            }

            when (state.phase) {
                AsteroidPhase.READY -> CenterBanner(
                    "SPATIAL DEBRIS AVOIDANCE",
                    "Tap to deploy Rollsuck Supreme.",
                    onAbandon = { viewModel.resetGame(); onBack() },
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
            val hyperOnCooldown = (state.ship?.hyperspaceCooldown ?: 0) > 0
            val hyper = ControlAction("✦", "HYPER",
                { viewModel.setInput { it.copy(hyperspace = true) } },
                { viewModel.setInput { it.copy(hyperspace = false) } },
                dimmed = hyperOnCooldown)
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
                        dimmed = c.dimmed,
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

            AnimatedVisibility(
                visible = showSettings,
                enter = fadeIn(tween(150)),
                exit = fadeOut(tween(150)),
            ) {
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
    val dimmed: Boolean = false,
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

private fun DrawScope.drawUfo(u: Ufo, sMin: Float, t: Float) {
    val scale = if (u.small) 0.72f else 1f
    val r = ufoR(sMin) * scale
    val w = r * 2.6f
    val h = r * 1.05f
    val cx = u.x
    val cy = u.y

    val glow = Color(0xFFFFB066)
    val hullDark = Color(0xFF8A2D12)
    val bodyBright = Color(0xFFFF7A3D)
    val bodySheen = Color(0xFFFFB87A)
    val dome = Color(0xFFFFE7A6)
    val outline = Color(0xFF120A22)
    val lightOn = Color(0xFFB9F6FF)
    val stroke = androidx.compose.ui.graphics.drawscope.Stroke(width = sMin * 0.0035f)

    // Warm glow halo.
    drawCircle(glow.copy(alpha = 0.16f), w * 0.80f, Offset(cx, cy))
    drawCircle(glow.copy(alpha = 0.09f), w * 1.08f, Offset(cx, cy))
    // Main disc.
    drawOval(
        color = bodyBright,
        topLeft = Offset(cx - w * 0.5f, cy - h * 0.5f),
        size = Size(w, h),
    )
    // Upper sheen.
    drawOval(
        color = bodySheen.copy(alpha = 0.9f),
        topLeft = Offset(cx - w * 0.34f, cy - h * 0.60f),
        size = Size(w * 0.68f, h * 0.85f),
    )
    // Disc outline.
    drawOval(
        color = outline,
        topLeft = Offset(cx - w * 0.5f, cy - h * 0.5f),
        size = Size(w, h),
        style = stroke,
    )
    // Cockpit dome + outline + highlight.
    drawArc(
        color = dome,
        startAngle = 180f,
        sweepAngle = 180f,
        useCenter = true,
        topLeft = Offset(cx - w * 0.22f, cy - h * 1.05f),
        size = Size(w * 0.44f, h * 1.5f),
    )
    drawArc(
        color = outline,
        startAngle = 180f,
        sweepAngle = 180f,
        useCenter = false,
        topLeft = Offset(cx - w * 0.22f, cy - h * 1.05f),
        size = Size(w * 0.44f, h * 1.5f),
        style = stroke,
    )
    drawOval(
        color = Color.White.copy(alpha = 0.65f),
        topLeft = Offset(cx - w * 0.12f, cy - h * 0.92f),
        size = Size(w * 0.11f, h * 0.5f),
    )
    // Pulsing rim lights — parametric positions along the lower arc of the disc.
    val n = 5
    for (i in 0 until n) {
        val angle = PI.toFloat() / 6f + i.toFloat() * (PI.toFloat() * 2f / 3f / (n - 1).toFloat())
        val lx = cx + cos(angle) * w * 0.40f
        val ly = cy + sin(angle) * h * 0.42f
        val ph = (sin(t * 2f * PI.toFloat() + i * 1.1f) * 0.5f + 0.5f)
        drawCircle(outline, r * 0.14f, Offset(lx, ly))
        drawCircle(
            lightOn.copy(alpha = 0.30f + 0.60f * ph),
            r * 0.10f,
            Offset(lx, ly),
        )
    }
}

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
    dimmed: Boolean = false,
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
                color = AlienPink.copy(alpha = if (dimmed) 0.35f else 1f),
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                caption,
                color = Color.White.copy(alpha = if (dimmed) 0.25f else 0.6f),
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
private fun BoxScope.CenterBanner(title: String, subtitle: String, onAbandon: (() -> Unit)? = null) {
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
        if (onAbandon != null) {
            Spacer(Modifier.height(16.dp))
            Text(
                "Abandon Endeavour",
                color = Color.White.copy(alpha = 0.55f),
                fontSize = 12.sp,
                modifier = Modifier
                    .clickable(onClick = onAbandon)
                    .padding(4.dp),
            )
        }
    }
}
