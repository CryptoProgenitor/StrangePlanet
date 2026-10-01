package com.quokkalabs.strangeplanet.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.quokkalabs.strangeplanet.R
import kotlin.math.PI
import kotlin.math.sin

private val starFractions = listOf(
    Offset(0.08f, 0.12f),
    Offset(0.88f, 0.08f),
    Offset(0.45f, 0.18f),
    Offset(0.58f, 0.32f),
    Offset(0.05f, 0.25f),
    Offset(0.78f, 0.15f),
    Offset(0.30f, 0.05f),
    Offset(0.92f, 0.40f),
    Offset(0.15f, 0.55f),
    Offset(0.70f, 0.48f),
    // Bottom-band stars (visible below the maze)
    Offset(0.12f, 0.78f),
    Offset(0.55f, 0.83f),
    Offset(0.82f, 0.75f),
    Offset(0.35f, 0.90f),
    Offset(0.68f, 0.93f),
)

private val auraColors = listOf(
    Color.White.copy(alpha = 0.9f),
    Color.White.copy(alpha = 0.4f),
    Color.Transparent,
)

@Composable
fun StarField() {
    val infiniteTransition = rememberInfiniteTransition(label = "stars")
    val starPainter = painterResource(id = R.drawable.sp_star)

    // Read as State and only inside the draw lambda, so twinkling just redraws
    // the canvas instead of recomposing and re-laying-out every star each frame.
    val twinkle = infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            tween(10000, easing = LinearEasing),
            RepeatMode.Restart,
        ),
        label = "twinkle",
    )

    Canvas(modifier = Modifier.fillMaxSize()) {
        val t = twinkle.value
        val inset = 30.dp.toPx()
        val auraBase = 60.dp.toPx()
        val starBase = 48.dp.toPx()

        starFractions.forEachIndexed { index, fraction ->
            val ox = fraction.x * size.width
            val oy = fraction.y * size.height

            val phase = (t + index * 0.1f) % 1f
            val pulse = (sin(phase * 2f * PI.toFloat()) + 1f) / 2f * 0.7f + 0.3f
            val starScale = 0.8f + pulse * 0.4f
            val auraScale = 1.2f + pulse * 0.6f
            val auraAlpha = 0.5f + pulse * 0.4f

            // Aura: top-left anchored at (offset - 30dp), growing with auraScale
            val auraRadius = auraBase * auraScale / 2f
            val auraCenter = Offset(ox - inset + auraRadius, oy - inset + auraRadius)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = auraColors,
                    center = auraCenter,
                    radius = auraRadius,
                ),
                radius = auraRadius,
                center = auraCenter,
                alpha = auraAlpha,
            )

            // Star sprite: top-left anchored at offset
            val starSize = starBase * starScale
            translate(left = ox, top = oy) {
                with(starPainter) {
                    draw(size = Size(starSize, starSize), alpha = 0.95f)
                }
            }
        }
    }
}
