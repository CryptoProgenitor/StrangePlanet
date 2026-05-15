package com.quokkalabs.strangeplanet.ui.screen

import android.graphics.BitmapFactory
import android.media.AudioManager
import android.media.ToneGenerator
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
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
import androidx.compose.ui.graphics.drawscope.DrawScope
import com.quokkalabs.strangeplanet.data.model.PacAvatar
import com.quokkalabs.strangeplanet.data.model.PacDir
import com.quokkalabs.strangeplanet.data.model.PacPhase
import com.quokkalabs.strangeplanet.data.model.SeekerEntity
import com.quokkalabs.strangeplanet.data.model.SeekerMode
import com.quokkalabs.strangeplanet.data.model.SeekerType
import com.quokkalabs.strangeplanet.ui.components.CosmicBackground
import com.quokkalabs.strangeplanet.ui.theme.AlienPink
import com.quokkalabs.strangeplanet.ui.theme.CosmicBlue
import com.quokkalabs.strangeplanet.ui.theme.DeepNavy
import com.quokkalabs.strangeplanet.ui.viewmodel.PacViewModel
import kotlin.math.abs
import kotlin.math.sin

@Composable
fun PacScreen(
    viewModel: PacViewModel,
    onBack: () -> Unit,
) {
    val state by viewModel.state.collectAsState()
    val settings by viewModel.pacSettings.collectAsState()
    val density = LocalDensity.current
    val view = LocalView.current
    val context = LocalContext.current

    DisposableEffect(Unit) {
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

    BackHandler {
        viewModel.resetGame()
        onBack()
    }

    val avatarBitmap = remember(settings.avatar) {
        val resId = when (settings.avatar) {
            PacAvatar.BEING -> R.drawable.sp_alien_dad
            PacAvatar.HOUND -> R.drawable.sp_dog
            PacAvatar.FELINE -> R.drawable.sp_cat
            PacAvatar.ROLLSUCK -> R.drawable.sp_rollsuck
            PacAvatar.UNICORN -> R.drawable.sp_unicorn
        }
        BitmapFactory.decodeResource(context.resources, resId).asImageBitmap()
    }
    val starBitmap = remember {
        BitmapFactory.decodeResource(context.resources, R.drawable.sp_star).asImageBitmap()
    }

    val swipeThreshold = with(density) { 24.dp.toPx() }

    var showSettings by remember { mutableStateOf(false) }

    // Linear clock (radians) — each sock derives its own phase offset so the
    // halos pulse out of sync with one another.
    val pulseClock by rememberInfiniteTransition(label = "sockPulse").animateFloat(
        initialValue = 0f,
        targetValue = (2.0 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "sockPulseClock",
    )

    // Tone-based SFX (gated by the sound setting).
    val toneGen = remember {
        runCatching { ToneGenerator(AudioManager.STREAM_MUSIC, 70) }.getOrNull()
    }
    DisposableEffect(Unit) { onDispose { toneGen?.release() } }
    fun beep(tone: Int, ms: Int) {
        if (settings.soundEnabled) runCatching { toneGen?.startTone(tone, ms) }
    }
    LaunchedEffect(state.score) {
        if (state.score > 0) beep(ToneGenerator.TONE_PROP_BEEP, 40)
    }
    LaunchedEffect(state.frightenedTick > 0) {
        if (state.frightenedTick > 0) beep(ToneGenerator.TONE_PROP_BEEP2, 120)
    }
    LaunchedEffect(state.lives) {
        if (state.phase == PacPhase.DYING) beep(ToneGenerator.TONE_CDMA_ABBR_ALERT, 300)
    }
    LaunchedEffect(state.phase) {
        if (state.phase == PacPhase.LEVEL_CLEARED) {
            beep(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 250)
        }
    }

    CosmicBackground(showStars = true) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val screenWidth = with(density) { maxWidth.toPx() }
            val screenHeight = with(density) { maxHeight.toPx() }

            LaunchedEffect(screenWidth, screenHeight) {
                viewModel.initGame(screenWidth, screenHeight)
            }

            // Swipe-anywhere control + tap-to-(re)start.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        var dx = 0f
                        var dy = 0f
                        detectDragGestures(
                            onDragStart = { dx = 0f; dy = 0f },
                            onDragEnd = { dx = 0f; dy = 0f },
                        ) { change, drag ->
                            change.consume()
                            dx += drag.x
                            dy += drag.y
                            if (abs(dx) > swipeThreshold || abs(dy) > swipeThreshold) {
                                val dir = if (abs(dx) > abs(dy)) {
                                    if (dx > 0) PacDir.RIGHT else PacDir.LEFT
                                } else {
                                    if (dy > 0) PacDir.DOWN else PacDir.UP
                                }
                                viewModel.onSwipe(dir)
                                dx = 0f
                                dy = 0f
                            }
                        }
                    }
                    .pointerInput(Unit) {
                        detectTapGestures(onTap = { viewModel.onTapToStart() })
                    },
            )

            if (state.tileSize > 0f) {
                val ts = state.tileSize

                Canvas(modifier = Modifier.fillMaxSize()) {
                    // Maze floor — cuts the cosmic background through so the
                    // grid reads as a discrete playfield.
                    drawRect(
                        color = DeepNavy.copy(alpha = 0.97f),
                        topLeft = Offset(state.originX, state.originY),
                        size = Size(state.cols * ts, state.rows * ts),
                    )

                    // Maze walls
                    state.walls.forEach { k ->
                        val c = k % state.cols
                        val r = k / state.cols
                        drawRoundRect(
                            color = CosmicBlue.copy(alpha = 0.70f),
                            topLeft = Offset(
                                state.originX + c * ts,
                                state.originY + r * ts,
                            ),
                            size = Size(ts, ts),
                            cornerRadius = CornerRadius(ts * 0.22f, ts * 0.22f),
                        )
                    }

                    // Stars
                    val starSz = (ts * 0.55f).toInt()
                    state.pellets.forEach { k ->
                        val c = k % state.cols
                        val r = k / state.cols
                        val cx = state.originX + (c + 0.5f) * ts
                        val cy = state.originY + (r + 0.5f) * ts
                        drawImage(
                            image = starBitmap,
                            dstOffset = IntOffset(
                                (cx - starSz / 2f).toInt(),
                                (cy - starSz / 2f).toInt(),
                            ),
                            dstSize = IntSize(starSz, starSz),
                        )
                    }

                    // Socks (power pellets) — fabric tube + pulsing halo
                    state.socks.forEach { k ->
                        val c = k % state.cols
                        val r = k / state.cols
                        val cx = state.originX + (c + 0.5f) * ts
                        val cy = state.originY + (r + 0.5f) * ts
                        val pulse = (sin(pulseClock + k * 1.7f) + 1f) / 2f
                        drawCircle(
                            color = AlienPink.copy(alpha = 0.18f + 0.30f * pulse),
                            radius = ts * (0.45f + 0.30f * pulse),
                            center = Offset(cx, cy),
                        )
                        val tubeColor = Color(0xFFEAD9FF)
                        // Leg of the sock.
                        drawRoundRect(
                            color = tubeColor,
                            topLeft = Offset(cx - ts * 0.13f, cy - ts * 0.30f),
                            size = Size(ts * 0.26f, ts * 0.42f),
                            cornerRadius = CornerRadius(ts * 0.10f, ts * 0.10f),
                        )
                        // Foot of the sock.
                        drawRoundRect(
                            color = tubeColor,
                            topLeft = Offset(cx - ts * 0.13f, cy + ts * 0.04f),
                            size = Size(ts * 0.40f, ts * 0.22f),
                            cornerRadius = CornerRadius(ts * 0.11f, ts * 0.11f),
                        )
                        // Cuff stripe.
                        drawRoundRect(
                            color = AlienPink,
                            topLeft = Offset(cx - ts * 0.13f, cy - ts * 0.30f),
                            size = Size(ts * 0.26f, ts * 0.09f),
                            cornerRadius = CornerRadius(ts * 0.05f, ts * 0.05f),
                        )
                    }

                    // Seekers (procedural dome + wavy-feet silhouette)
                    state.seekers.forEach { s ->
                        drawSeeker(
                            s = s,
                            originX = state.originX,
                            originY = state.originY,
                            ts = ts,
                            frightenedTick = state.frightenedTick,
                        )
                    }

                    // Being (smoothly interpolated between tiles)
                    val b = state.being
                    val bx = state.originX +
                        (b.col + b.dir.dc * b.progress + 0.5f) * ts
                    val by = state.originY +
                        (b.row + b.dir.dr * b.progress + 0.5f) * ts
                    val bSz = (ts * 1.5f).toInt()
                    // Halo glow — concentric soft rings make the being pop
                    // against the busy background.
                    drawCircle(AlienPink.copy(alpha = 0.38f), ts * 1.40f, Offset(bx, by))
                    drawCircle(AlienPink.copy(alpha = 0.28f), ts * 1.05f, Offset(bx, by))
                    drawCircle(AlienPink.copy(alpha = 0.18f), ts * 0.72f, Offset(bx, by))
                    drawImage(
                        image = avatarBitmap,
                        dstOffset = IntOffset(
                            (bx - bSz / 2f).toInt(),
                            (by - bSz / 2f).toInt(),
                        ),
                        dstSize = IntSize(bSz, bSz),
                    )
                }

                // HUD — full deadpan terminology
                Column(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 76.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Row(
                        modifier = Modifier
                            .background(
                                DeepNavy.copy(alpha = 0.75f),
                                RoundedCornerShape(14.dp),
                            )
                            .padding(horizontal = 22.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(28.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        HudStat("SUSTENANCE", state.score.toString())
                        HudStat("RECORD", state.highScore.toString())
                        HudStat("DESIGNATION", "TIER ${state.level}")
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                "ATTEMPTS",
                                color = Color.White.copy(alpha = 0.45f),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Medium,
                            )
                            Spacer(Modifier.height(4.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                repeat(state.lives.coerceAtLeast(0)) {
                                    Image(
                                        bitmap = avatarBitmap,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp),
                                    )
                                }
                            }
                        }
                    }
                    if (state.frightenedTick > 0 && settings.showSayings) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "THE PERISHED BEINGS ARE VULNERABLE",
                            color = AlienPink,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }

                when (state.phase) {
                    PacPhase.READY -> CenterBanner(
                        title = "PREPARE FOR PURSUIT",
                        subtitle = "Gesture in any direction to commence locomotion.",
                    )
                    PacPhase.DYING -> CenterBanner(
                        title = "THIS IS NOT IDEAL",
                        subtitle = "A perished being made contact. Reconstituting.",
                    )
                    PacPhase.LEVEL_CLEARED -> CenterBanner(
                        title = "VIBRATION EMOTION",
                        subtitle = state.activeSaying ?: "ALL STARS CONSUMED.",
                    )
                    PacPhase.GAME_OVER -> CenterBanner(
                        title = "PURSUIT CONCLUDED",
                        subtitle = "Sustenance ${state.score} · Record " +
                            "${state.highScore}\nTap to attempt the activity again.",
                    )
                    PacPhase.PAUSED -> CenterBanner(
                        title = "ACTIVITY SUSPENDED",
                        subtitle = "Tap to resume.",
                    )
                    else -> {}
                }
            }

            // Back button (matches the styling used elsewhere)
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

            // Settings button
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
                PacSettingsPanel(
                    settings = settings,
                    onAvatar = viewModel::setAvatar,
                    onSound = viewModel::setSoundEnabled,
                    onSayings = viewModel::setShowSayings,
                    onClose = {
                        showSettings = false
                        viewModel.resumeGame()
                    },
                )
            }
        }
    }
}

@Composable
private fun BoxScope.PacSettingsPanel(
    settings: com.quokkalabs.strangeplanet.data.model.PacSettings,
    onAvatar: (PacAvatar) -> Unit,
    onSound: (Boolean) -> Unit,
    onSayings: (Boolean) -> Unit,
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
            Text(
                "DESIGNATED FORM",
                color = Color.White.copy(alpha = 0.45f),
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
            )
            Spacer(Modifier.height(8.dp))
            PacAvatar.values().forEach { av ->
                val selected = settings.avatar == av
                Text(
                    av.label,
                    color = if (selected) AlienPink else Color.White.copy(alpha = 0.7f),
                    fontSize = 15.sp,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onAvatar(av) }
                        .padding(vertical = 8.dp),
                    textAlign = TextAlign.Center,
                )
            }
            Spacer(Modifier.height(12.dp))
            ToggleRow("AUDIBLE FEEDBACK", settings.soundEnabled) { onSound(!settings.soundEnabled) }
            ToggleRow("DEADPAN COMMENTARY", settings.showSayings) { onSayings(!settings.showSayings) }
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
private fun ToggleRow(label: String, on: Boolean, onToggle: () -> Unit) {
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

private fun DrawScope.drawSeeker(
    s: SeekerEntity,
    originX: Float,
    originY: Float,
    ts: Float,
    frightenedTick: Int,
) {
    val cx = originX + (s.col + s.dir.dc * s.progress + 0.5f) * ts
    val cy = originY + (s.row + s.dir.dr * s.progress + 0.5f) * ts

    val pupilColor = Color(0xFF1A0050)
    val frightened = s.mode == SeekerMode.FRIGHTENED && frightenedTick > 0
    val eaten = s.mode == SeekerMode.EATEN

    val bodyColor = when {
        frightened -> {
            // Flash white in the final ~2 s (≈125 ticks).
            if (frightenedTick < 125 && (frightenedTick / 14) % 2 == 0) Color.White
            else Color(0xFF1A0050)
        }
        else -> when (s.type) {
            SeekerType.MINUTE_REMINDER -> Color(0xFFFF6B6B)
            SeekerType.SOCIAL_ANXIETY -> Color(0xFFFFB5D8)
            SeekerType.LOGICAL_DEBATER -> Color(0xFF00E5FF)
            SeekerType.OPTIONAL_OBLIGATION -> Color(0xFFFFB347)
        }
    }

    val r = ts * 0.42f

    if (!eaten) {
        // Top dome.
        drawArc(
            color = bodyColor,
            startAngle = 180f,
            sweepAngle = 180f,
            useCenter = true,
            topLeft = Offset(cx - r, cy - r),
            size = Size(r * 2f, r * 1.4f),
        )
        // Body.
        drawRect(
            color = bodyColor,
            topLeft = Offset(cx - r, cy - r * 0.3f),
            size = Size(r * 2f, r * 0.95f),
        )
        // Three wavy feet.
        val bumpR = r / 3f
        val footY = cy + r * 0.65f
        for (i in 0..2) {
            val bcx = cx - r + bumpR + i * bumpR * 2f
            drawArc(
                color = bodyColor,
                startAngle = 0f,
                sweepAngle = 180f,
                useCenter = true,
                topLeft = Offset(bcx - bumpR, footY - bumpR),
                size = Size(bumpR * 2f, bumpR * 2f),
            )
        }
    }

    if (frightened) {
        // Two small white eye dots.
        drawCircle(Color.White, ts * 0.05f, Offset(cx - ts * 0.11f, cy - ts * 0.06f))
        drawCircle(Color.White, ts * 0.05f, Offset(cx + ts * 0.11f, cy - ts * 0.06f))
    } else {
        // Directional eyes (whites + pupils that lean with travel).
        val eyeR = ts * 0.10f
        val pupilR = ts * 0.055f
        val eyeDX = ts * 0.13f
        val eyeDY = ts * 0.10f
        val leanX = s.dir.dc * ts * 0.05f
        val leanY = s.dir.dr * ts * 0.05f
        for (side in listOf(-1f, 1f)) {
            val ex = cx + side * eyeDX
            val ey = cy - eyeDY
            drawCircle(Color.White, eyeR, Offset(ex, ey))
            drawCircle(pupilColor, pupilR, Offset(ex + leanX, ey + leanY))
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
private fun BoxScope.CenterBanner(
    title: String,
    subtitle: String,
) {
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
