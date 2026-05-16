package com.quokkalabs.strangeplanet.ui.screen

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.quokkalabs.strangeplanet.data.model.MergePhase
import com.quokkalabs.strangeplanet.data.model.MergeTier
import com.quokkalabs.strangeplanet.data.model.Orb
import com.quokkalabs.strangeplanet.ui.components.CosmicBackground
import com.quokkalabs.strangeplanet.ui.components.PauseOnBackground
import com.quokkalabs.strangeplanet.ui.theme.AlienPink
import com.quokkalabs.strangeplanet.ui.theme.DeepNavy
import com.quokkalabs.strangeplanet.ui.viewmodel.MergeViewModel

@Composable
fun MergeScreen(
    viewModel: MergeViewModel,
    onBack: () -> Unit,
) {
    val state by viewModel.state.collectAsState()
    val density = LocalDensity.current
    val view = LocalView.current

    DisposableEdgeToEdge(view)
    PauseOnBackground { /* physics pauses naturally when phase != PLAYING */ }

    BackHandler {
        viewModel.resetGame()
        onBack()
    }

    CosmicBackground(showStars = true) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val screenWidthPx = with(density) { maxWidth.toPx() }
            val screenHeightPx = with(density) { maxHeight.toPx() }

            DisposableEffect(screenWidthPx, screenHeightPx) {
                viewModel.initGame(screenWidthPx, screenHeightPx)
                onDispose { }
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            viewModel.onAim(down.position.x)
                            down.consume()
                            while (true) {
                                val event = awaitPointerEvent()
                                val ch = event.changes.firstOrNull() ?: break
                                viewModel.onAim(ch.position.x)
                                ch.consume()
                                if (!ch.pressed) break
                            }
                            viewModel.onRelease()
                        }
                    },
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    drawVessel(state.vesselLeft, state.vesselRight, state.vesselTop, state.vesselBottom)

                    // Aim guide + hovering current orb at the spout
                    if (state.phase == MergePhase.PLAYING) {
                        val r = state.currentTier.radiusFrac * (state.vesselRight - state.vesselLeft)
                        val gx = state.spoutX
                        var gy = state.vesselTop
                        while (gy < state.vesselBottom) {
                            drawLine(
                                color = Color.White.copy(alpha = 0.12f),
                                start = Offset(gx, gy),
                                end = Offset(gx, (gy + 14f).coerceAtMost(state.vesselBottom)),
                                strokeWidth = 2f,
                            )
                            gy += 24f
                        }
                        drawOrb(
                            Orb(-1L, state.currentTier, gx, state.vesselTop - r - 4f),
                            r,
                            alphaMul = 0.85f,
                        )
                    }

                    // Settled / falling orbs
                    state.orbs.forEach { o ->
                        val r = o.tier.radiusFrac * (state.vesselRight - state.vesselLeft)
                        drawOrb(o, r)
                    }
                }

                // ── HUD pill ────────────────────────────────────────────────
                Row(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 28.dp)
                        .background(DeepNavy.copy(alpha = 0.7f), RoundedCornerShape(16.dp))
                        .padding(horizontal = 20.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(22.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    HudStat("MASS ACCUMULATED", state.score.toString())
                    HudStat("RECORD", state.highScore.toString())
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            "NEXT",
                            color = Color.White.copy(alpha = 0.5f),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium,
                        )
                        Spacer(Modifier.height(4.dp))
                        Box(
                            modifier = Modifier
                                .size(20.dp)
                                .background(tierColor(state.nextTier), CircleShape),
                        )
                    }
                }

                // ── Merge / void name flash ────────────────────────────────
                MergeNameFlash(
                    name = state.lastMergeName,
                    emphatic = state.voidFlash,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 110.dp),
                )

                // ── READY ──────────────────────────────────────────────────
                AnimatedVisibility(
                    visible = state.phase == MergePhase.READY,
                    enter = fadeIn(tween(150)),
                    exit = fadeOut(tween(150)),
                ) {
                    MergeBanner(
                        title = "SPHERICAL\nAGGLOMERATION",
                        subtitle = "Release spheres into the containment\n" +
                            "vessel. Identical spheres coalesce.\n\n" +
                            "Form THE INEVITABLE VOID to\nconsume all nearby mass.\n\n" +
                            "Drag to aim · release to deposit\nTap to commence.",
                        onAbandon = { viewModel.resetGame(); onBack() },
                    )
                }

                // ── GAME OVER ──────────────────────────────────────────────
                AnimatedVisibility(
                    visible = state.phase == MergePhase.GAME_OVER,
                    enter = fadeIn(tween(150)),
                    exit = fadeOut(tween(150)),
                ) {
                    MergeBanner(
                        title = "VESSEL CAPACITY\nEXCEEDED",
                        subtitle = "Mass ${state.score} · Record ${state.highScore}\n" +
                            "This is not ideal.\n\nTap to attempt again.",
                    )
                }

                // ── Back FAB ───────────────────────────────────────────────
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
}

// ─── Drawing ─────────────────────────────────────────────────────────────────

private fun DrawScope.drawVessel(left: Float, right: Float, top: Float, bottom: Float) {
    val wall = Color(0xFF9FB6E0).copy(alpha = 0.55f)
    val w = 7f
    // Floor
    drawLine(wall, Offset(left, bottom), Offset(right, bottom), strokeWidth = w)
    // Walls
    drawLine(wall, Offset(left, top), Offset(left, bottom), strokeWidth = w)
    drawLine(wall, Offset(right, top), Offset(right, bottom), strokeWidth = w)
    // Capacity line (dashed)
    var x = left
    while (x < right) {
        drawLine(
            color = AlienPink.copy(alpha = 0.30f),
            start = Offset(x, top),
            end = Offset((x + 16f).coerceAtMost(right), top),
            strokeWidth = 2f,
        )
        x += 28f
    }
}

private fun DrawScope.drawOrb(o: Orb, r: Float, alphaMul: Float = 1f) {
    val base = tierColor(o.tier)
    val center = Offset(o.x, o.y)

    // Outer glow for luminous tiers
    val glow = when (o.tier) {
        MergeTier.STAR -> 0.45f
        MergeTier.NEUTRON_STAR -> 0.6f
        MergeTier.BLACK_HOLE -> 0.7f
        else -> 0f
    }
    if (glow > 0f) {
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    base.copy(alpha = glow * alphaMul),
                    base.copy(alpha = 0.08f * alphaMul),
                    Color.Transparent,
                ),
                center = center,
                radius = r * 2.1f,
            ),
            radius = r * 2.1f,
            center = center,
        )
    }

    if (o.tier == MergeTier.BLACK_HOLE) {
        // Accretion ring + void core
        drawCircle(
            color = Color(0xFFE8B4C8).copy(alpha = 0.7f * alphaMul),
            radius = r,
            center = center,
            style = Stroke(width = r * 0.22f),
        )
        drawCircle(Color(0xFF0A0A12).copy(alpha = alphaMul), r * 0.86f, center)
        return
    }

    // Body with a top-left highlight
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                lighten(base).copy(alpha = alphaMul),
                base.copy(alpha = alphaMul),
                darken(base).copy(alpha = alphaMul),
            ),
            center = Offset(o.x - r * 0.35f, o.y - r * 0.35f),
            radius = r * 1.5f,
        ),
        radius = r,
        center = center,
    )
    // Rim
    drawCircle(
        color = darken(base).copy(alpha = 0.5f * alphaMul),
        radius = r,
        center = center,
        style = Stroke(width = r * 0.06f),
    )
}

private fun tierColor(t: MergeTier): Color = when (t) {
    MergeTier.DUST_MOTE -> Color(0xFF8A8A96)
    MergeTier.PEBBLE -> Color(0xFF6E7486)
    MergeTier.BOULDER -> Color(0xFF8C7A5E)
    MergeTier.MOONLET -> Color(0xFFB9B6C4)
    MergeTier.MOON -> Color(0xFFD7E2F0)
    MergeTier.STRANGE_PLANET -> Color(0xFF3FB6C4)
    MergeTier.GAS_GIANT -> Color(0xFFD98A3D)
    MergeTier.STAR -> Color(0xFFFFD66B)
    MergeTier.NEUTRON_STAR -> Color(0xFF9FD6FF)
    MergeTier.BLACK_HOLE -> Color(0xFF1A0030)
}

private fun lighten(c: Color) = Color(
    (c.red + (1f - c.red) * 0.45f),
    (c.green + (1f - c.green) * 0.45f),
    (c.blue + (1f - c.blue) * 0.45f),
    c.alpha,
)

private fun darken(c: Color) = Color(
    c.red * 0.55f,
    c.green * 0.55f,
    c.blue * 0.55f,
    c.alpha,
)

// ─── Overlays ────────────────────────────────────────────────────────────────

@Composable
private fun BoxScope.MergeNameFlash(
    name: String?,
    emphatic: Boolean,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = name != null,
        enter = fadeIn(tween(120)),
        exit = fadeOut(tween(500)),
        modifier = modifier,
    ) {
        Text(
            text = name ?: "",
            color = if (emphatic) AlienPink else Color.White.copy(alpha = 0.85f),
            fontSize = if (emphatic) 20.sp else 15.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun BoxScope.MergeBanner(
    title: String,
    subtitle: String,
    onAbandon: (() -> Unit)? = null,
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 340.dp)
                .fillMaxWidth(0.9f)
                .background(DeepNavy.copy(alpha = 0.9f), RoundedCornerShape(20.dp))
                .padding(horizontal = 28.dp, vertical = 26.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                title,
                color = AlienPink,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                subtitle,
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
            )
            if (onAbandon != null) {
                Spacer(Modifier.height(16.dp))
                Text(
                    "Abandon Endeavour",
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 13.sp,
                    modifier = Modifier
                        .clickable(onClick = onAbandon)
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                )
            }
        }
    }
}

// ─── Local helpers (private per-file, as elsewhere in the codebase) ──────────

@Composable
private fun DisposableEdgeToEdge(view: android.view.View) {
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
}

@Composable
private fun HudStat(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            label,
            color = Color.White.copy(alpha = 0.5f),
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
