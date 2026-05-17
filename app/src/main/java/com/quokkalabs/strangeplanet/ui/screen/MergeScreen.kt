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
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.draw.clip
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
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
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
import com.quokkalabs.strangeplanet.data.model.CONSUME_MAX
import com.quokkalabs.strangeplanet.data.model.ConsumingOrb
import com.quokkalabs.strangeplanet.data.model.MergePhase
import com.quokkalabs.strangeplanet.data.model.MergeTier
import com.quokkalabs.strangeplanet.data.model.Orb
import com.quokkalabs.strangeplanet.data.model.POP_MAX
import com.quokkalabs.strangeplanet.data.model.Pop
import com.quokkalabs.strangeplanet.data.model.VOID_POP_MAX
import kotlin.math.cos
import kotlin.math.sin
import com.quokkalabs.strangeplanet.ui.components.CosmicBackground
import com.quokkalabs.strangeplanet.ui.components.ExitChoiceDialog
import com.quokkalabs.strangeplanet.ui.components.PauseOnBackground
import com.quokkalabs.strangeplanet.ui.components.ResumePrompt
import com.quokkalabs.strangeplanet.ui.theme.AlienPink
import com.quokkalabs.strangeplanet.ui.theme.DeepNavy
import com.quokkalabs.strangeplanet.ui.viewmodel.MergeViewModel

@Composable
fun MergeScreen(
    viewModel: MergeViewModel,
    onBack: () -> Unit,
) {
    val state by viewModel.state.collectAsState()
    val canUndo by viewModel.canUndo.collectAsState()
    val undoPenalty by viewModel.undoPenalty.collectAsState()
    val density = LocalDensity.current
    val view = LocalView.current

    DisposableEdgeToEdge(view)
    PauseOnBackground { /* physics pauses naturally when phase != PLAYING */ }

    var showExit by remember { mutableStateOf(false) }
    var showResume by remember { mutableStateOf(viewModel.hasSavedSession()) }
    var showSolarSystem by remember { mutableStateOf(false) }
    var showUndoConfirm by remember { mutableStateOf(false) }
    var showSweepConfirm by remember { mutableStateOf(false) }

    val sweepCost = MergeViewModel.sweepCost(state.orbs)
    val canSweep = state.orbs.any { it.tier in MergeViewModel.SWEEPABLE } &&
        state.score >= sweepCost

    fun attemptBack() {
        if (state.phase == MergePhase.PLAYING) {
            showExit = true
        } else {
            viewModel.resetGame()
            onBack()
        }
    }

    BackHandler { attemptBack() }

    val blockInput by rememberUpdatedState(
        showExit || showResume || showSolarSystem || showUndoConfirm || showSweepConfirm,
    )

    LaunchedEffect(blockInput) { viewModel.setPaused(blockInput) }

    CosmicBackground(showStars = true) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val screenWidthPx = with(density) { maxWidth.toPx() }
            val screenHeightPx = with(density) { maxHeight.toPx() }

            DisposableEffect(screenWidthPx, screenHeightPx) {
                viewModel.initGame(screenWidthPx, screenHeightPx)
                onDispose { viewModel.stopLoop() }
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        val sysGestureTopPx = with(density) { 32.dp.toPx() }
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = true)
                            if (blockInput) return@awaitEachGesture
                            // Ignore touches originating from system gesture zones (top/bottom edges)
                            val downY = down.position.y
                            val inSystemZone = downY < sysGestureTopPx ||
                                downY > screenHeightPx - with(density) { 56.dp.toPx() }
                            viewModel.onAim(down.position.x)
                            down.consume()
                            while (true) {
                                val event = awaitPointerEvent()
                                val ch = event.changes.firstOrNull() ?: break
                                ch.consume()
                                if (blockInput) break
                                viewModel.onAim(ch.position.x)
                                if (!ch.pressed) break
                            }
                            if (!inSystemZone && !blockInput) viewModel.onRelease()
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

                    // Orbs spiralling into a forming void
                    state.consuming.forEach {
                        drawConsuming(it, state.vesselRight - state.vesselLeft)
                    }

                    // Merge bursts (drawn on top of everything)
                    state.pops.forEach { drawPop(it) }
                }

                // ── HUD pill ────────────────────────────────────────────────
                Column(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 28.dp)
                        .background(DeepNavy.copy(alpha = 0.7f), RoundedCornerShape(16.dp))
                        .padding(horizontal = 20.dp, vertical = 8.dp)
                        .pointerInput(Unit) {
                            awaitEachGesture {
                                awaitFirstDown(requireUnconsumed = false).consume()
                                do {
                                    val event = awaitPointerEvent()
                                    event.changes.forEach { it.consume() }
                                } while (event.changes.any { it.pressed })
                            }
                        },
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(22.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        HudStat("MASS ACCUMULATED", state.score.toString())
                        HudStat("RECORD", state.highScore.toString())
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = { showSolarSystem = true },
                            )
                            .padding(4.dp),
                    ) {
                        val queue = listOf(state.nextTier) + state.upcoming.take(2)
                        for (idx in queue.indices) {
                            val tier = queue[idx]
                            val sizeDp = if (idx == 0) 40.dp else 28.dp
                            val alpha = if (idx == 0) 1f else if (idx == 1) 0.75f else 0.55f
                            Canvas(modifier = Modifier.size(sizeDp)) {
                                val r = size.minDimension / 2f
                                drawOrb(Orb(tier.ordinal.toLong() * 31L + idx.toLong(), tier, r, r), r, alphaMul = alpha)
                            }
                        }
                    }
                }

                // ── Merge / void name flash ────────────────────────────────
                MergeNameFlash(
                    name = state.lastMergeName,
                    emphatic = state.voidFlash,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 150.dp),
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
                    onClick = { attemptBack() },
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

                // ── Undo FAB ───────────────────────────────────────────────
                if (state.phase == MergePhase.PLAYING) {
                    FloatingActionButton(
                        onClick = { if (canUndo) showUndoConfirm = true },
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(bottom = 20.dp, end = 14.dp)
                            .size(44.dp),
                        shape = CircleShape,
                        containerColor = DeepNavy.copy(alpha = if (canUndo) 0.75f else 0.35f),
                        contentColor = AlienPink.copy(alpha = if (canUndo) 1f else 0.3f),
                    ) {
                        Text("↺", fontSize = 20.sp)
                    }
                }

                // ── Sweep FAB ──────────────────────────────────────────────
                if (state.phase == MergePhase.PLAYING) {
                    FloatingActionButton(
                        onClick = { if (canSweep) showSweepConfirm = true },
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(bottom = 20.dp, start = 14.dp)
                            .size(44.dp),
                        shape = CircleShape,
                        containerColor = DeepNavy.copy(alpha = if (canSweep) 0.75f else 0.35f),
                        contentColor = AlienPink.copy(alpha = if (canSweep) 1f else 0.3f),
                    ) {
                        Text("✦", fontSize = 18.sp)
                    }
                }

                // ── Solar system reference modal ───────────────────────────
                if (showSolarSystem) {
                    SolarSystemModal(onDismiss = { showSolarSystem = false })
                }

                // ── Undo confirmation ──────────────────────────────────────
                if (showUndoConfirm) {
                    UndoConfirmDialog(
                        penalty = undoPenalty,
                        onConfirm = { viewModel.undo(); showUndoConfirm = false },
                        onCancel = { showUndoConfirm = false },
                    )
                }

                // ── Sweep confirmation ─────────────────────────────────────
                if (showSweepConfirm) {
                    SweepConfirmDialog(
                        cost = sweepCost,
                        onConfirm = { viewModel.sweep(); showSweepConfirm = false },
                        onCancel = { showSweepConfirm = false },
                    )
                }

                if (showExit) {
                    ExitChoiceDialog(
                        canPreserve = true,
                        onAbandon = {
                            showExit = false
                            viewModel.discardSavedSession()
                            viewModel.resetGame()
                            onBack()
                        },
                        onCancel = { showExit = false },
                        onPreserve = {
                            showExit = false
                            viewModel.saveSession()
                            onBack()
                        },
                    )
                }

                if (showResume && state.phase == MergePhase.READY) {
                    ResumePrompt(
                        onResume = {
                            showResume = false
                            viewModel.resumeSession()
                        },
                        onFresh = {
                            showResume = false
                            viewModel.discardSavedSession()
                        },
                    )
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

    // Tier-specific surface detail, clipped to the orb so nothing spills.
    val orbPath = Path().apply {
        addOval(Rect(o.x - r, o.y - r, o.x + r, o.y + r))
    }
    clipPath(orbPath) {
        // Surface texture rotates with the body; lighting (below) does not.
        rotate(
            degrees = Math.toDegrees(o.angle.toDouble()).toFloat(),
            pivot = center,
        ) {
            when (o.tier) {
                MergeTier.DUST_MOTE, MergeTier.PEBBLE, MergeTier.BOULDER,
                MergeTier.MOONLET, MergeTier.MOON -> drawCraters(o, r, base, alphaMul)
                MergeTier.STRANGE_PLANET -> drawEarth(o, r, alphaMul)
                MergeTier.GAS_GIANT -> drawGasGiant(o, r, base, alphaMul)
                MergeTier.STAR -> drawStarSurface(o, r, alphaMul)
                MergeTier.NEUTRON_STAR -> drawNeutronCore(o, r, alphaMul)
                else -> {}
            }
        }
    }

    // Terminator shadow (light comes from the top-left)
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(Color.Transparent, Color.Transparent, darken(base).copy(alpha = 0.5f * alphaMul)),
            center = Offset(o.x - r * 0.3f, o.y - r * 0.3f),
            radius = r * 1.35f,
        ),
        radius = r,
        center = center,
    )

    // Specular highlight
    drawCircle(
        color = Color.White.copy(alpha = 0.22f * alphaMul),
        radius = r * 0.22f,
        center = Offset(o.x - r * 0.38f, o.y - r * 0.38f),
    )

    // Rim
    drawCircle(
        color = darken(base).copy(alpha = 0.5f * alphaMul),
        radius = r,
        center = center,
        style = Stroke(width = r * 0.06f),
    )
}

/** Deterministic 0..1 hash so an orb's surface stays stable across frames. */
private fun hash01(seed: Long, i: Int): Float {
    var h = seed * -0x61c8864680b583ebL + i * -0x7a143595L
    h = (h xor (h ushr 15)) * -0x3d4d51c2d82b14b1L
    h = h xor (h ushr 13)
    return (h and 0xFFFFL).toFloat() / 65535f
}

private fun DrawScope.drawCraters(o: Orb, r: Float, base: Color, a: Float) {
    // Soft mottling
    for (i in 0 until 4) {
        val ang = hash01(o.id, i + 200) * 6.2832f
        val dd = hash01(o.id, i + 250) * r * 0.65f
        drawCircle(
            color = darken(base).copy(alpha = 0.16f * a),
            radius = r * (0.12f + hash01(o.id, i + 300) * 0.16f),
            center = Offset(o.x + cos(ang) * dd, o.y + sin(ang) * dd),
        )
    }
    // Craters: shadowed pit + sunlit rim crescent
    for (i in 0 until 5) {
        val ang = hash01(o.id, i) * 6.2832f
        val dd = (0.12f + hash01(o.id, i + 50) * 0.58f) * r
        val cr = (0.09f + hash01(o.id, i + 100) * 0.15f) * r
        val cx = o.x + cos(ang) * dd
        val cy = o.y + sin(ang) * dd
        drawCircle(darken(base).copy(alpha = 0.55f * a), cr, Offset(cx, cy))
        drawCircle(
            color = lighten(base).copy(alpha = 0.5f * a),
            radius = cr,
            center = Offset(cx - cr * 0.26f, cy - cr * 0.26f),
            style = Stroke(width = cr * 0.32f),
        )
    }
}

private fun DrawScope.drawGasGiant(o: Orb, r: Float, base: Color, a: Float) {
    val bands = 7
    for (i in 0 until bands) {
        val fy = (i + 0.5f) / bands
        val yy = o.y - r + fy * 2f * r
        val bandH = (2f * r / bands) * 1.18f
        val shade = if (i % 2 == 0) lighten(base) else darken(base)
        drawRect(
            color = shade.copy(alpha = 0.42f * a),
            topLeft = Offset(o.x - r, yy - bandH / 2f),
            size = Size(2f * r, bandH),
        )
    }
    // The Great Spot — a storm churned FROM the atmosphere: same warm
    // palette as the bands, feathered with concentric low-contrast ovals
    // so the edges dissolve into the surrounding flow.
    val sx = o.x + r * 0.26f
    val sy = o.y + r * 0.24f
    val sw = r * 0.66f
    val sh = r * 0.40f
    val storm = darken(base)
    drawOval(
        color = storm.copy(alpha = 0.18f * a),
        topLeft = Offset(sx - sw * 0.64f, sy - sh * 0.64f),
        size = Size(sw * 1.28f, sh * 1.28f),
    )
    drawOval(
        color = storm.copy(alpha = 0.26f * a),
        topLeft = Offset(sx - sw * 0.5f, sy - sh * 0.5f),
        size = Size(sw, sh),
    )
    drawOval(
        color = Color(0xFFB85636).copy(alpha = 0.32f * a),
        topLeft = Offset(sx - sw * 0.38f, sy - sh * 0.38f),
        size = Size(sw * 0.76f, sh * 0.76f),
    )
    drawOval(
        color = Color(0xFFDB9A4E).copy(alpha = 0.30f * a),
        topLeft = Offset(sx - sw * 0.20f, sy - sh * 0.20f),
        size = Size(sw * 0.40f, sh * 0.40f),
    )
}

private fun DrawScope.drawEarth(o: Orb, r: Float, a: Float) {
    val land = Color(0xFF4F8B43)      // vegetated continent
    val landDry = Color(0xFFA98B53)   // arid / desert
    val ice = Color(0xFFEAF4FA)       // polar cap

    // Continents — clustered ragged landmasses over the ocean base
    for (i in 0 until 6) {
        val ang = hash01(o.id, i + 10) * 6.2832f
        val dd = hash01(o.id, i + 60) * r * 0.68f
        val cx = o.x + cos(ang) * dd
        val cy = o.y + sin(ang) * dd
        val br = r * (0.18f + hash01(o.id, i + 110) * 0.24f)
        val col = if (hash01(o.id, i + 140) > 0.66f) landDry else land
        drawCircle(col.copy(alpha = 0.88f * a), br, Offset(cx, cy))
        // satellite blob for a less circular coastline
        drawCircle(
            col.copy(alpha = 0.78f * a),
            br * 0.55f,
            Offset(cx + cos(ang) * br * 0.75f, cy + sin(ang) * br * 0.75f),
        )
    }

    // Polar ice caps at the top & bottom of the planet's axis
    drawOval(
        color = ice.copy(alpha = 0.92f * a),
        topLeft = Offset(o.x - r * 0.72f, o.y - r * 1.04f),
        size = Size(r * 1.44f, r * 0.56f),
    )
    drawOval(
        color = ice.copy(alpha = 0.92f * a),
        topLeft = Offset(o.x - r * 0.72f, o.y + r * 0.48f),
        size = Size(r * 1.44f, r * 0.56f),
    )

    // High clouds — soft white swirls drifting over land & sea
    for (i in 0 until 4) {
        val ang = hash01(o.id, i + 30) * 6.2832f
        val dd = hash01(o.id, i + 80) * r * 0.62f
        drawOval(
            color = Color.White.copy(alpha = 0.24f * a),
            topLeft = Offset(o.x + cos(ang) * dd - r * 0.52f, o.y + sin(ang) * dd - r * 0.14f),
            size = Size(r * 1.04f, r * 0.28f),
        )
    }
}

private fun DrawScope.drawStarSurface(o: Orb, r: Float, a: Float) {
    drawCircle(
        color = Color(0xFFFFF3C4).copy(alpha = 0.5f * a),
        radius = r * 0.6f,
        center = Offset(o.x - r * 0.12f, o.y - r * 0.12f),
    )
    for (i in 0 until 6) {
        val ang = hash01(o.id, i + 20) * 6.2832f
        val dd = hash01(o.id, i + 70) * r * 0.72f
        val sr = r * (0.10f + hash01(o.id, i + 120) * 0.14f)
        val hot = i % 2 == 0
        drawCircle(
            color = (if (hot) Color.White else Color(0xFFE8932B))
                .copy(alpha = (if (hot) 0.5f else 0.35f) * a),
            radius = sr,
            center = Offset(o.x + cos(ang) * dd, o.y + sin(ang) * dd),
        )
    }
}

private fun DrawScope.drawNeutronCore(o: Orb, r: Float, a: Float) {
    drawCircle(Color.White.copy(alpha = 0.85f * a), r * 0.42f, Offset(o.x, o.y))
    drawCircle(Color(0xFFCBE9FF).copy(alpha = 0.55f * a), r * 0.70f, Offset(o.x, o.y))
}

private fun DrawScope.drawConsuming(c: ConsumingOrb, vesselWidth: Float) {
    val t = (c.age / CONSUME_MAX.toFloat()).coerceIn(0f, 1f)
    // Accelerate inward (ease-in) and spiral around the void centre.
    val pull = t * t
    val baseR = c.tier.radiusFrac * vesselWidth
    val ang = t * 6.2832f * 1.5f
    val swirl = (1f - pull) * baseR * 1.4f
    val x = c.startX + (c.cx - c.startX) * pull + cos(ang) * swirl
    val y = c.startY + (c.cy - c.startY) * pull + sin(ang) * swirl
    val r = baseR * (1f - pull)
    if (r < 0.5f) return
    drawOrb(Orb(-2L, c.tier, x, y), r, alphaMul = (1f - t * 0.4f))
}

private fun DrawScope.drawPop(p: Pop) {
    val maxAge = if (p.big) VOID_POP_MAX else POP_MAX
    val t = (p.age / maxAge.toFloat()).coerceIn(0f, 1f)
    val ease = 1f - (1f - t) * (1f - t)        // fast then settle
    val fade = 1f - t
    val center = Offset(p.x, p.y)
    val tint = if (p.tier == MergeTier.BLACK_HOLE || p.tier == null) {
        Color(0xFFE8B4C8)
    } else {
        lighten(tierColor(p.tier))
    }
    val spread = if (p.big) 3.6f else 2.2f

    // Bright flash core — strong at first, gone quickly
    val coreFade = fade * fade
    if (coreFade > 0.02f) {
        drawCircle(
            color = Color.White.copy(alpha = 0.85f * coreFade),
            radius = p.radius * (0.55f + ease * 0.7f),
            center = center,
        )
    }

    // Expanding ring
    val ringR = p.radius * (1f + spread * ease)
    drawCircle(
        color = tint.copy(alpha = 0.85f * fade),
        radius = ringR,
        center = center,
        style = Stroke(width = (p.radius * 0.30f * fade).coerceAtLeast(1f)),
    )

    // Radiating sparks
    val sparks = if (p.big) 14 else 9
    val sparkR = p.radius * 0.16f * fade
    for (i in 0 until sparks) {
        val ang = (i.toFloat() / sparks) * 2f * Math.PI.toFloat()
        val dist = ringR * 0.96f
        drawCircle(
            color = tint.copy(alpha = 0.9f * fade),
            radius = sparkR.coerceAtLeast(0.5f),
            center = Offset(p.x + cos(ang) * dist, p.y + sin(ang) * dist),
        )
    }
}

private fun tierColor(t: MergeTier): Color = when (t) {
    MergeTier.DUST_MOTE -> Color(0xFFB59A6E)      // dusty ochre
    MergeTier.PEBBLE -> Color(0xFF5C7392)         // slate blue
    MergeTier.BOULDER -> Color(0xFFB05A34)        // rust brown
    MergeTier.MOONLET -> Color(0xFF7FA8A0)        // pale teal-grey
    MergeTier.MOON -> Color(0xFFD7E2F0)           // ivory blue
    MergeTier.STRANGE_PLANET -> Color(0xFF2F6FB0)  // ocean blue
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
private fun SweepConfirmDialog(
    cost: Int,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false).consume()
                    do {
                        val event = awaitPointerEvent()
                        event.changes.forEach { it.consume() }
                    } while (event.changes.any { it.pressed })
                }
            }
            .background(Color.Black.copy(alpha = 0.6f))
            .clickable(onClick = onCancel),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 300.dp)
                .fillMaxWidth(0.8f)
                .background(DeepNavy.copy(alpha = 0.95f), RoundedCornerShape(20.dp))
                .padding(horizontal = 24.dp, vertical = 22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                "SWEEP SMALL SPHERES?",
                color = AlienPink,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Clears the two smallest tiers.\nCosts $cost points.",
                color = Color.White.copy(alpha = 0.65f),
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(20.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(
                    "Cancel",
                    color = Color.White.copy(alpha = 0.5f),
                    fontSize = 14.sp,
                    modifier = Modifier
                        .clickable(onClick = onCancel)
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                )
                Text(
                    "Sweep (−$cost)",
                    color = AlienPink,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .clickable(onClick = onConfirm)
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                )
            }
        }
    }
}

@Composable
private fun UndoConfirmDialog(
    penalty: Int,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false).consume()
                    do {
                        val event = awaitPointerEvent()
                        event.changes.forEach { it.consume() }
                    } while (event.changes.any { it.pressed })
                }
            }
            .background(Color.Black.copy(alpha = 0.6f))
            .clickable(onClick = onCancel),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 300.dp)
                .fillMaxWidth(0.8f)
                .background(DeepNavy.copy(alpha = 0.95f), RoundedCornerShape(20.dp))
                .padding(horizontal = 24.dp, vertical = 22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                "UNDO LAST DROP?",
                color = AlienPink,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Costs $penalty points.",
                color = Color.White.copy(alpha = 0.65f),
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(20.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(
                    "Cancel",
                    color = Color.White.copy(alpha = 0.5f),
                    fontSize = 14.sp,
                    modifier = Modifier
                        .clickable(onClick = onCancel)
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                )
                Text(
                    "Undo (−$penalty)",
                    color = AlienPink,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .clickable(onClick = onConfirm)
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                )
            }
        }
    }
}

@Composable
private fun SolarSystemModal(onDismiss: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            // Absorb every pointer event — prevents drags reaching the game layer
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false).consume()
                    do {
                        val event = awaitPointerEvent()
                        event.changes.forEach { it.consume() }
                    } while (event.changes.any { it.pressed })
                }
            }
            .background(Color.Black.copy(alpha = 0.75f))
            .clickable(onClick = onDismiss),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 320.dp)
                .fillMaxWidth(0.85f)
                .background(DeepNavy.copy(alpha = 0.95f), RoundedCornerShape(20.dp))
                .padding(horizontal = 20.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                "SPHERES OF THE COSMOS",
                color = AlienPink,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(14.dp))
            MergeTier.entries.forEach { tier ->
                val mergeScore = (tier.ordinal + 1) * 5
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Canvas(modifier = Modifier.size(28.dp)) {
                        val r = size.minDimension / 2f
                        drawOrb(Orb(tier.ordinal.toLong(), tier, r, r), r)
                    }
                    Spacer(Modifier.width(12.dp))
                    Text(
                        tier.displayName,
                        color = Color.White.copy(alpha = 0.85f),
                        fontSize = 11.sp,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        "+$mergeScore",
                        color = AlienPink,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
            Spacer(Modifier.height(14.dp))
            Text(
                "tap to close",
                color = Color.White.copy(alpha = 0.6f),
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(Color.White.copy(alpha = 0.1f))
                    .clickable(onClick = onDismiss)
                    .padding(horizontal = 24.dp, vertical = 8.dp),
            )
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
