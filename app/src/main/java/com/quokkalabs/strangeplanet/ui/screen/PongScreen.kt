package com.quokkalabs.strangeplanet.ui.screen

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import com.quokkalabs.strangeplanet.data.model.BluetoothLobbyState
import com.quokkalabs.strangeplanet.data.model.BtConnectionState
import com.quokkalabs.strangeplanet.data.model.GameMode
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
    val btState by viewModel.btState.collectAsState()
    val density = LocalDensity.current

    // Bluetooth permission launcher
    val btPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { results ->
        viewModel.updateBtPermissions(results.values.all { it })
    }

    CosmicBackground(showStars = true) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val screenWidth = with(density) { maxWidth.toPx() }
            val screenHeight = with(density) { maxHeight.toPx() }

            LaunchedEffect(screenWidth, screenHeight) {
                viewModel.initGame(screenWidth, screenHeight)
            }

            // Touch handler (multi-touch for 2-player / BT)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        awaitPointerEventScope {
                            while (true) {
                                val event = awaitPointerEvent()
                                event.changes.forEach { change ->
                                    if (change.pressed) {
                                        viewModel.onTouch(change.position.x, change.position.y)
                                        if (state.phase == GamePhase.READY || state.phase == GamePhase.GAME_OVER) {
                                            viewModel.onTapToStart()
                                        }
                                    }
                                    change.consume()
                                }
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
                    GamePhase.READY -> ReadyOverlay(
                        gameMode = state.gameMode,
                        onModeSelected = { viewModel.selectMode(it) },
                        btState = btState,
                        onBtHost = { viewModel.btHost() },
                        onBtScan = { viewModel.btScan() },
                        onBtStopScan = { viewModel.btStopScan() },
                        onBtConnect = { viewModel.btConnect(it) },
                        onBtDisconnect = { viewModel.btDisconnect() },
                        onRequestBtPermissions = {
                            val perms = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                arrayOf(
                                    Manifest.permission.BLUETOOTH_CONNECT,
                                    Manifest.permission.BLUETOOTH_SCAN,
                                    Manifest.permission.BLUETOOTH_ADVERTISE,
                                )
                            } else {
                                arrayOf(
                                    Manifest.permission.BLUETOOTH,
                                    Manifest.permission.BLUETOOTH_ADMIN,
                                    Manifest.permission.ACCESS_FINE_LOCATION,
                                )
                            }
                            btPermissionLauncher.launch(perms)
                        },
                        modifier = Modifier.align(Alignment.Center),
                    )

                    GamePhase.GAME_OVER -> GameOverOverlay(
                        playerWon = state.playerScore > state.aiScore,
                        playerScore = state.playerScore,
                        aiScore = state.aiScore,
                        gameMode = state.gameMode,
                        modifier = Modifier.align(Alignment.Center),
                    )

                    else -> {
                        // Show disconnect warning during gameplay
                        if (state.gameMode == GameMode.BLUETOOTH &&
                            btState.connectionState != BtConnectionState.CONNECTED
                        ) {
                            SayingOverlay(
                                text = "Frequency link lost!",
                                modifier = Modifier
                                    .align(Alignment.Center)
                                    .offset(y = (-60).dp),
                            )
                        }
                    }
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

// ─── Game Canvas ─────────────────────────────────────────────────────────────

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

        // Ball glow + body
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
        } else {
            CardPink
        }
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
        } else {
            CardPink
        }
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

// ─── Creature Sprite ─────────────────────────────────────────────────────────

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

// ─── Saying Overlay ──────────────────────────────────────────────────────────

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

// ─── Ready Overlay ───────────────────────────────────────────────────────────

@Composable
private fun ReadyOverlay(
    gameMode: GameMode,
    onModeSelected: (GameMode) -> Unit,
    btState: BluetoothLobbyState,
    onBtHost: () -> Unit,
    onBtScan: () -> Unit,
    onBtStopScan: () -> Unit,
    onBtConnect: (String) -> Unit,
    onBtDisconnect: () -> Unit,
    onRequestBtPermissions: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .widthIn(max = 300.dp)
            .heightIn(max = 520.dp)
            .background(DeepNavy.copy(alpha = 0.8f), RoundedCornerShape(20.dp))
            .padding(28.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            "Recreational\nSphere Deflection",
            color = AlienPink,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(18.dp))

        // Mode selector
        Text(
            "Select participants:",
            color = Color.White.copy(alpha = 0.5f),
            fontSize = 13.sp,
        )
        Spacer(Modifier.height(8.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ModeButton("1 Being", gameMode == GameMode.SINGLE_PLAYER) {
                onModeSelected(GameMode.SINGLE_PLAYER)
            }
            ModeButton("2 Beings", gameMode == GameMode.TWO_PLAYER) {
                onModeSelected(GameMode.TWO_PLAYER)
            }
            ModeButton("Distant", gameMode == GameMode.BLUETOOTH) {
                onModeSelected(GameMode.BLUETOOTH)
            }
        }

        Spacer(Modifier.height(16.dp))

        if (gameMode == GameMode.BLUETOOTH) {
            BluetoothLobby(
                btState = btState,
                onHost = onBtHost,
                onScan = onBtScan,
                onStopScan = onBtStopScan,
                onConnect = onBtConnect,
                onDisconnect = onBtDisconnect,
                onRequestPermissions = onRequestBtPermissions,
            )
        } else {
            Text(
                "Tap to commence",
                color = Color.White.copy(alpha = 0.6f),
                fontSize = 15.sp,
            )
        }
    }
}

// ─── Bluetooth Lobby ─────────────────────────────────────────────────────────

@Composable
private fun BluetoothLobby(
    btState: BluetoothLobbyState,
    onHost: () -> Unit,
    onScan: () -> Unit,
    onStopScan: () -> Unit,
    onConnect: (String) -> Unit,
    onDisconnect: () -> Unit,
    onRequestPermissions: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        when {
            !btState.available -> {
                Text(
                    "This device lacks\nfrequency transmission hardware.",
                    color = Color.White.copy(alpha = 0.6f),
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                )
            }

            !btState.permissionsGranted -> {
                Text(
                    "Frequency access\nauthorization required.",
                    color = Color.White.copy(alpha = 0.6f),
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(10.dp))
                LobbyButton("Grant Access") { onRequestPermissions() }
            }

            !btState.enabled -> {
                Text(
                    "Activate your frequency\ntransmitter in device settings.",
                    color = Color.White.copy(alpha = 0.6f),
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                )
            }

            else -> when (btState.connectionState) {
                BtConnectionState.IDLE -> {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        LobbyButton("Broadcast\nPresence") { onHost() }
                        LobbyButton("Detect\nBeings") { onScan() }
                    }

                    // Paired devices
                    if (btState.pairedDevices.isNotEmpty()) {
                        Spacer(Modifier.height(12.dp))
                        Text(
                            "Known Beings:",
                            color = Color.White.copy(alpha = 0.4f),
                            fontSize = 11.sp,
                        )
                        Spacer(Modifier.height(4.dp))
                        btState.pairedDevices.forEach { device ->
                            Text(
                                text = device.name,
                                color = SoftPink,
                                fontSize = 13.sp,
                                modifier = Modifier
                                    .clickable { onConnect(device.address) }
                                    .background(
                                        Color.White.copy(alpha = 0.08f),
                                        RoundedCornerShape(8.dp),
                                    )
                                    .padding(horizontal = 12.dp, vertical = 6.dp)
                                    .fillMaxWidth(),
                                textAlign = TextAlign.Center,
                            )
                            Spacer(Modifier.height(4.dp))
                        }
                    }
                }

                BtConnectionState.HOSTING -> {
                    Text(
                        "Broadcasting presence...\nAwaiting distant being...",
                        color = Color.White.copy(alpha = 0.6f),
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(10.dp))
                    LobbyButton("Cancel") { onDisconnect() }
                }

                BtConnectionState.SCANNING -> {
                    Text(
                        "Scanning for beings...",
                        color = Color.White.copy(alpha = 0.6f),
                        fontSize = 13.sp,
                    )
                    Spacer(Modifier.height(6.dp))

                    // Discovered devices
                    btState.discoveredDevices.forEach { device ->
                        Text(
                            text = device.name,
                            color = AlienPink,
                            fontSize = 13.sp,
                            modifier = Modifier
                                .clickable { onConnect(device.address) }
                                .background(
                                    Color.White.copy(alpha = 0.08f),
                                    RoundedCornerShape(8.dp),
                                )
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                                .fillMaxWidth(),
                            textAlign = TextAlign.Center,
                        )
                        Spacer(Modifier.height(4.dp))
                    }

                    // Paired devices
                    if (btState.pairedDevices.isNotEmpty()) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "Known Beings:",
                            color = Color.White.copy(alpha = 0.4f),
                            fontSize = 11.sp,
                        )
                        Spacer(Modifier.height(4.dp))
                        btState.pairedDevices.forEach { device ->
                            Text(
                                text = device.name,
                                color = SoftPink.copy(alpha = 0.7f),
                                fontSize = 13.sp,
                                modifier = Modifier
                                    .clickable { onConnect(device.address) }
                                    .background(
                                        Color.White.copy(alpha = 0.05f),
                                        RoundedCornerShape(8.dp),
                                    )
                                    .padding(horizontal = 12.dp, vertical = 6.dp)
                                    .fillMaxWidth(),
                                textAlign = TextAlign.Center,
                            )
                            Spacer(Modifier.height(4.dp))
                        }
                    }

                    Spacer(Modifier.height(6.dp))
                    LobbyButton("Stop") { onStopScan() }
                }

                BtConnectionState.CONNECTING -> {
                    Text(
                        "Establishing\nfrequency link...",
                        color = Color.White.copy(alpha = 0.6f),
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center,
                    )
                }

                BtConnectionState.CONNECTED -> {
                    Text(
                        "Frequency link established!",
                        color = SoftPink,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.Center,
                    )
                    btState.connectedDeviceName?.let { name ->
                        Text(
                            "Connected to: $name",
                            color = Color.White.copy(alpha = 0.5f),
                            fontSize = 12.sp,
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "Tap to commence",
                        color = Color.White.copy(alpha = 0.6f),
                        fontSize = 15.sp,
                    )
                }
            }
        }
    }
}

// ─── Mode & Lobby Buttons ────────────────────────────────────────────────────

@Composable
private fun ModeButton(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val bgColor = if (selected) SoftPink else Color.White.copy(alpha = 0.15f)
    val textColor = if (selected) Color.White else Color.White.copy(alpha = 0.6f)

    Text(
        text = label,
        color = textColor,
        fontSize = 13.sp,
        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .background(bgColor, RoundedCornerShape(10.dp))
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 10.dp),
    )
}

@Composable
private fun LobbyButton(
    label: String,
    onClick: () -> Unit,
) {
    Text(
        text = label,
        color = Color.White,
        fontSize = 13.sp,
        fontWeight = FontWeight.Medium,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .background(SoftPink.copy(alpha = 0.6f), RoundedCornerShape(10.dp))
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 10.dp),
    )
}

// ─── Game Over Overlay ───────────────────────────────────────────────────────

@Composable
private fun GameOverOverlay(
    playerWon: Boolean,
    playerScore: Int,
    aiScore: Int,
    gameMode: GameMode,
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
            when (gameMode) {
                GameMode.SINGLE_PLAYER -> {
                    if (playerWon) "You have achieved\nsphere deflection supremacy!"
                    else "Your opponent's implement\ntechnique proved superior."
                }

                GameMode.TWO_PLAYER -> {
                    if (playerWon) "The lower being has achieved\nsphere deflection supremacy!"
                    else "The upper being's implement\ntechnique proved superior!"
                }

                GameMode.BLUETOOTH -> {
                    if (playerWon) "You have achieved\nsphere deflection supremacy!"
                    else "The distant being's implement\ntechnique proved superior!"
                }
            },
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
