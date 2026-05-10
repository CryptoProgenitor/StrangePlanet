package com.quokkalabs.strangeplanet.ui.screen

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.quokkalabs.strangeplanet.R
import com.quokkalabs.strangeplanet.data.model.GamePhase
import com.quokkalabs.strangeplanet.data.model.GameSide
import com.quokkalabs.strangeplanet.data.model.PongGameState
import com.quokkalabs.strangeplanet.ui.components.CosmicBackground
import com.quokkalabs.strangeplanet.ui.theme.AlienPink
import com.quokkalabs.strangeplanet.ui.theme.CardPink
import com.quokkalabs.strangeplanet.ui.theme.DeepNavy
import com.quokkalabs.strangeplanet.ui.theme.SoftPink
import com.quokkalabs.strangeplanet.ui.viewmodel.PongViewModel

@Composable
fun PongScreen(
    viewModel: PongViewModel,
    onBack: () -> Unit,
) {
    val state by viewModel.gameState.collectAsState()
    val density = LocalDensity.current

    CosmicBackground(showStars = true) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val screenWidth = with(density) { maxWidth.toPx() }
            val screenHeight = with(density) { maxHeight.toPx() }

            LaunchedEffect(screenWidth, screenHeight) {
                viewModel.initGame(screenWidth, screenHeight)
            }

            // Touch handler
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        awaitPointerEventScope {
                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull() ?: continue
                                if (change.pressed) {
                                    viewModel.onTouch(change.position.x)
                                    if (state.phase == GamePhase.READY || state.phase == GamePhase.GAME_OVER) {
                                        viewModel.onTapToStart()
                                    }
                                }
                                change.consume()
                            }
                        }
                    },
            )

            if (state.screenWidth > 0f) {
                // Game canvas
                GameCanvas(state = state)

                // AI creature (top, flipped)
                PongCreature(
                    paddleX = state.aiPaddleX,
                    paddleY = state.aiPaddleY,
                    isFlipped = true,
                    hitPulse = state.aiHitPulse,
                )

                // Player creature (bottom)
                PongCreature(
                    paddleX = state.playerPaddleX,
                    paddleY = state.playerPaddleY,
                    isFlipped = false,
                    hitPulse = state.playerHitPulse,
                )

                // Score
                Text(
                    text = "${state.aiScore}  —  ${state.playerScore}",
                    color = Color.White.copy(alpha = 0.25f),
                    fontSize = 44.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.align(Alignment.Center),
                )

                // Active saying
                state.activeSaying?.let { (side, text) ->
                    val yFraction = if (side == GameSide.AI) 0.25f else 0.72f
                    SayingOverlay(
                        text = text,
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .offset(
                                y = with(density) { (screenHeight * yFraction).toDp() },
                            ),
                    )
                }

                // Phase overlays
                when (state.phase) {
                    GamePhase.READY -> ReadyOverlay(Modifier.align(Alignment.Center))
                    GamePhase.GAME_OVER -> GameOverOverlay(
                        playerWon = state.playerScore > state.aiScore,
                        playerScore = state.playerScore,
                        aiScore = state.aiScore,
                        modifier = Modifier.align(Alignment.Center),
                    )
                    else -> {}
                }

                // Back button
                FloatingActionButton(
                    onClick = onBack,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(top = 24.dp, start = 12.dp)
                        .size(36.dp),
                    shape = CircleShape,
                    containerColor = AlienPink.copy(alpha = 0.5f),
                    contentColor = DeepNavy,
                ) {
                    Text("←", fontSize = 14.sp)
                }
            }
        }
    }
}

@Composable
private fun GameCanvas(state: PongGameState) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        // Center dashed line
        val dashLen = 20f
        val gapLen = 15f
        val cy = size.height / 2f
        var dx = 0f
        while (dx < size.width) {
            drawLine(
                color = Color.White.copy(alpha = 0.12f),
                start = Offset(dx, cy),
                end = Offset((dx + dashLen).coerceAtMost(size.width), cy),
                strokeWidth = 2f,
            )
            dx += dashLen + gapLen
        }

        // Trail
        state.trail.forEachIndexed { i, pt ->
            val progress = i.toFloat() / state.trail.size.coerceAtLeast(1)
            val alpha = (1f - progress) * 0.35f
            val r = state.ballRadius * (1f - progress * 0.5f)
            drawCircle(
                color = SoftPink.copy(alpha = alpha),
                radius = r,
                center = Offset(pt.x, pt.y),
            )
        }

        // Ball glow
        if (state.phase == GamePhase.PLAYING || state.phase == GamePhase.POINT_SCORED) {
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        SoftPink.copy(alpha = 0.35f),
                        SoftPink.copy(alpha = 0.08f),
                        Color.Transparent,
                    ),
                    center = Offset(state.ballX, state.ballY),
                    radius = state.ballRadius * 3.5f,
                ),
                radius = state.ballRadius * 3.5f,
                center = Offset(state.ballX, state.ballY),
            )

            // Ball body
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(AlienPink, SoftPink),
                    center = Offset(
                        state.ballX - state.ballRadius * 0.25f,
                        state.ballY - state.ballRadius * 0.25f,
                    ),
                    radius = state.ballRadius * 1.5f,
                ),
                radius = state.ballRadius,
                center = Offset(state.ballX, state.ballY),
            )
        }

        // Player paddle bar
        val pColor = if (state.playerHitPulse > 0f) {
            lerp(CardPink, Color.White, state.playerHitPulse * 0.5f)
        } else CardPink

        drawRoundRect(
            color = pColor,
            topLeft = Offset(
                state.playerPaddleX - state.paddleWidth / 2f,
                state.playerPaddleY - state.paddleHeight / 2f,
            ),
            size = Size(state.paddleWidth, state.paddleHeight),
            cornerRadius = CornerRadius(state.paddleHeight / 2f),
        )

        // AI paddle bar
        val aColor = if (state.aiHitPulse > 0f) {
            lerp(CardPink, Color.White, state.aiHitPulse * 0.5f)
        } else CardPink

        drawRoundRect(
            color = aColor,
            topLeft = Offset(
                state.aiPaddleX - state.paddleWidth / 2f,
                state.aiPaddleY - state.paddleHeight / 2f,
            ),
            size = Size(state.paddleWidth, state.paddleHeight),
            cornerRadius = CornerRadius(state.paddleHeight / 2f),
        )
    }
}

@Composable
private fun PongCreature(
    paddleX: Float,
    paddleY: Float,
    isFlipped: Boolean,
    hitPulse: Float,
) {
    val density = LocalDensity.current
    val creatureSize = 70.dp
    val creatureSizePx = with(density) { creatureSize.toPx() }
    val scale = 1f + hitPulse * 0.12f

    val yOffset = if (isFlipped) {
        paddleY - creatureSizePx * 0.55f
    } else {
        paddleY + creatureSizePx * 0.05f
    }

    Image(
        painter = painterResource(id = R.drawable.sp_pong_player),
        contentDescription = "Sphere Deflection Being",
        modifier = Modifier
            .offset(
                x = with(density) { (paddleX - creatureSizePx / 2f).toDp() },
                y = with(density) { (yOffset - creatureSizePx / 2f).toDp() },
            )
            .size(creatureSize)
            .graphicsLayer {
                scaleX = scale
                scaleY = if (isFlipped) -scale else scale
            },
    )
}

@Composable
private fun SayingOverlay(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        color = Color.White,
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        textAlign = TextAlign.Center,
        modifier = modifier
            .background(DeepNavy.copy(alpha = 0.75f), RoundedCornerShape(12.dp))
            .padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

@Composable
private fun ReadyOverlay(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .background(DeepNavy.copy(alpha = 0.8f), RoundedCornerShape(20.dp))
            .padding(36.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            "Recreational\nSphere Deflection",
            color = AlienPink,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(16.dp))
        Text(
            "Tap to commence",
            color = Color.White.copy(alpha = 0.6f),
            fontSize = 15.sp,
        )
    }
}

@Composable
private fun GameOverOverlay(
    playerWon: Boolean,
    playerScore: Int,
    aiScore: Int,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .background(DeepNavy.copy(alpha = 0.85f), RoundedCornerShape(20.dp))
            .padding(36.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            "The Contest Has Concluded",
            color = AlienPink,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            if (playerWon) "You have achieved\nsphere deflection supremacy!"
            else "Your opponent's implement\ntechnique proved superior.",
            color = Color.White.copy(alpha = 0.8f),
            fontSize = 15.sp,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "$playerScore — $aiScore",
            color = SoftPink,
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(16.dp))
        Text(
            "Tap for rematch",
            color = Color.White.copy(alpha = 0.5f),
            fontSize = 14.sp,
        )
    }
}
