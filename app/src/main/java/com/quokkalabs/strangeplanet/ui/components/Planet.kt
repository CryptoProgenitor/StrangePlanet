package com.quokkalabs.strangeplanet.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun Planet(
    modifier: Modifier = Modifier,
    size: Dp = 160.dp,
    glowBoost: Float = 0f,
    onTap: () -> Unit = {},
) {
    val planetColor = Color(0xFFE8B4C8)
    val ringColor = Color(0xFF9B7FB8)
    val glowColor = Color(0xFFC77DA3)
    val craterColor = Color(0xFFD4A5B9)

    val transition = rememberInfiniteTransition(label = "planet")

    val ringRotation by transition.animateFloat(
        initialValue = -15f,
        targetValue = 15f,
        animationSpec = infiniteRepeatable(
            tween(3000, easing = FastOutSlowInEasing),
            RepeatMode.Reverse,
        ),
        label = "ringRotation",
    )

    val glowPulse by transition.animateFloat(
        initialValue = 0.15f,
        targetValue = 0.3f,
        animationSpec = infiniteRepeatable(
            tween(2000, easing = FastOutSlowInEasing),
            RepeatMode.Reverse,
        ),
        label = "glow",
    )

    Canvas(
        modifier = modifier
            .size(size)
            .pointerInput(Unit) { detectTapGestures { onTap() } },
    ) {
        val effectiveGlow = glowPulse + glowBoost
        val cx = this.size.width / 2
        val cy = this.size.height / 2
        val planetRadius = this.size.minDimension * 0.25f

        // Ring dimensions
        val outerW = planetRadius * 2.3f
        val outerH = planetRadius * 0.5f
        val midW = planetRadius * 1.9f
        val midH = planetRadius * 0.4f
        val innerW = planetRadius * 1.5f
        val innerH = planetRadius * 0.32f

        // Ambient glow
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    glowColor.copy(alpha = effectiveGlow.coerceIn(0f, 1f)),
                    glowColor.copy(alpha = (effectiveGlow * 0.4f).coerceIn(0f, 1f)),
                    Color.Transparent,
                ),
                radius = planetRadius * 2.5f,
            ),
            radius = planetRadius * 2.5f,
            center = Offset(cx, cy),
        )

        // Back ring arcs (behind planet)
        rotate(degrees = ringRotation, pivot = Offset(cx, cy)) {
            drawArc(ringColor, 180f, 180f, false,
                Offset(cx - outerW, cy - outerH), Size(outerW * 2, outerH * 2),
                style = Stroke(6.dp.toPx(), cap = StrokeCap.Round))
            drawArc(glowColor.copy(alpha = 0.8f), 180f, 180f, false,
                Offset(cx - midW, cy - midH), Size(midW * 2, midH * 2),
                style = Stroke(4.dp.toPx(), cap = StrokeCap.Round))
            drawArc(ringColor.copy(alpha = 0.5f), 180f, 180f, false,
                Offset(cx - innerW, cy - innerH), Size(innerW * 2, innerH * 2),
                style = Stroke(3.dp.toPx(), cap = StrokeCap.Round))
        }

        // Planet body
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(planetColor, planetColor.copy(alpha = 0.9f), craterColor),
                center = Offset(cx - planetRadius * 0.3f, cy - planetRadius * 0.3f),
                radius = planetRadius * 1.5f,
            ),
            radius = planetRadius,
            center = Offset(cx, cy),
        )

        // Front ring arcs (in front of planet)
        rotate(degrees = ringRotation, pivot = Offset(cx, cy)) {
            drawArc(ringColor, 0f, 180f, false,
                Offset(cx - outerW, cy - outerH), Size(outerW * 2, outerH * 2),
                style = Stroke(6.dp.toPx(), cap = StrokeCap.Round))
            drawArc(glowColor.copy(alpha = 0.8f), 0f, 180f, false,
                Offset(cx - midW, cy - midH), Size(midW * 2, midH * 2),
                style = Stroke(4.dp.toPx(), cap = StrokeCap.Round))
            drawArc(ringColor.copy(alpha = 0.5f), 0f, 180f, false,
                Offset(cx - innerW, cy - innerH), Size(innerW * 2, innerH * 2),
                style = Stroke(3.dp.toPx(), cap = StrokeCap.Round))
        }

        // Craters
        drawCircle(craterColor, planetRadius * 0.17f,
            Offset(cx + planetRadius * 0.35f, cy - planetRadius * 0.3f))
        drawCircle(craterColor.copy(alpha = 0.7f), planetRadius * 0.12f,
            Offset(cx + planetRadius * 0.45f, cy + planetRadius * 0.15f))
        drawCircle(craterColor.copy(alpha = 0.6f), planetRadius * 0.09f,
            Offset(cx - planetRadius * 0.25f, cy + planetRadius * 0.4f))
    }
}
