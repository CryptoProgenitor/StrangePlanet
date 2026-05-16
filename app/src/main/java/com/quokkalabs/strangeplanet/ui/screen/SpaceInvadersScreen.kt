package com.quokkalabs.strangeplanet.ui.screen

import android.graphics.BitmapFactory
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.quokkalabs.strangeplanet.R
import com.quokkalabs.strangeplanet.data.model.DifficultyLevel
import com.quokkalabs.strangeplanet.data.model.InvaderType
import com.quokkalabs.strangeplanet.data.model.SIPhase
import com.quokkalabs.strangeplanet.ui.components.CosmicBackground
import com.quokkalabs.strangeplanet.ui.components.ExitChoiceDialog
import com.quokkalabs.strangeplanet.ui.components.PauseOnBackground
import com.quokkalabs.strangeplanet.ui.components.ResumePrompt
import com.quokkalabs.strangeplanet.ui.theme.AlienPink
import com.quokkalabs.strangeplanet.ui.theme.DeepNavy
import com.quokkalabs.strangeplanet.ui.theme.SoftPink
import com.quokkalabs.strangeplanet.ui.viewmodel.SpaceInvadersViewModel

@Composable
fun SpaceInvadersScreen(
    viewModel: SpaceInvadersViewModel,
    onBack: () -> Unit,
) {
    val state by viewModel.state.collectAsState()
    val siSettings by viewModel.siSettings.collectAsState()
    var showSettings by remember { mutableStateOf(false) }
    val density = LocalDensity.current
    val view = LocalView.current
    val context = LocalContext.current

    var showExit by remember { mutableStateOf(false) }
    var showResume by remember { mutableStateOf(viewModel.hasSavedSession()) }

    fun attemptBack() {
        if (state.phase == SIPhase.PLAYING || state.phase == SIPhase.PAUSED) {
            showExit = true
        } else {
            viewModel.resetGame()
            onBack()
        }
    }

    BackHandler { attemptBack() }

    // Screen off / app backgrounded → suspend play.
    PauseOnBackground { viewModel.pauseGame() }

    // Immersive mode
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

    // Pre-load sprites as ImageBitmap for Canvas drawing
    val catBitmap = remember {
        BitmapFactory.decodeResource(context.resources, R.drawable.sp_pong_cat).asImageBitmap()
    }
    val dogBitmap = remember {
        BitmapFactory.decodeResource(context.resources, R.drawable.sp_pong_dog).asImageBitmap()
    }
    val playerBitmap = remember {
        BitmapFactory.decodeResource(context.resources, R.drawable.sp_invaders_alien_dad).asImageBitmap()
    }
    val sockBitmap = remember {
        BitmapFactory.decodeResource(context.resources, R.drawable.sp_socks).asImageBitmap()
    }

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
                                val anyPressed = event.changes.any { it.pressed }
                                if (anyPressed) {
                                    event.changes.forEach { change ->
                                        if (change.pressed) {
                                            viewModel.onTouch(change.position.x)
                                            if (state.phase == SIPhase.READY ||
                                                state.phase == SIPhase.GAME_OVER
                                            ) {
                                                viewModel.onTapToStart()
                                            }
                                        }
                                    }
                                } else {
                                    viewModel.onTouchUp()
                                }
                                event.changes.forEach { it.consume() }
                            }
                        }
                    },
            )

            if (state.screenWidth > 0f) {
                val invaderSizePx = state.screenWidth * 0.07f

                // ── Game Canvas ─────────────────────────────────────────────
                Canvas(modifier = Modifier.fillMaxSize()) {
                    // Invaders
                    state.invaders.filter { it.alive }.forEach { inv ->
                        val bitmap = when (inv.type) {
                            InvaderType.CAT -> catBitmap
                            InvaderType.DOG -> dogBitmap
                            InvaderType.FOOT_FABRIC_TUBE -> sockBitmap
                        }
                        val sz = invaderSizePx.toInt()
                        drawImage(
                            image = bitmap,
                            dstOffset = IntOffset(
                                (inv.x - sz / 2).toInt(),
                                (inv.y - sz / 2).toInt(),
                            ),
                            dstSize = IntSize(sz, sz),
                        )
                    }

                    // Atmospheric barriers (shields)
                    val shieldColor = AlienPink.copy(alpha = 0.45f)
                    val shieldBlockSz = state.screenWidth * 0.022f
                    val shieldHalf = shieldBlockSz / 2f
                    state.shields.filter { it.alive }.forEach { block ->
                        drawRect(
                            color = shieldColor,
                            topLeft = Offset(block.x - shieldHalf, block.y - shieldHalf),
                            size = Size(shieldBlockSz, shieldBlockSz),
                        )
                    }

                    // Player sprite (170% size)
                    val pSize = (state.playerWidth * 2.04f).toInt()
                    drawImage(
                        image = playerBitmap,
                        dstOffset = IntOffset(
                            (state.playerX - pSize / 2).toInt(),
                            (state.playerY - pSize / 2).toInt(),
                        ),
                        dstSize = IntSize(pSize, pSize),
                    )

                    // Player projectiles (pink glow rounds — fattened)
                    state.playerProjectiles.forEach { proj ->
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    SoftPink.copy(alpha = 0.6f),
                                    SoftPink.copy(alpha = 0.1f),
                                    Color.Transparent,
                                ),
                                center = Offset(proj.x, proj.y),
                                radius = state.screenWidth * 0.035f,
                            ),
                            radius = state.screenWidth * 0.035f,
                            center = Offset(proj.x, proj.y),
                        )
                        drawRoundRect(
                            color = SoftPink,
                            topLeft = Offset(
                                proj.x - state.screenWidth * 0.008f,
                                proj.y - state.screenHeight * 0.014f,
                            ),
                            size = Size(
                                state.screenWidth * 0.016f,
                                state.screenHeight * 0.028f,
                            ),
                            cornerRadius = CornerRadius(6f),
                        )
                    }

                    // Enemy projectiles (green — fattened)
                    val enemyGreen = Color(0xFF4ADE80)
                    state.enemyProjectiles.forEach { proj ->
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    enemyGreen.copy(alpha = 0.5f),
                                    enemyGreen.copy(alpha = 0.1f),
                                    Color.Transparent,
                                ),
                                center = Offset(proj.x, proj.y),
                                radius = state.screenWidth * 0.025f,
                            ),
                            radius = state.screenWidth * 0.025f,
                            center = Offset(proj.x, proj.y),
                        )
                        drawRoundRect(
                            color = enemyGreen,
                            topLeft = Offset(
                                proj.x - state.screenWidth * 0.007f,
                                proj.y - state.screenHeight * 0.012f,
                            ),
                            size = Size(
                                state.screenWidth * 0.014f,
                                state.screenHeight * 0.024f,
                            ),
                            cornerRadius = CornerRadius(5f),
                        )
                    }

                    // Disintegration particles
                    val particleRadius = state.screenWidth * 0.006f
                    state.particles.forEach { p ->
                        drawCircle(
                            color = Color(p.color).copy(alpha = p.alpha.coerceIn(0f, 1f)),
                            radius = particleRadius * p.alpha.coerceIn(0.3f, 1f),
                            center = Offset(p.x, p.y),
                        )
                    }

                    // Subtle paddle bar under player
                    drawRoundRect(
                        color = AlienPink.copy(alpha = 0.35f),
                        topLeft = Offset(
                            state.playerX - state.playerWidth / 2f,
                            state.playerY + pSize / 2f,
                        ),
                        size = Size(state.playerWidth, state.screenHeight * 0.005f),
                        cornerRadius = CornerRadius(4f),
                    )
                }

                // ── HUD ────────────────────────────────────────────────────

                // Lives pill — top center
                Row(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 28.dp)
                        .background(DeepNavy.copy(alpha = 0.65f), RoundedCornerShape(20.dp))
                        .padding(horizontal = 14.dp, vertical = 7.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    repeat(state.lives.coerceAtLeast(0)) { i ->
                        Image(
                            painter = painterResource(R.drawable.sp_invaders_alien_dad),
                            contentDescription = "Life ${i + 1}",
                            modifier = Modifier.size(22.dp),
                        )
                    }
                    if (state.lives <= 0) {
                        Text("—", color = Color.White.copy(alpha = 0.4f), fontSize = 14.sp)
                    }
                }

                // Score + wave pill — top center, below lives
                Row(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 70.dp)
                        .background(DeepNavy.copy(alpha = 0.55f), RoundedCornerShape(14.dp))
                        .padding(horizontal = 16.dp, vertical = 5.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "${state.score}",
                        color = AlienPink,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Text("·", color = Color.White.copy(alpha = 0.3f), fontSize = 14.sp)
                    Text(
                        text = "Wave ${state.wave}",
                        color = Color.White.copy(alpha = 0.65f),
                        fontSize = 13.sp,
                    )
                }

                // ── Saying overlay (top of screen) ─────────────────────────
                if (siSettings.showSayings && state.phase == SIPhase.PLAYING) {
                    state.activeSaying?.let { text ->
                        Text(
                            text = text,
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .padding(top = 60.dp)
                                .background(
                                    DeepNavy.copy(alpha = 0.75f),
                                    RoundedCornerShape(12.dp),
                                )
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                        )
                    }
                }

                // ── Ready overlay ───────────────────────────────────────────
                if (state.phase == SIPhase.READY) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.3f)),
                    )
                    Column(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .widthIn(max = 340.dp)
                            .fillMaxWidth(0.9f)
                            .background(
                                DeepNavy.copy(alpha = 0.85f),
                                RoundedCornerShape(20.dp),
                            )
                            .verticalScroll(rememberScrollState())
                            .padding(28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            "Descending\nEntity Defence",
                            color = AlienPink,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                        )
                        Spacer(Modifier.height(12.dp))
                        Text(
                            "Hostile creatures descend\nfrom the upper atmosphere.\n\nNeutralise them with your\nprojection device.",
                            color = Color.White.copy(alpha = 0.7f),
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center,
                        )
                        Spacer(Modifier.height(16.dp))
                        Text(
                            "Slide to aim · auto-fire active",
                            color = Color.White.copy(alpha = 0.7f),
                            fontSize = 13.sp,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Tap anywhere to commence",
                            color = AlienPink.copy(alpha = 0.85f),
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Spacer(Modifier.height(14.dp))
                        Text(
                            "Configure Parameters",
                            color = AlienPink.copy(alpha = 0.85f),
                            fontSize = 13.sp,
                            modifier = Modifier
                                .clickable { showSettings = true }
                                .background(
                                    Color.White.copy(alpha = 0.08f),
                                    RoundedCornerShape(10.dp),
                                )
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                        )
                        Spacer(Modifier.height(14.dp))
                        Text(
                            "Abandon Endeavour",
                            color = Color.White.copy(alpha = 0.7f),
                            fontSize = 13.sp,
                            modifier = Modifier
                                .clickable { viewModel.resetGame(); onBack() }
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                        )
                    }
                }

                // ── Game over overlay ───────────────────────────────────────
                if (state.phase == SIPhase.GAME_OVER) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.4f)),
                    )
                    Column(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .widthIn(max = 340.dp)
                            .fillMaxWidth(0.9f)
                            .background(
                                DeepNavy.copy(alpha = 0.85f),
                                RoundedCornerShape(20.dp),
                            )
                            .verticalScroll(rememberScrollState())
                            .padding(28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            "Defence Concluded",
                            color = AlienPink,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            state.activeSaying ?: "The descending entities\nhave prevailed.",
                            color = Color.White.copy(alpha = 0.6f),
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center,
                        )
                        Spacer(Modifier.height(12.dp))
                        Text(
                            "${state.score}",
                            color = SoftPink,
                            fontSize = 36.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            "points accumulated",
                            color = Color.White.copy(alpha = 0.4f),
                            fontSize = 12.sp,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Wave ${state.wave} reached",
                            color = Color.White.copy(alpha = 0.3f),
                            fontSize = 11.sp,
                        )
                        Spacer(Modifier.height(16.dp))
                        Text(
                            "Tap anywhere to attempt again",
                            color = AlienPink.copy(alpha = 0.85f),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }

                // ── Wave clear flash ────────────────────────────────────────
                if (state.phase == SIPhase.WAVE_CLEAR) {
                    Column(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .background(
                                DeepNavy.copy(alpha = 0.75f),
                                RoundedCornerShape(16.dp),
                            )
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            "Wave ${state.wave} Complete!",
                            color = AlienPink,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        state.activeSaying?.let { text ->
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text,
                                color = Color.White.copy(alpha = 0.6f),
                                fontSize = 12.sp,
                            )
                        }
                    }
                }

                // ── Paused overlay ──────────────────────────────────────────
                if (state.phase == SIPhase.PAUSED) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.4f)),
                    )
                    Column(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .widthIn(max = 340.dp)
                            .fillMaxWidth(0.9f)
                            .background(
                                DeepNavy.copy(alpha = 0.85f),
                                RoundedCornerShape(20.dp),
                            )
                            .verticalScroll(rememberScrollState())
                            .padding(28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            "Activity Suspended",
                            color = AlienPink,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                        )
                        Spacer(Modifier.height(12.dp))
                        Text(
                            "The entities await\nyour return.",
                            color = Color.White.copy(alpha = 0.6f),
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center,
                        )
                        Spacer(Modifier.height(16.dp))
                        Text(
                            "Resume Activity",
                            color = AlienPink,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier
                                .clickable { viewModel.resumeGame() }
                                .background(
                                    Color.White.copy(alpha = 0.08f),
                                    RoundedCornerShape(12.dp),
                                )
                                .padding(horizontal = 20.dp, vertical = 10.dp),
                        )
                    }
                }

                // ── Back button ─────────────────────────────────────────────
                FloatingActionButton(
                    onClick = { attemptBack() },
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(top = 24.dp, start = 12.dp)
                        .size(48.dp),
                    shape = CircleShape,
                    containerColor = AlienPink.copy(alpha = 0.5f),
                    contentColor = DeepNavy,
                ) {
                    Text("←", fontSize = 18.sp)
                }

                // ── Pause button (during gameplay) ─────────────────────────
                if (state.phase == SIPhase.PLAYING) {
                    FloatingActionButton(
                        onClick = { viewModel.pauseGame() },
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(top = 24.dp, start = 68.dp)
                            .size(48.dp),
                        shape = CircleShape,
                        containerColor = AlienPink.copy(alpha = 0.5f),
                        contentColor = DeepNavy,
                    ) {
                        Text("⏸", fontSize = 18.sp)
                    }
                }

                // ── Settings gear (top-end) ────────────────────────────────
                if (state.phase == SIPhase.READY || state.phase == SIPhase.GAME_OVER || state.phase == SIPhase.PAUSED) {
                    FloatingActionButton(
                        onClick = { showSettings = true },
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(top = 24.dp, end = 12.dp)
                            .size(48.dp),
                        shape = CircleShape,
                        containerColor = AlienPink.copy(alpha = 0.5f),
                        contentColor = DeepNavy,
                    ) {
                        Text("⚙", fontSize = 20.sp)
                    }
                }

                // ── Settings modal ─────────────────────────────────────────
                AnimatedVisibility(
                    visible = showSettings,
                    enter = fadeIn(tween(150)),
                    exit = fadeOut(tween(150)),
                ) {
                    SISettingsModal(
                        settings = siSettings,
                        onSoundToggle = { viewModel.setSoundEnabled(it) },
                        onSayingsToggle = { viewModel.setShowSayings(it) },
                        onDifficultyChange = { viewModel.setDifficulty(it) },
                        onDismiss = { showSettings = false },
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

                if (showResume && state.phase == SIPhase.READY) {
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

@Composable
private fun SISettingsModal(
    settings: com.quokkalabs.strangeplanet.data.model.SISettings,
    onSoundToggle: (Boolean) -> Unit,
    onSayingsToggle: (Boolean) -> Unit,
    onDifficultyChange: (DifficultyLevel) -> Unit,
    onDismiss: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.6f))
            .clickable { onDismiss() },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 340.dp)
                .fillMaxWidth(0.9f)
                .background(DeepNavy.copy(alpha = 0.95f), RoundedCornerShape(20.dp))
                .clickable { /* consume */ }
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                "Defence Parameters",
                color = AlienPink,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(20.dp))

            // Sound toggle
            SettingsRow("Auditory Feedback", settings.soundEnabled, onSoundToggle)
            Spacer(Modifier.height(10.dp))

            // Sayings toggle
            SettingsRow("Creature Sayings", settings.showSayings, onSayingsToggle)
            Spacer(Modifier.height(16.dp))

            // Difficulty
            Text(
                "Descent Intensity",
                color = Color.White.copy(alpha = 0.6f),
                fontSize = 13.sp,
            )
            Spacer(Modifier.height(8.dp))

            val labels = mapOf(
                DifficultyLevel.GENTLE to "Leisurely Descent",
                DifficultyLevel.STANDARD to "Standard",
                DifficultyLevel.AGGRESSIVE to "Aggressive Descent",
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                DifficultyLevel.entries.forEach { level ->
                    val selected = settings.difficulty == level
                    Text(
                        text = labels[level] ?: level.label,
                        color = if (selected) DeepNavy else AlienPink.copy(alpha = 0.7f),
                        fontSize = 11.sp,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .weight(1f)
                            .background(
                                if (selected) AlienPink else Color.White.copy(alpha = 0.08f),
                                RoundedCornerShape(10.dp),
                            )
                            .clickable { onDifficultyChange(level) }
                            .padding(vertical = 10.dp, horizontal = 4.dp),
                    )
                }
            }

            Spacer(Modifier.height(20.dp))
            Text(
                "Dismiss",
                color = Color.White.copy(alpha = 0.4f),
                fontSize = 13.sp,
                modifier = Modifier
                    .clickable { onDismiss() }
                    .padding(8.dp),
            )
        }
    }
}

@Composable
private fun SettingsRow(
    label: String,
    checked: Boolean,
    onToggle: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            color = Color.White.copy(alpha = 0.7f),
            fontSize = 14.sp,
        )
        Switch(
            checked = checked,
            onCheckedChange = onToggle,
            colors = SwitchDefaults.colors(
                checkedThumbColor = AlienPink,
                checkedTrackColor = AlienPink.copy(alpha = 0.3f),
            ),
        )
    }
}
