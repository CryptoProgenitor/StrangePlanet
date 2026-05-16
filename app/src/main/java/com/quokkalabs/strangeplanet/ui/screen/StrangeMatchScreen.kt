package com.quokkalabs.strangeplanet.ui.screen

import android.graphics.BitmapFactory
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import kotlin.math.abs
import kotlin.math.hypot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.quokkalabs.strangeplanet.R
import com.quokkalabs.strangeplanet.data.model.SM_COLS
import com.quokkalabs.strangeplanet.data.model.SM_ROWS
import com.quokkalabs.strangeplanet.data.model.StrangeMatchPhase
import com.quokkalabs.strangeplanet.data.model.Tile
import com.quokkalabs.strangeplanet.data.model.TileKind
import com.quokkalabs.strangeplanet.data.model.TileType
import com.quokkalabs.strangeplanet.ui.components.CosmicBackground
import com.quokkalabs.strangeplanet.ui.components.ExitChoiceDialog
import com.quokkalabs.strangeplanet.ui.components.PauseOnBackground
import com.quokkalabs.strangeplanet.ui.components.ResumePrompt
import com.quokkalabs.strangeplanet.ui.theme.AlienPink
import com.quokkalabs.strangeplanet.ui.theme.DeepNavy
import com.quokkalabs.strangeplanet.ui.viewmodel.StrangeMatchViewModel

@Composable
fun StrangeMatchScreen(
    viewModel: StrangeMatchViewModel,
    onBack: () -> Unit,
) {
    val state by viewModel.state.collectAsState()
    val density = LocalDensity.current
    val view = LocalView.current
    val context = LocalContext.current

    DisposableEdgeToEdge(view)

    PauseOnBackground { /* no-op for now */ }

    var showExit by remember { mutableStateOf(false) }
    var showResume by remember { mutableStateOf(viewModel.hasSavedSession()) }

    fun attemptBack() {
        if (state.phase == StrangeMatchPhase.PLAYING ||
            state.phase == StrangeMatchPhase.ANIMATING
        ) {
            showExit = true
        } else {
            viewModel.resetGame()
            onBack()
        }
    }

    BackHandler { attemptBack() }

    LaunchedEffect(Unit) { viewModel.onEnterScreen() }

    // Load all 7 tile bitmaps once at screen level
    val bitmaps = remember {
        mapOf(
            TileType.STAR to BitmapFactory.decodeResource(context.resources, R.drawable.sp_star).asImageBitmap(),
            TileType.DOG to BitmapFactory.decodeResource(context.resources, R.drawable.sp_dog).asImageBitmap(),
            TileType.ROLLSUCK to BitmapFactory.decodeResource(context.resources, R.drawable.sp_rollsuck).asImageBitmap(),
            TileType.UNICORN to BitmapFactory.decodeResource(context.resources, R.drawable.sp_unicorn).asImageBitmap(),
            TileType.ALIEN_DAD to BitmapFactory.decodeResource(context.resources, R.drawable.sp_alien_dad).asImageBitmap(),
            TileType.CAT to BitmapFactory.decodeResource(context.resources, R.drawable.sp_cat).asImageBitmap(),
            TileType.SOCKS to BitmapFactory.decodeResource(context.resources, R.drawable.sp_socks).asImageBitmap(),
        )
    }

    CosmicBackground(showStars = true) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val screenWidthPx = with(density) { maxWidth.toPx() }
            val screenHeightPx = with(density) { maxHeight.toPx() }
            val gridHPadPx = with(density) { 24.dp.toPx() } // 12dp each side
            val cellSizePx = minOf(
                (screenWidthPx - gridHPadPx) / SM_COLS,
                screenHeightPx * 0.78f / SM_ROWS,
            )
            val cellSizeDp = with(density) { cellSizePx.toDp() }

            // HUD pill
            Row(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 76.dp)
                    .background(
                        DeepNavy.copy(alpha = 0.75f),
                        RoundedCornerShape(14.dp),
                    )
                    .padding(horizontal = 22.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(20.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                HudStat("SUSTENANCE", state.score.toString())
                HudStat("RECORD", state.highScore.toString())
                HudStat("MOVES", state.movesLeft.coerceAtLeast(0).toString())
                HudStat("TIER", state.level.toString())
            }

            // Grid centered below HUD
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(start = 12.dp, end = 12.dp, top = 140.dp)
                    .windowInsetsPadding(WindowInsets.navigationBars),
                contentAlignment = Alignment.Center,
            ) {
                if (state.grid.isNotEmpty()) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(0.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        for (row in 0 until SM_ROWS) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(0.dp),
                            ) {
                                for (col in 0 until SM_COLS) {
                                    val tile = state.grid.getOrNull(row)?.getOrNull(col)
                                    val isSelected = state.selectedCell == (row to col)
                                    val isMatched = (row to col) in state.matchedCells
                                    val isBombExplosion = (row to col) in state.bombExplosionCells

                                    Box(
                                        modifier = Modifier
                                            .size(cellSizeDp)
                                            .padding(2.dp)
                                            .pointerInput(row, col) {
                                                val swipeMin = 18.dp.toPx()
                                                awaitEachGesture {
                                                    awaitFirstDown(requireUnconsumed = false)
                                                    var dx = 0f; var dy = 0f
                                                    while (true) {
                                                        val event = awaitPointerEvent()
                                                        val ch = event.changes.firstOrNull() ?: break
                                                        dx += ch.position.x - ch.previousPosition.x
                                                        dy += ch.position.y - ch.previousPosition.y
                                                        ch.consume()
                                                        if (!ch.pressed) break
                                                    }
                                                    if (hypot(dx, dy) < swipeMin) {
                                                        viewModel.onCellTapped(row, col)
                                                    } else {
                                                        val (dr, dc) = if (abs(dx) > abs(dy))
                                                            0 to if (dx > 0) 1 else -1
                                                        else
                                                            if (dy > 0) 1 to 0 else -1 to 0
                                                        viewModel.onSwipeTo(row, col, row + dr, col + dc)
                                                    }
                                                }
                                            },
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        if (tile != null) {
                                            TileCell(
                                                tile = tile,
                                                bitmap = bitmaps[tile.type],
                                                cellSizePx = cellSizePx,
                                                isSelected = isSelected,
                                                isMatched = isMatched,
                                                isBombExplosion = isBombExplosion,
                                            )
                                        } else {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxSize()
                                                    .background(
                                                        Color.White.copy(alpha = 0.04f),
                                                        RoundedCornerShape(10.dp),
                                                    ),
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Phase overlays
            AnimatedVisibility(
                visible = state.phase == StrangeMatchPhase.READY,
                enter = fadeIn(tween(150)),
                exit = fadeOut(tween(150)),
            ) {
                SmCenterBanner(
                    title = "STRANGE MATCH",
                    subtitle = "Swap tiles to match 3 or more.\nTap to commence.",
                    onAbandon = { viewModel.resetGame(); onBack() },
                    onTap = { viewModel.startGame() },
                )
            }

            AnimatedVisibility(
                visible = state.phase == StrangeMatchPhase.GAME_OVER,
                enter = fadeIn(tween(150)),
                exit = fadeOut(tween(150)),
            ) {
                SmCenterBanner(
                    title = "PURSUIT CONCLUDED",
                    subtitle = "Sustenance ${state.score} · Record ${state.highScore}\nTap to attempt again.",
                    onTap = { viewModel.startGame() },
                )
            }

            // Back button FAB
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

            if (showResume && state.phase == StrangeMatchPhase.READY) {
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

@Composable
private fun TileCell(
    tile: Tile,
    bitmap: androidx.compose.ui.graphics.ImageBitmap?,
    cellSizePx: Float,
    isSelected: Boolean,
    isMatched: Boolean,
    isBombExplosion: Boolean,
) {
    val bgColor = tileColor(tile.type)
    val spriteSizeDp = with(LocalDensity.current) { (cellSizePx * 0.70f).toDp() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(bgColor, RoundedCornerShape(10.dp))
            .then(
                if (isSelected) Modifier.border(2.dp, AlienPink, RoundedCornerShape(10.dp))
                else Modifier
            ),
        contentAlignment = Alignment.Center,
    ) {
        // Matched overlay
        if (isMatched) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.White.copy(alpha = 0.6f), RoundedCornerShape(10.dp)),
            )
        }
        // Bomb explosion overlay
        if (isBombExplosion) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(AlienPink.copy(alpha = 0.45f), RoundedCornerShape(10.dp)),
            )
        }
        // Sprite image
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = tile.type.name,
                modifier = Modifier.size(spriteSizeDp),
                contentScale = ContentScale.Fit,
            )
        }
        // Bomb indicator
        if (tile.kind == TileKind.BOMB) {
            Text(
                text = "✦",
                color = Color.Yellow,
                fontSize = 11.sp,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(2.dp),
            )
        }
    }
}

@Composable
private fun BoxScope.SmCenterBanner(
    title: String,
    subtitle: String,
    onAbandon: (() -> Unit)? = null,
    onTap: (() -> Unit)? = null,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .then(if (onTap != null) Modifier.clickable { onTap() } else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 340.dp)
                .fillMaxWidth(0.9f)
                .background(DeepNavy.copy(alpha = 0.88f), RoundedCornerShape(20.dp))
                .padding(horizontal = 28.dp, vertical = 24.dp)
                .clickable(enabled = false) {},
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

private fun tileColor(type: TileType) = when (type) {
    TileType.STAR -> Color(0xFF6B35B8)
    TileType.DOG -> Color(0xFFBF2020)
    TileType.ROLLSUCK -> Color(0xFFCC3680)
    TileType.UNICORN -> Color(0xFF8B5FBF)
    TileType.ALIEN_DAD -> Color(0xFF2255AA)
    TileType.CAT -> Color(0xFF2E7D4F)
    TileType.SOCKS -> Color(0xFFB8740A)
}

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
