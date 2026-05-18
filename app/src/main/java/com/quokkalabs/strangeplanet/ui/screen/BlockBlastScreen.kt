package com.quokkalabs.strangeplanet.ui.screen

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import com.quokkalabs.strangeplanet.data.model.BlockBlastPhase
import com.quokkalabs.strangeplanet.data.model.BlockColor
import com.quokkalabs.strangeplanet.data.model.BlockPiece
import com.quokkalabs.strangeplanet.domain.BlockBlastEngine
import com.quokkalabs.strangeplanet.ui.theme.AlienPink
import com.quokkalabs.strangeplanet.ui.viewmodel.BlockBlastViewModel
import kotlin.math.min
import kotlin.math.roundToInt

// ── Colour mapping ────────────────────────────────────────────────────────────

private fun BlockColor.toColor() = when (this) {
    BlockColor.PINK   -> AlienPink
    BlockColor.CORAL  -> Color(0xFFFF6B6B)
    BlockColor.CYAN   -> Color(0xFF00E5FF)
    BlockColor.GOLD   -> Color(0xFFFFD66B)
    BlockColor.SLATE  -> Color(0xFF9FB6E0)
    BlockColor.VIOLET -> Color(0xFF9B7FB8)
}

// ── Shared block draw helper ──────────────────────────────────────────────────

private fun DrawScope.drawBlock(color: Color, x: Float, y: Float, sz: Float) {
    val inset = sz * 0.055f
    val inner = sz - inset * 2f
    val corner = CornerRadius(sz * 0.22f, sz * 0.22f)
    drawRoundRect(
        brush = Brush.radialGradient(
            listOf(Color.White.copy(alpha = 0.32f), color, color.copy(alpha = 0.72f)),
            center = Offset(x + inset + inner * 0.28f, y + inset + inner * 0.28f),
            radius = inner * 0.95f,
        ),
        topLeft = Offset(x + inset, y + inset),
        size = Size(inner, inner),
        cornerRadius = corner,
    )
}

// ── Drag state ────────────────────────────────────────────────────────────────

private data class DragSession(
    val trayIndex: Int,
    val piece: BlockPiece,
    val touchX: Float,
    val touchY: Float,
    val ghostRow: Int = -1,
    val ghostCol: Int = -1,
)

// ── Screen ────────────────────────────────────────────────────────────────────

@Composable
fun BlockBlastScreen(
    viewModel: BlockBlastViewModel,
    onBack: () -> Unit,
) {
    val state by viewModel.state.collectAsState()
    val density = LocalDensity.current
    val stateRef by rememberUpdatedState(state)

    // Animate the clear-line flash
    val clearAlpha = remember { Animatable(0f) }
    LaunchedEffect(state.justCleared) {
        if (state.justCleared.isNotEmpty()) {
            clearAlpha.snapTo(1f)
            clearAlpha.animateTo(0f, tween(340))
        }
    }

    var drag by remember { mutableStateOf<DragSession?>(null) }

    BackHandler { onBack() }

    // Kick off a fresh game when the screen first opens
    LaunchedEffect(Unit) {
        if (state.phase == BlockBlastPhase.IDLE) viewModel.startGame()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0E0820)),
    ) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val w = with(density) { maxWidth.toPx() }
            val h = with(density) { maxHeight.toPx() }

            // ── Layout constants ──────────────────────────────────────────
            val topPad   = with(density) { 72.dp.toPx() }   // clears header row
            val trayH    = with(density) { 116.dp.toPx() }
            val botPad   = with(density) { 20.dp.toPx() }
            val gap      = with(density) { 10.dp.toPx() }

            val gridSz   = min(w * 0.96f, h - topPad - gap - trayH - botPad)
            val cellSz   = gridSz / BlockBlastEngine.GRID
            val gridLeft = (w - gridSz) / 2f
            val gridTop  = topPad + gap
            val trayTop  = gridTop + gridSz + gap
            val slotW    = w / 3f

            // How far above trayTop we still count a drag start as "in tray"
            val traySlop = with(density) { 28.dp.toPx() }

            // ── Game canvas ───────────────────────────────────────────────
            Canvas(Modifier.fillMaxSize()) {
                // Starfield
                val rng = java.util.Random(77777L)
                repeat(30) {
                    drawCircle(
                        Color.White.copy(alpha = 0.10f + rng.nextFloat() * 0.18f),
                        rng.nextFloat() * 1.1f + 0.4f,
                        Offset(rng.nextFloat() * size.width, rng.nextFloat() * size.height),
                    )
                }

                // Grid background panel
                drawRoundRect(
                    Color.White.copy(alpha = 0.04f),
                    topLeft = Offset(gridLeft - 5f, gridTop - 5f),
                    size = Size(gridSz + 10f, gridSz + 10f),
                    cornerRadius = CornerRadius(14f, 14f),
                )

                // Grid lines
                repeat(BlockBlastEngine.GRID + 1) { i ->
                    val x = gridLeft + i * cellSz
                    val y = gridTop + i * cellSz
                    drawLine(Color.White.copy(alpha = 0.07f), Offset(x, gridTop), Offset(x, gridTop + gridSz))
                    drawLine(Color.White.copy(alpha = 0.07f), Offset(gridLeft, y), Offset(gridLeft + gridSz, y))
                }

                // Placed blocks
                for (r in 0 until BlockBlastEngine.GRID) {
                    for (c in 0 until BlockBlastEngine.GRID) {
                        val color = state.grid[r][c] ?: continue
                        drawBlock(color.toColor(), gridLeft + c * cellSz, gridTop + r * cellSz, cellSz)
                    }
                }

                // Clear-line flash
                val ca = clearAlpha.value
                if (ca > 0.01f) {
                    state.justCleared.forEach { (r, c) ->
                        val bx = gridLeft + c * cellSz
                        val by = gridTop + r * cellSz
                        val inset = cellSz * 0.055f
                        val inner = cellSz - inset * 2f
                        drawRoundRect(
                            AlienPink.copy(alpha = ca * 0.85f),
                            Offset(bx + inset, by + inset),
                            Size(inner, inner),
                            CornerRadius(cellSz * 0.22f, cellSz * 0.22f),
                        )
                        drawCircle(
                            AlienPink.copy(alpha = ca * 0.30f),
                            cellSz * 0.65f,
                            Offset(bx + cellSz / 2f, by + cellSz / 2f),
                        )
                    }
                }

                // Ghost preview on the grid
                val d = drag
                if (d != null && d.ghostRow >= 0) {
                    val valid = viewModel.canPlace(d.trayIndex, d.ghostRow, d.ghostCol)
                    d.piece.cells.forEach { (dr, dc) ->
                        val bx = gridLeft + (d.ghostCol + dc) * cellSz
                        val by = gridTop + (d.ghostRow + dr) * cellSz
                        val inset = cellSz * 0.055f
                        val inner = cellSz - inset * 2f
                        val corner = CornerRadius(cellSz * 0.22f, cellSz * 0.22f)
                        if (valid) {
                            drawRoundRect(
                                d.piece.color.toColor().copy(alpha = 0.42f),
                                Offset(bx + inset, by + inset), Size(inner, inner), corner,
                            )
                        } else {
                            drawRoundRect(
                                Color.White.copy(alpha = 0.12f),
                                Offset(bx + inset, by + inset), Size(inner, inner), corner,
                                style = Stroke(1.5f),
                            )
                        }
                    }
                }

                // Tray slots + pieces
                for (slot in 0..2) {
                    val cx = slotW * (slot + 0.5f)
                    val cy = trayTop + trayH / 2f
                    val r  = min(slotW, trayH) * 0.42f
                    val isDragging = (slot == drag?.trayIndex)

                    drawRoundRect(
                        Color.White.copy(alpha = if (isDragging) 0.02f else 0.05f),
                        Offset(cx - r, cy - r), Size(r * 2f, r * 2f),
                        CornerRadius(r * 0.28f, r * 0.28f),
                    )
                    drawRoundRect(
                        Color.White.copy(alpha = 0.11f),
                        Offset(cx - r, cy - r), Size(r * 2f, r * 2f),
                        CornerRadius(r * 0.28f, r * 0.28f),
                        style = Stroke(1f),
                    )

                    val piece = state.tray.getOrNull(slot) ?: continue
                    if (isDragging) continue

                    val pRows = piece.cells.maxOf { it.first } + 1
                    val pCols = piece.cells.maxOf { it.second } + 1
                    val area  = r * 2f * 0.78f
                    val pCell = min(area / pCols, area / pRows)
                    val pLeft = cx - pCols * pCell / 2f
                    val pTop  = cy - pRows * pCell / 2f
                    piece.cells.forEach { (dr, dc) ->
                        drawBlock(piece.color.toColor(), pLeft + dc * pCell, pTop + dr * pCell, pCell)
                    }
                }

                // Dragged piece — follows ghost on grid or floats above finger
                if (d != null) {
                    val piece  = d.piece
                    val pRows  = piece.cells.maxOf { it.first } + 1
                    val pCols  = piece.cells.maxOf { it.second } + 1
                    val (ox, oy) = if (d.ghostRow >= 0) {
                        gridLeft + d.ghostCol * cellSz to gridTop + d.ghostRow * cellSz
                    } else {
                        d.touchX - pCols * cellSz / 2f to d.touchY - (pRows + 1.1f) * cellSz
                    }
                    piece.cells.forEach { (dr, dc) ->
                        drawBlock(piece.color.toColor(), ox + dc * cellSz, oy + dr * cellSz, cellSz)
                    }
                    // Lift glow
                    drawCircle(
                        piece.color.toColor().copy(alpha = 0.18f),
                        cellSz * 1.1f,
                        Offset(ox + pCols * cellSz / 2f, oy + pRows * cellSz / 2f),
                    )
                }
            }

            // ── Score header ──────────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "← EXIT",
                    color = Color.White.copy(alpha = 0.32f),
                    fontSize = 12.sp,
                    modifier = Modifier
                        .clickable { onBack() }
                        .padding(4.dp),
                )
                Spacer(Modifier.weight(1f))
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "TERRITORIAL CONFIGURATION",
                        color = AlienPink.copy(alpha = 0.50f),
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp,
                    )
                    Text(
                        "${state.score}",
                        color = AlienPink,
                        fontSize = 30.sp,
                        fontWeight = FontWeight.Bold,
                        lineHeight = 32.sp,
                    )
                }
                Spacer(Modifier.weight(1f))
                Column(horizontalAlignment = Alignment.End) {
                    Text("BEST", color = Color.White.copy(0.28f), fontSize = 9.sp)
                    Text(
                        "${state.highScore}",
                        color = Color.White.copy(0.50f),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }

            // ── Drag gesture layer ────────────────────────────────────────
            Box(
                Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragStart = { offset ->
                                if (stateRef.phase != BlockBlastPhase.PLAYING) return@detectDragGestures
                                if (offset.y < trayTop - traySlop) return@detectDragGestures
                                val slot = (offset.x / slotW).toInt().coerceIn(0, 2)
                                val piece = stateRef.tray.getOrNull(slot) ?: return@detectDragGestures
                                drag = DragSession(slot, piece, offset.x, offset.y)
                            },
                            onDrag = { change, _ ->
                                change.consume()
                                val d = drag ?: return@detectDragGestures
                                val tx = change.position.x
                                val ty = change.position.y
                                val pRows = d.piece.cells.maxOf { it.first } + 1
                                val pCols = d.piece.cells.maxOf { it.second } + 1
                                val gr = ((ty - gridTop) / cellSz - pRows / 2f)
                                    .roundToInt().coerceIn(0, BlockBlastEngine.GRID - pRows)
                                val gc = ((tx - gridLeft) / cellSz - pCols / 2f)
                                    .roundToInt().coerceIn(0, BlockBlastEngine.GRID - pCols)
                                val overGrid = ty < gridTop + gridSz + cellSz * 0.6f &&
                                    tx >= gridLeft - cellSz * 0.5f &&
                                    tx <= gridLeft + gridSz + cellSz * 0.5f
                                drag = d.copy(
                                    touchX = tx, touchY = ty,
                                    ghostRow = if (overGrid) gr else -1,
                                    ghostCol = if (overGrid) gc else -1,
                                )
                            },
                            onDragEnd = {
                                val d = drag ?: return@detectDragGestures
                                drag = null
                                if (d.ghostRow >= 0) viewModel.tryPlace(d.trayIndex, d.ghostRow, d.ghostCol)
                            },
                            onDragCancel = { drag = null },
                        )
                    },
            )

            // ── Game-over overlay ─────────────────────────────────────────
            if (state.phase == BlockBlastPhase.GAME_OVER) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.76f))
                        .windowInsetsPadding(WindowInsets.statusBars)
                        .windowInsetsPadding(WindowInsets.navigationBars),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            "CONFIGURATION COMPLETE",
                            color = AlienPink,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                        )
                        Text(
                            "The void has reclaimed the grid.",
                            color = Color.White.copy(alpha = 0.50f),
                            fontSize = 13.sp,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                        Text(
                            "${state.score}",
                            color = AlienPink,
                            fontSize = 60.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(top = 20.dp),
                            lineHeight = 62.sp,
                        )
                        Text(
                            "MASS ARRANGED",
                            color = AlienPink.copy(alpha = 0.50f),
                            fontSize = 11.sp,
                            letterSpacing = 1.5.sp,
                        )
                        if (state.score > 0 && state.score == state.highScore) {
                            Text(
                                "⬥  NEW RECORD  ⬥",
                                color = Color(0xFFFFD66B),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(top = 8.dp),
                            )
                        }
                        Box(
                            modifier = Modifier
                                .padding(top = 28.dp)
                                .background(AlienPink.copy(alpha = 0.18f), RoundedCornerShape(14.dp))
                                .clickable { viewModel.startGame() }
                                .padding(horizontal = 44.dp, vertical = 14.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                "▶  RECONFIGURE",
                                color = AlienPink,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                        Text(
                            "Abandon Configuration",
                            color = Color.White.copy(alpha = 0.38f),
                            fontSize = 13.sp,
                            modifier = Modifier
                                .padding(top = 16.dp)
                                .clickable { onBack() }
                                .padding(8.dp),
                        )
                    }
                }
            }
        }
    }
}
