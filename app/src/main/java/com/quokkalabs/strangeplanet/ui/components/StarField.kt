package com.quokkalabs.strangeplanet.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.quokkalabs.strangeplanet.R
import kotlin.math.PI
import kotlin.math.sin

@Composable
fun StarField() {
    val infiniteTransition = rememberInfiniteTransition(label = "stars")
    val density = LocalDensity.current

    val twinkle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            tween(10000, easing = LinearEasing),
            RepeatMode.Restart,
        ),
        label = "twinkle",
    )

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val w = constraints.maxWidth.toFloat()
        val h = constraints.maxHeight.toFloat()

        val positions = remember(w, h) {
            listOf(
                Offset(w * 0.08f, h * 0.12f),
                Offset(w * 0.88f, h * 0.08f),
                Offset(w * 0.45f, h * 0.18f),
                Offset(w * 0.58f, h * 0.32f),
                Offset(w * 0.05f, h * 0.25f),
                Offset(w * 0.78f, h * 0.15f),
                Offset(w * 0.30f, h * 0.05f),
                Offset(w * 0.92f, h * 0.40f),
                Offset(w * 0.15f, h * 0.55f),
                Offset(w * 0.70f, h * 0.48f),
                // Bottom-band stars (visible below the maze)
                Offset(w * 0.12f, h * 0.78f),
                Offset(w * 0.55f, h * 0.83f),
                Offset(w * 0.82f, h * 0.75f),
                Offset(w * 0.35f, h * 0.90f),
                Offset(w * 0.68f, h * 0.93f),
            )
        }

        positions.forEachIndexed { index, offset ->
            val phase = (twinkle + index * 0.1f) % 1f
            val pulse = (sin(phase * 2f * PI.toFloat()) + 1f) / 2f * 0.7f + 0.3f
            val starScale = 0.8f + pulse * 0.4f
            val auraScale = 1.2f + pulse * 0.6f
            val auraAlpha = 0.5f + pulse * 0.4f

            Box(
                modifier = Modifier
                    .offset(
                        x = with(density) { (offset.x - 30.dp.toPx()).toDp() },
                        y = with(density) { (offset.y - 30.dp.toPx()).toDp() },
                    )
                    .size((60 * auraScale).dp)
                    .alpha(auraAlpha)
                    .background(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                Color.White.copy(alpha = 0.9f),
                                Color.White.copy(alpha = 0.4f),
                                Color.Transparent,
                            ),
                        ),
                        shape = CircleShape,
                    ),
            )

            Image(
                painter = painterResource(id = R.drawable.sp_star),
                contentDescription = null,
                modifier = Modifier
                    .offset(
                        x = with(density) { offset.x.toDp() },
                        y = with(density) { offset.y.toDp() },
                    )
                    .size((48 * starScale).dp)
                    .alpha(0.95f),
            )
        }
    }
}
