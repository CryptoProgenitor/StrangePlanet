package com.quokkalabs.strangeplanet.ui.screen

import android.graphics.BitmapFactory
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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
                    // Maze walls
                    state.walls.forEach { k ->
                        val c = k % state.cols
                        val r = k / state.cols
                        drawRoundRect(
                            color = CosmicBlue.copy(alpha = 0.55f),
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
                Row(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 20.dp, start = 64.dp, end = 64.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    HudStat("SUSTENANCE", state.score.toString())
                    HudStat("DESIGNATION", "TIER ${state.level}")
                    HudStat("ATTEMPTS", state.lives.toString())
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
                        subtitle = "Tap to attempt the activity again.",
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
        }
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
