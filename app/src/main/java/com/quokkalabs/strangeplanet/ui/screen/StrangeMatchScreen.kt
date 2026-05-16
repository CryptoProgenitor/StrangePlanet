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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
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
import com.quokkalabs.strangeplanet.ui.components.PauseOnBackground
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

    BackHandler {
        viewModel.resetGame()
        onBack()
    }

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
            val cellSizePx = minOf(
                screenWidthPx / SM_COLS,
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
                    .padding(top = 140.dp, bottom = 16.dp),
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
                                            .clickable { viewModel.onCellTapped(row, col) },
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
                fontSize = 9.sp,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(1.dp),
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
                .background(DeepNavy.copy(alpha = 0.88f), RoundedCornerShape(20.dp))
                .padding(horizontal = 32.dp, vertical = 24.dp)
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
                    color = Color.White.copy(alpha = 0.55f),
                    fontSize = 12.sp,
                    modifier = Modifier
                        .clickable(onClick = onAbandon)
                        .padding(4.dp),
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
