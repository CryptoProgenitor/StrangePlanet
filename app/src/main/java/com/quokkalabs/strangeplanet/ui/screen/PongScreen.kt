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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.quokkalabs.strangeplanet.R
import com.quokkalabs.strangeplanet.data.model.BluetoothLobbyState
import com.quokkalabs.strangeplanet.data.model.BtConnectionState
import com.quokkalabs.strangeplanet.data.model.BtRole
import com.quokkalabs.strangeplanet.data.model.DifficultyLevel
import com.quokkalabs.strangeplanet.data.model.GameMode
import com.quokkalabs.strangeplanet.data.model.GamePhase
import com.quokkalabs.strangeplanet.data.model.GameSide
import com.quokkalabs.strangeplanet.data.model.OnlineConnectionState
import com.quokkalabs.strangeplanet.data.model.OnlineLobbyState
import com.quokkalabs.strangeplanet.data.model.PongGameState
import com.quokkalabs.strangeplanet.data.model.PongSettings
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
    val onlineState by viewModel.onlineState.collectAsState()
    val settings by viewModel.pongSettings.collectAsState()
    val playerCreature by viewModel.playerCreature.collectAsState()
    val opponentCreature by viewModel.opponentCreature.collectAsState()
    val player2Creature by viewModel.player2Creature.collectAsState()
    var showSettings by remember { mutableStateOf(false) }
    var showExitConfirm by remember { mutableStateOf(false) }
    val density = LocalDensity.current
    val view = LocalView.current

    // Immersive sticky mode — hide system bars during gameplay
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

                // Top creature (AI / player 2)
                val topCreature = if (state.gameMode == GameMode.SINGLE_PLAYER) opponentCreature else player2Creature
                PongCreature(
                    drawableRes = topCreature,
                    paddleX = state.aiPaddleX,
                    paddleY = state.aiPaddleY,
                    isFlipped = true,
                    hitPulse = state.aiHitPulse,
                )

                // Player creature (bottom)
                PongCreature(
                    drawableRes = playerCreature,
                    paddleX = state.playerPaddleX,
                    paddleY = state.playerPaddleY,
                    isFlipped = false,
                    hitPulse = state.playerHitPulse,
                )

                // Score with creature icons
                Row(
                    modifier = Modifier.align(Alignment.Center),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                ) {
                    // Top player's creature icon (left side)
                    Image(
                        painter = painterResource(id = topCreature),
                        contentDescription = null,
                        modifier = Modifier
                            .size(28.dp)
                            .graphicsLayer { alpha = 0.35f },
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "${state.aiScore}  —  ${state.playerScore}",
                        color = Color.White.copy(alpha = 0.25f),
                        fontSize = 44.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.width(6.dp))
                    // Bottom player's creature icon (right side)
                    Image(
                        painter = painterResource(id = playerCreature),
                        contentDescription = null,
                        modifier = Modifier
                            .size(28.dp)
                            .graphicsLayer { alpha = 0.35f },
                    )
                }

                // Active saying (gated by settings)
                if (settings.showSayings) {
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
                }

                // Phase overlays
                when (state.phase) {
                    GamePhase.READY -> ReadyOverlay(
                        gameMode = state.gameMode,
                        onModeSelected = { viewModel.selectMode(it) },
                        onConfigure = { showSettings = true },
                        selectedCreature = playerCreature,
                        onSelectCreature = { viewModel.selectCreature(it) },
                        selectedPlayer2Creature = player2Creature,
                        onSelectPlayer2Creature = { viewModel.selectPlayer2Creature(it) },
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
                        onlineState = onlineState,
                        onOnlineCreate = { viewModel.onlineCreateRoom() },
                        onOnlineJoin = { viewModel.onlineJoinRoom(it) },
                        onOnlineDisconnect = { viewModel.onlineDisconnect() },
                        modifier = Modifier.align(Alignment.Center),
                    )

                    GamePhase.GAME_OVER -> GameOverOverlay(
                        playerWon = state.playerScore > state.aiScore,
                        playerScore = state.playerScore,
                        aiScore = state.aiScore,
                        gameMode = state.gameMode,
                        modifier = Modifier.align(Alignment.Center),
                    )

                    GamePhase.PAUSED -> {
                        // Scrim
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = 0.4f)),
                        )
                        Column(
                            modifier = Modifier.align(Alignment.Center),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(
                                "Activity Suspended",
                                color = AlienPink,
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center,
                            )
                            Spacer(Modifier.height(16.dp))
                            Text(
                                text = "Tap to Proceed",
                                color = Color.White,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Medium,
                                textAlign = TextAlign.Center,
                                modifier = Modifier
                                    .background(
                                        SoftPink.copy(alpha = 0.6f),
                                        RoundedCornerShape(12.dp),
                                    )
                                    .clickable { viewModel.requestPause() }
                                    .padding(horizontal = 24.dp, vertical = 12.dp),
                            )
                        }
                    }

                    else -> {
                        // Show disconnect warning during gameplay
                        val showDisconnect = when (state.gameMode) {
                            GameMode.BLUETOOTH -> btState.connectionState != BtConnectionState.CONNECTED
                            GameMode.ONLINE -> onlineState.connectionState != OnlineConnectionState.CONNECTED
                            else -> false
                        }
                        if (showDisconnect) {
                            SayingOverlay(
                                text = "Frequency link lost!",
                                modifier = Modifier
                                    .align(Alignment.Center)
                                    .offset(y = (-60).dp),
                            )
                        }
                    }
                }

                // Pause button (top-right, during gameplay)
                if (state.phase == GamePhase.PLAYING) {
                    FloatingActionButton(
                        onClick = { viewModel.requestPause() },
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(top = 24.dp, end = 12.dp)
                            .size(36.dp),
                        shape = CircleShape,
                        containerColor = AlienPink.copy(alpha = 0.5f),
                        contentColor = DeepNavy,
                    ) {
                        Text("⏸", fontSize = 12.sp)
                    }
                }

                // Back button
                FloatingActionButton(
                    onClick = {
                        val phase = state.phase
                        if (phase == GamePhase.READY || phase == GamePhase.GAME_OVER) {
                            if (state.gameMode == GameMode.BLUETOOTH) viewModel.btDisconnect()
                            if (state.gameMode == GameMode.ONLINE) viewModel.onlineDisconnect()
                            viewModel.resetGame(); onBack()
                        } else {
                            if (phase == GamePhase.PLAYING) viewModel.requestPause()
                            showExitConfirm = true
                        }
                    },
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

                // Exit confirmation
                if (showExitConfirm) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.5f))
                            .clickable { /* block taps */ },
                    )
                    Column(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .background(DeepNavy.copy(alpha = 0.95f), RoundedCornerShape(20.dp))
                            .padding(28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            "Abandon Contest?",
                            color = AlienPink,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Progress will not\nbe preserved.",
                            color = Color.White.copy(alpha = 0.6f),
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center,
                        )
                        Spacer(Modifier.height(20.dp))
                        Text(
                            text = "Confirm Departure",
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .background(AlienPink.copy(alpha = 0.7f), RoundedCornerShape(12.dp))
                                .clickable {
                                    showExitConfirm = false
                                    when (state.gameMode) {
                                        GameMode.BLUETOOTH -> viewModel.quitBtGame()
                                        GameMode.ONLINE -> viewModel.quitOnlineGame()
                                        else -> viewModel.resetGame()
                                    }
                                    onBack()
                                }
                                .padding(horizontal = 20.dp, vertical = 12.dp),
                        )
                        Spacer(Modifier.height(10.dp))
                        Text(
                            text = "Resume Activity",
                            color = Color.White.copy(alpha = 0.6f),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .background(Color.White.copy(alpha = 0.1f), RoundedCornerShape(12.dp))
                                .clickable { showExitConfirm = false; viewModel.requestPause() }
                                .padding(horizontal = 20.dp, vertical = 10.dp),
                        )
                    }
                }

                // Settings modal
                if (showSettings) {
                    SettingsModal(
                        settings = settings,
                        onSoundToggle = { viewModel.setSoundEnabled(it) },
                        onSayingsToggle = { viewModel.setShowSayings(it) },
                        onDifficulty = { viewModel.setDifficulty(it) },
                        onDismiss = { showSettings = false },
                        modifier = Modifier.align(Alignment.Center),
                    )
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
        if (state.phase == GamePhase.PLAYING || state.phase == GamePhase.POINT_SCORED || state.phase == GamePhase.PAUSED) {
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
    drawableRes: Int,
    paddleX: Float,
    paddleY: Float,
    isFlipped: Boolean,
    hitPulse: Float,
) {
    val density = LocalDensity.current
    val creatureSize = 70.dp
    val creatureSizePx = with(density) { creatureSize.toPx() }
    val scale = 1f + hitPulse * 0.12f

    // Sprite edge sits flush against the paddle
    val yOffset = if (isFlipped) {
        paddleY - creatureSizePx * 0.5f   // sprite bottom edge at paddle
    } else {
        paddleY + creatureSizePx * 0.5f   // sprite top edge at paddle
    }

    Image(
        painter = painterResource(id = drawableRes),
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
    onConfigure: () -> Unit,
    selectedCreature: Int,
    onSelectCreature: (Int) -> Unit,
    selectedPlayer2Creature: Int,
    onSelectPlayer2Creature: (Int) -> Unit,
    btState: BluetoothLobbyState,
    onBtHost: () -> Unit,
    onBtScan: () -> Unit,
    onBtStopScan: () -> Unit,
    onBtConnect: (String) -> Unit,
    onBtDisconnect: () -> Unit,
    onRequestBtPermissions: () -> Unit,
    onlineState: OnlineLobbyState = OnlineLobbyState(),
    onOnlineCreate: () -> Unit = {},
    onOnlineJoin: (String) -> Unit = {},
    onOnlineDisconnect: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .widthIn(max = 300.dp)
            .fillMaxHeight(0.85f)
            .background(DeepNavy.copy(alpha = 0.8f), RoundedCornerShape(20.dp))
            .padding(24.dp)
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
        Spacer(Modifier.height(14.dp))

        // Mode selector
        Text(
            "Select participants:",
            color = Color.White.copy(alpha = 0.5f),
            fontSize = 12.sp,
        )
        Spacer(Modifier.height(6.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Max),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            ModeButton("1 Being", gameMode == GameMode.SINGLE_PLAYER, Modifier.weight(1f).fillMaxHeight()) {
                onModeSelected(GameMode.SINGLE_PLAYER)
            }
            ModeButton("2 Beings", gameMode == GameMode.TWO_PLAYER, Modifier.weight(1f).fillMaxHeight()) {
                onModeSelected(GameMode.TWO_PLAYER)
            }
            ModeButton("Nearby", gameMode == GameMode.BLUETOOTH, Modifier.weight(1f).fillMaxHeight()) {
                onModeSelected(GameMode.BLUETOOTH)
            }
            ModeButton("Remote", gameMode == GameMode.ONLINE, Modifier.weight(1f).fillMaxHeight()) {
                onModeSelected(GameMode.ONLINE)
            }
        }

        Spacer(Modifier.height(12.dp))

        // Creature picker(s)
        val isBtClient = gameMode == GameMode.BLUETOOTH &&
            btState.role == BtRole.CLIENT &&
            btState.connectionState == BtConnectionState.CONNECTED
        val isOnlineClient = gameMode == GameMode.ONLINE &&
            onlineState.role == BtRole.CLIENT &&
            onlineState.connectionState == OnlineConnectionState.CONNECTED
        val isRemoteClient = isBtClient || isOnlineClient
        val showSecondPicker = gameMode == GameMode.TWO_PLAYER ||
            (gameMode == GameMode.BLUETOOTH && !isBtClient) ||
            (gameMode == GameMode.ONLINE && !isOnlineClient)

        if (isRemoteClient) {
            // Client: host picks creatures
            Text(
                "The host being\nselects creatures.",
                color = Color.White.copy(alpha = 0.5f),
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
            )
        } else {
            Text(
                if (showSecondPicker) "Your being:" else "Select your being:",
                color = Color.White.copy(alpha = 0.5f),
                fontSize = 12.sp,
            )
            Spacer(Modifier.height(6.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CreaturePickerItem(
                    drawableRes = R.drawable.sp_pong_player,
                    selected = selectedCreature == R.drawable.sp_pong_player,
                    onClick = { onSelectCreature(R.drawable.sp_pong_player) },
                )
                CreaturePickerItem(
                    drawableRes = R.drawable.sp_pong_cat,
                    selected = selectedCreature == R.drawable.sp_pong_cat,
                    onClick = { onSelectCreature(R.drawable.sp_pong_cat) },
                )
                CreaturePickerItem(
                    drawableRes = R.drawable.sp_pong_dog,
                    selected = selectedCreature == R.drawable.sp_pong_dog,
                    onClick = { onSelectCreature(R.drawable.sp_pong_dog) },
                )
            }

            // Second picker: 2-player or BT host
            if (showSecondPicker) {
                Spacer(Modifier.height(8.dp))
                Text(
                    if (gameMode == GameMode.TWO_PLAYER) "Upper being:" else "Distant being:",
                    color = Color.White.copy(alpha = 0.5f),
                    fontSize = 12.sp,
                )
                Spacer(Modifier.height(6.dp))

                val allCreatures = listOf(
                    R.drawable.sp_pong_player,
                    R.drawable.sp_pong_cat,
                    R.drawable.sp_pong_dog,
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    allCreatures.forEach { res ->
                        CreaturePickerItem(
                            drawableRes = res,
                            selected = selectedPlayer2Creature == res,
                            enabled = res != selectedCreature,
                            onClick = { onSelectPlayer2Creature(res) },
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(10.dp))

        // Configure button
        Text(
            text = "Configure Parameters",
            color = Color.White.copy(alpha = if (isRemoteClient) 0.2f else 0.6f),
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .background(
                    Color.White.copy(alpha = if (isRemoteClient) 0.04f else 0.1f),
                    RoundedCornerShape(10.dp),
                )
                .then(if (!isRemoteClient) Modifier.clickable { onConfigure() } else Modifier)
                .padding(horizontal = 16.dp, vertical = 8.dp),
        )
        if (isRemoteClient) {
            Spacer(Modifier.height(4.dp))
            Text(
                "Parameters governed\nby host being.",
                color = Color.White.copy(alpha = 0.3f),
                fontSize = 11.sp,
                textAlign = TextAlign.Center,
            )
        }

        Spacer(Modifier.height(14.dp))

        when (gameMode) {
            GameMode.BLUETOOTH -> {
                BluetoothLobby(
                    btState = btState,
                    onHost = onBtHost,
                    onScan = onBtScan,
                    onStopScan = onBtStopScan,
                    onConnect = onBtConnect,
                    onDisconnect = onBtDisconnect,
                    onRequestPermissions = onRequestBtPermissions,
                )
            }
            GameMode.ONLINE -> {
                OnlineLobby(
                    onlineState = onlineState,
                    onCreate = onOnlineCreate,
                    onJoin = onOnlineJoin,
                    onDisconnect = onOnlineDisconnect,
                )
            }
            else -> {
                Text(
                    "Tap to commence",
                    color = Color.White.copy(alpha = 0.6f),
                    fontSize = 15.sp,
                )
            }
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
                    // Quick reconnect to last known opponent
                    btState.lastConnectedDevice?.let { last ->
                        Text(
                            "Previously linked being:",
                            color = Color.White.copy(alpha = 0.4f),
                            fontSize = 11.sp,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = last.name,
                            color = AlienPink,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier
                                .clickable { onConnect(last.address) }
                                .background(
                                    AlienPink.copy(alpha = 0.15f),
                                    RoundedCornerShape(8.dp),
                                )
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                                .fillMaxWidth(),
                            textAlign = TextAlign.Center,
                        )
                        Spacer(Modifier.height(12.dp))
                    }

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

// ─── Online Lobby ───────────────────────────────────────────────────────────

@Composable
private fun OnlineLobby(
    onlineState: OnlineLobbyState,
    onCreate: () -> Unit,
    onJoin: (String) -> Unit,
    onDisconnect: () -> Unit,
) {
    var joinCode by remember { mutableStateOf("") }
    var showJoinInput by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        when (onlineState.connectionState) {
            OnlineConnectionState.IDLE -> {
                Text(
                    "Connect across\nthe entire planet.",
                    color = Color.White.copy(alpha = 0.6f),
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(10.dp))

                if (!showJoinInput) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        LobbyButton("Create\nRoom") { onCreate() }
                        LobbyButton("Join\nRoom") { showJoinInput = true }
                    }
                } else {
                    Text(
                        "Enter room code:",
                        color = Color.White.copy(alpha = 0.5f),
                        fontSize = 12.sp,
                    )
                    Spacer(Modifier.height(6.dp))

                    // Simple 4-character code input
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        repeat(CODE_LENGTH) { idx ->
                            val char = joinCode.getOrNull(idx)?.toString() ?: ""
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .background(
                                        Color.White.copy(alpha = 0.1f),
                                        RoundedCornerShape(8.dp),
                                    ),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = char,
                                    color = AlienPink,
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))

                    // Letter buttons (keyboard)
                    val letters = "ABCDEFGHJKLMNPQRSTUVWXYZ"
                    letters.chunked(8).forEach { row ->
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(3.dp),
                            modifier = Modifier.padding(vertical = 2.dp),
                        ) {
                            row.forEach { letter ->
                                Text(
                                    text = letter.toString(),
                                    color = Color.White,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier
                                        .size(28.dp)
                                        .background(
                                            Color.White.copy(alpha = 0.12f),
                                            RoundedCornerShape(4.dp),
                                        )
                                        .clickable {
                                            if (joinCode.length < CODE_LENGTH) {
                                                joinCode += letter
                                            }
                                        }
                                        .padding(top = 4.dp),
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "Clear",
                            color = Color.White.copy(alpha = 0.5f),
                            fontSize = 12.sp,
                            modifier = Modifier
                                .background(
                                    Color.White.copy(alpha = 0.08f),
                                    RoundedCornerShape(8.dp),
                                )
                                .clickable { joinCode = "" }
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                        )
                        if (joinCode.length == CODE_LENGTH) {
                            Text(
                                text = "Connect",
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier
                                    .background(
                                        SoftPink.copy(alpha = 0.7f),
                                        RoundedCornerShape(8.dp),
                                    )
                                    .clickable { onJoin(joinCode) }
                                    .padding(horizontal = 14.dp, vertical = 6.dp),
                            )
                        }
                    }

                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "Back",
                        color = Color.White.copy(alpha = 0.4f),
                        fontSize = 11.sp,
                        modifier = Modifier
                            .clickable { showJoinInput = false; joinCode = "" }
                            .padding(4.dp),
                    )
                }
            }

            OnlineConnectionState.CREATING -> {
                Text(
                    "Creating frequency room...",
                    color = Color.White.copy(alpha = 0.6f),
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                )
            }

            OnlineConnectionState.WAITING_FOR_PLAYER -> {
                Text(
                    "Room established!",
                    color = SoftPink,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "Transmit this code\nto the distant being:",
                    color = Color.White.copy(alpha = 0.5f),
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(8.dp))

                // Big room code display
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    (onlineState.roomCode ?: "").forEach { ch ->
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .background(
                                    AlienPink.copy(alpha = 0.2f),
                                    RoundedCornerShape(10.dp),
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = ch.toString(),
                                color = AlienPink,
                                fontSize = 26.sp,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))
                Text(
                    "Awaiting distant being...",
                    color = Color.White.copy(alpha = 0.4f),
                    fontSize = 12.sp,
                )
                Spacer(Modifier.height(8.dp))
                LobbyButton("Cancel") { onDisconnect() }
            }

            OnlineConnectionState.JOINING -> {
                Text(
                    "Joining frequency room...",
                    color = Color.White.copy(alpha = 0.6f),
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                )
            }

            OnlineConnectionState.CONNECTED -> {
                Text(
                    "Planetary link established!",
                    color = SoftPink,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center,
                )
                onlineState.connectedPlayerName?.let { name ->
                    Text(
                        "Connected to: $name",
                        color = Color.White.copy(alpha = 0.5f),
                        fontSize = 12.sp,
                    )
                }
                onlineState.roomCode?.let { code ->
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Room: $code",
                        color = Color.White.copy(alpha = 0.3f),
                        fontSize = 11.sp,
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

private const val CODE_LENGTH = 4

// ─── Mode & Lobby Buttons ────────────────────────────────────────────────────

@Composable
private fun ModeButton(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val bgColor = if (selected) SoftPink else Color.White.copy(alpha = 0.15f)
    val textColor = if (selected) Color.White else Color.White.copy(alpha = 0.6f)

    Box(
        modifier = modifier
            .background(bgColor, RoundedCornerShape(10.dp))
            .clickable { onClick() }
            .padding(horizontal = 10.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = textColor,
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun CreaturePickerItem(
    drawableRes: Int,
    selected: Boolean,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val borderColor = when {
        !enabled -> Color.White.copy(alpha = 0.05f)
        selected -> AlienPink
        else -> Color.White.copy(alpha = 0.15f)
    }
    Box(
        modifier = Modifier
            .size(60.dp)
            .background(borderColor.copy(alpha = 0.3f), CircleShape)
            .then(if (enabled) Modifier.clickable { onClick() } else Modifier)
            .padding(4.dp),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(id = drawableRes),
            contentDescription = "Select being",
            modifier = Modifier
                .size(48.dp)
                .graphicsLayer { alpha = if (enabled) 1f else 0.25f },
        )
    }
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

// ─── Settings Modal ─────────────────────────────────────────────────────────

@Composable
private fun SettingsModal(
    settings: PongSettings,
    onSoundToggle: (Boolean) -> Unit,
    onSayingsToggle: (Boolean) -> Unit,
    onDifficulty: (DifficultyLevel) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Scrim
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.5f))
            .clickable { onDismiss() },
    )

    Column(
        modifier = modifier
            .widthIn(max = 280.dp)
            .background(DeepNavy.copy(alpha = 0.95f), RoundedCornerShape(20.dp))
            .padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            "Adjustment\nParameters",
            color = AlienPink,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.height(20.dp))

        // Sound toggle
        SettingsToggleRow(
            label = "Acoustic Feedback",
            checked = settings.soundEnabled,
            onCheckedChange = onSoundToggle,
        )

        Spacer(Modifier.height(12.dp))

        // Sayings toggle
        SettingsToggleRow(
            label = "Verbal Commentary",
            checked = settings.showSayings,
            onCheckedChange = onSayingsToggle,
        )

        Spacer(Modifier.height(20.dp))

        // Difficulty presets
        Text(
            "Sphere Intensity:",
            color = Color.White.copy(alpha = 0.5f),
            fontSize = 13.sp,
        )
        Spacer(Modifier.height(8.dp))

        Column(
            verticalArrangement = Arrangement.spacedBy(6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            DifficultyLevel.entries.forEach { level ->
                val selected = settings.difficulty == level
                Text(
                    text = level.label,
                    color = if (selected) Color.White else Color.White.copy(alpha = 0.5f),
                    fontSize = 14.sp,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            if (selected) SoftPink else Color.White.copy(alpha = 0.08f),
                            RoundedCornerShape(10.dp),
                        )
                        .clickable { onDifficulty(level) }
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                )
            }
        }

        Spacer(Modifier.height(24.dp))

        // Dismiss button
        Text(
            text = "Configuration Satisfactory",
            color = Color.White,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .background(AlienPink.copy(alpha = 0.7f), RoundedCornerShape(12.dp))
                .clickable { onDismiss() }
                .padding(horizontal = 20.dp, vertical = 12.dp),
        )
    }
}

@Composable
private fun SettingsToggleRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            color = Color.White.copy(alpha = 0.8f),
            fontSize = 14.sp,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(12.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = AlienPink,
                checkedTrackColor = SoftPink.copy(alpha = 0.4f),
                uncheckedThumbColor = Color.White.copy(alpha = 0.4f),
                uncheckedTrackColor = Color.White.copy(alpha = 0.1f),
            ),
        )
    }
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

                GameMode.BLUETOOTH, GameMode.ONLINE -> {
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
