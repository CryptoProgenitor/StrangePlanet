package com.quokkalabs.strangeplanet.ui.screen

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.gestures.awaitFirstDown
import com.quokkalabs.strangeplanet.data.model.Tetromino
import com.quokkalabs.strangeplanet.data.model.TetrisPhase
import com.quokkalabs.strangeplanet.data.model.TetrisState
import com.quokkalabs.strangeplanet.data.model.TetroType
import com.quokkalabs.strangeplanet.domain.TetrisEngine
import com.quokkalabs.strangeplanet.ui.theme.AlienPink
import com.quokkalabs.strangeplanet.ui.viewmodel.TetrisViewModel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlin.math.min

// ── Tetromino colours (Strange Planet palette) ────────────────────────────────

private fun TetroType.toColor() = when (this) {
    TetroType.I -> Color(0xFF00E5FF)
    TetroType.O -> Color(0xFFFFD66B)
    TetroType.T -> AlienPink
    TetroType.S -> Color(0xFF6BCB77)
    TetroType.Z -> Color(0xFFFF6B6B)
    TetroType.J -> Color(0xFF6B9FD4)
    TetroType.L -> Color(0xFFFFB347)
}

// ── Block draw helper ─────────────────────────────────────────────────────────

private fun DrawScope.drawCell(color: Color, x: Float, y: Float, sz: Float, alpha: Float = 1f) {
    val inset = sz * 0.05f
    val inner = sz - inset * 2f
    val corner = CornerRadius(sz * 0.18f, sz * 0.18f)
    drawRoundRect(
        brush = Brush.radialGradient(
            listOf(Color.White.copy(alpha = 0.30f * alpha), color.copy(alpha = alpha), color.copy(alpha = 0.65f * alpha)),
            center = Offset(x + inset + inner * 0.3f, y + inset + inner * 0.3f),
            radius = inner * 0.9f,
        ),
        topLeft = Offset(x + inset, y + inset),
        size = Size(inner, inner),
        cornerRadius = corner,
    )
}

// ── Press-and-hold button helper ──────────────────────────────────────────────

@Composable
private fun HoldButton(
    label: String,
    modifier: Modifier = Modifier,
    onDown: () -> Unit = {},
    onUp: () -> Unit = {},
) {
    var pressed by remember { mutableStateOf(false) }
    Box(
        modifier = modifier
            .background(
                if (pressed) AlienPink.copy(alpha = 0.30f) else AlienPink.copy(alpha = 0.12f),
                RoundedCornerShape(14.dp),
            )
            .pointerInput(Unit) {
                while (true) {
                    awaitPointerEventScope {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        down.consume()
                        pressed = true
                        onDown()
                        // Wait for all pointers to be released
                        do {
                            val ev = awaitPointerEvent()
                        } while (ev.changes.any { it.pressed })
                        pressed = false
                        onUp()
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = if (pressed) AlienPink else AlienPink.copy(0.65f), fontSize = 22.sp)
    }
}

@Composable
private fun TapButton(label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    var pressed by remember { mutableStateOf(false) }
    Box(
        modifier = modifier
            .background(
                if (pressed) AlienPink.copy(alpha = 0.30f) else AlienPink.copy(alpha = 0.12f),
                RoundedCornerShape(14.dp),
            )
            .pointerInput(Unit) {
                while (true) {
                    awaitPointerEventScope {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        down.consume()
                        pressed = true
                        do { awaitPointerEvent() } while (awaitPointerEvent().changes.any { it.pressed })
                        pressed = false
                        onClick()
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = if (pressed) AlienPink else AlienPink.copy(0.65f), fontSize = 22.sp)
    }
}

// ── Screen ────────────────────────────────────────────────────────────────────

@Composable
fun TetrisScreen(viewModel: TetrisViewModel, onBack: () -> Unit) {
    val state by viewModel.state.collectAsState()
    val density = LocalDensity.current
    val stateRef by rememberUpdatedState(state)

    val clearAlpha = remember { Animatable(0f) }
    LaunchedEffect(state.clearingRows) {
        if (state.clearingRows.isNotEmpty()) {
            clearAlpha.snapTo(1f)
            clearAlpha.animateTo(0f, tween(360))
        }
    }

    BackHandler { onBack() }

    DisposableEffect(Unit) {
        viewModel.startGame()
        onDispose { viewModel.stopLoop() }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0xFF0E0820)),
    ) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val sw = with(density) { maxWidth.toPx() }
            val sh = with(density) { maxHeight.toPx() }
            val ctrlH = with(density) { 100.dp.toPx() }
            val statusH = with(density) { WindowInsets.statusBars.getTop(this) }

            val availH = sh - ctrlH - statusH - with(density) { 12.dp.toPx() }
            val gridW  = min(sw * 0.60f, availH / 2f)
            val gridH  = gridW * 2f
            val cellSz = gridW / TetrisEngine.COLS
            val gridLeft = with(density) { 12.dp.toPx() }
            val gridTop  = statusH + (availH - gridH) / 2f

            val panelLeft = gridLeft + gridW + with(density) { 10.dp.toPx() }
            val panelW    = sw - panelLeft - with(density) { 8.dp.toPx() }

            // ── Main canvas ───────────────────────────────────────────────
            Canvas(Modifier.fillMaxSize()) {
                // Starfield
                val rng = java.util.Random(54321L)
                repeat(25) {
                    drawCircle(
                        Color.White.copy(alpha = 0.08f + rng.nextFloat() * 0.15f),
                        rng.nextFloat() * 1.0f + 0.4f,
                        Offset(rng.nextFloat() * size.width, rng.nextFloat() * size.height),
                    )
                }

                // Grid background
                drawRoundRect(
                    Color.White.copy(alpha = 0.04f),
                    Offset(gridLeft - 2f, gridTop - 2f),
                    Size(gridW + 4f, gridH + 4f),
                    CornerRadius(6f, 6f),
                )
                // Grid lines
                for (c in 0..TetrisEngine.COLS) {
                    drawLine(Color.White.copy(alpha = 0.06f),
                        Offset(gridLeft + c * cellSz, gridTop),
                        Offset(gridLeft + c * cellSz, gridTop + gridH))
                }
                for (r in 0..TetrisEngine.ROWS) {
                    drawLine(Color.White.copy(alpha = 0.06f),
                        Offset(gridLeft, gridTop + r * cellSz),
                        Offset(gridLeft + gridW, gridTop + r * cellSz))
                }

                // Locked cells
                for (r in 0 until TetrisEngine.ROWS) {
                    for (c in 0 until TetrisEngine.COLS) {
                        val type = state.grid[r][c] ?: continue
                        drawCell(type.toColor(), gridLeft + c * cellSz, gridTop + r * cellSz, cellSz)
                    }
                }

                // Clear-row flash
                val ca = clearAlpha.value
                if (ca > 0.01f) {
                    state.clearingRows.forEach { r ->
                        val y = gridTop + r * cellSz
                        drawRect(AlienPink.copy(alpha = ca * 0.70f), Offset(gridLeft, y), Size(gridW, cellSz))
                        drawRect(Color.White.copy(alpha = ca * 0.25f), Offset(gridLeft, y), Size(gridW, cellSz))
                    }
                }

                // Ghost piece
                val active = state.active
                if (active != null && state.phase != TetrisPhase.CLEARING) {
                    val engine = TetrisEngine()
                    val ghostR = engine.ghostRow(state.grid, active)
                    if (ghostR > active.row) {
                        engine.cells(active.copy(row = ghostR)).forEach { (r, c) ->
                            if (r in 0 until TetrisEngine.ROWS) {
                                val inset = cellSz * 0.05f
                                val inner = cellSz - inset * 2f
                                drawRoundRect(
                                    active.type.toColor().copy(alpha = 0.22f),
                                    Offset(gridLeft + c * cellSz + inset, gridTop + r * cellSz + inset),
                                    Size(inner, inner),
                                    CornerRadius(cellSz * 0.18f, cellSz * 0.18f),
                                    style = Stroke(1.5f),
                                )
                            }
                        }
                    }
                    // Active piece
                    val eng2 = TetrisEngine()
                    eng2.cells(active).forEach { (r, c) ->
                        if (r in 0 until TetrisEngine.ROWS) {
                            drawCell(active.type.toColor(), gridLeft + c * cellSz, gridTop + r * cellSz, cellSz)
                        }
                    }
                }

                // ── Right panel ────────────────────────────────────────────
                val py = gridTop

                // NEXT label
                drawRoundRect(
                    Color.White.copy(alpha = 0.05f),
                    Offset(panelLeft, py),
                    Size(panelW, panelW),
                    CornerRadius(8f, 8f),
                )
                drawRoundRect(
                    Color.White.copy(alpha = 0.10f),
                    Offset(panelLeft, py),
                    Size(panelW, panelW),
                    CornerRadius(8f, 8f),
                    style = Stroke(1f),
                )

                // Next piece preview (4×4 preview area, centered in panel)
                val previewCellSz = panelW * 0.90f / 4f
                val nextCells = TetrisEngine().cells(TetrisEngine().spawn(state.next))
                val minR = nextCells.minOf { it.first }
                val minC = nextCells.minOf { it.second }
                val maxR = nextCells.maxOf { it.first }
                val maxC = nextCells.maxOf { it.second }
                val pieceH = (maxR - minR + 1) * previewCellSz
                val pieceW = (maxC - minC + 1) * previewCellSz
                val previewOriginX = panelLeft + (panelW - pieceW) / 2f - minC * previewCellSz
                val previewOriginY = py + (panelW - pieceH) / 2f - minR * previewCellSz
                nextCells.forEach { (r, c) ->
                    drawCell(state.next.toColor(), previewOriginX + c * previewCellSz, previewOriginY + r * previewCellSz, previewCellSz)
                }
            }

            // ── Right panel text (score/level/lines) ──────────────────────
            val panelTopDp = with(density) { (gridTop + with(density) { panelW } + 12.dp.toPx()).toDp() }
            Column(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(
                        start = with(density) { panelLeft.toDp() } + 4.dp,
                        top = with(density) { (gridTop + panelW + 14.dp.toPx()).toDp() },
                    ),
            ) {
                listOf(
                    "SCORE" to "${state.score}",
                    "BEST" to "${state.highScore}",
                    "LEVEL" to "${state.level}",
                    "LINES" to "${state.lines}",
                ).forEach { (label, value) ->
                    Text(label, color = Color.White.copy(0.35f), fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp)
                    Text(value, color = AlienPink, fontSize = 18.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 8.dp))
                }
            }

            // NEXT label above preview panel
            Text(
                "NEXT",
                color = Color.White.copy(0.35f),
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.8.sp,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(
                        start = with(density) { panelLeft.toDp() } + 4.dp,
                        top = with(density) { (gridTop - 14.dp.toPx()).toDp() },
                    ),
            )

            // ── Control buttons ───────────────────────────────────────────
            val ctrlTopDp = with(density) { (sh - ctrlH).toDp() }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 12.dp, vertical = 12.dp)
                    .height(with(density) { ctrlH.toDp() } - 24.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                HoldButton("◀", Modifier.weight(1f).fillMaxSize().padding(end = 4.dp),
                    onDown = { viewModel.setLeftDown(true) },
                    onUp   = { viewModel.setLeftDown(false) })
                HoldButton("▶", Modifier.weight(1f).fillMaxSize().padding(horizontal = 4.dp),
                    onDown = { viewModel.setRightDown(true) },
                    onUp   = { viewModel.setRightDown(false) })
                HoldButton("▼", Modifier.weight(1f).fillMaxSize().padding(horizontal = 4.dp),
                    onDown = { viewModel.setSoftDrop(true) },
                    onUp   = { viewModel.setSoftDrop(false) })
                TapButton("↺", Modifier.weight(1f).fillMaxSize().padding(horizontal = 4.dp),
                    onClick = { viewModel.onRotate() })
                TapButton("⬇", Modifier.weight(1f).fillMaxSize().padding(start = 4.dp),
                    onClick = { viewModel.onHardDrop() })
            }

            // ── Game-over overlay ─────────────────────────────────────────
            if (state.phase == TetrisPhase.GAME_OVER) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.78f))
                        .windowInsetsPadding(WindowInsets.statusBars),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("SECTOR COLLAPSE", color = AlienPink, fontSize = 20.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                        Text("The fragments reached the summit.", color = Color.White.copy(0.50f), fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp))
                        Text("${state.score}", color = AlienPink, fontSize = 60.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 20.dp), lineHeight = 62.sp)
                        Text("MASS DESCENDED", color = AlienPink.copy(0.50f), fontSize = 11.sp, letterSpacing = 1.5.sp)
                        if (state.score > 0 && state.score == state.highScore) {
                            Text("⬥  NEW RECORD  ⬥", color = Color(0xFFFFD66B), fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))
                        }
                        Box(
                            modifier = Modifier
                                .padding(top = 28.dp)
                                .background(AlienPink.copy(0.18f), RoundedCornerShape(14.dp))
                                .pointerInput(Unit) {
                                    awaitPointerEventScope {
                                        awaitFirstDown()
                                        viewModel.startGame()
                                    }
                                }
                                .padding(horizontal = 44.dp, vertical = 14.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text("▶  REDESCEND", color = AlienPink, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        }
                        Text(
                            "Abandon Descent",
                            color = Color.White.copy(0.38f),
                            fontSize = 13.sp,
                            modifier = Modifier
                                .padding(top = 16.dp)
                                .pointerInput(Unit) {
                                    awaitPointerEventScope { awaitFirstDown(); onBack() }
                                }
                                .padding(8.dp),
                        )
                    }
                }
            }
        }
    }
}
