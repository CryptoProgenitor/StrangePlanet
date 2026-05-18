package com.quokkalabs.strangeplanet.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.rememberSplineBasedDecay
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.quokkalabs.strangeplanet.ui.theme.AlienPink
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * A 3D cylindrical carousel. Cards sit on the surface of a vertical drum
 * viewed slightly from above (a hint of the top ellipse). The drum has
 * polar-axial inertia: a fling spins it and it coasts to rest, then snaps to
 * the nearest card. Edge cards recede, desaturate, blur and fade; the front
 * card pops forward, sheds its blur and gains an AlienPink glow.
 *
 * The card content (a [GamePosters] poster) is supplied by the caller and
 * stays purely visual — all glassmorphism, depth and physics live here.
 */
@Composable
fun CylinderCarousel(
    count: Int,
    modifier: Modifier = Modifier,
    cardWidth: Dp = 188.dp,
    cardHeight: Dp = 286.dp,
    onFocusedChange: (Int) -> Unit = {},
    onLaunch: (Int) -> Unit = {},
    card: @Composable (index: Int) -> Unit,
) {
    if (count == 0) return
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()

    // Angular spacing so all cards fill the full 360° drum — a true closed
    // loop with no empty wedge at the back.
    val step = 360f / count
    // Cards beyond this angle from the front are fully invisible. Keeps the
    // current card plus two on each side on screen.
    val cutoff = (step * 2.5f).coerceAtMost(160f)
    // Pixels of horizontal drag that equal one degree of rotation.
    val dragPerDeg = with(density) { 3.6.dp.toPx() }
    val radiusPx = with(density) { (cardWidth * 1.3f).toPx() }
    // Must be large relative to the card or the perspective projection
    // degenerates and the card vanishes.
    val cameraPx = with(density) { cardHeight.toPx() * 2.5f }

    // Drum rotation, in degrees. The card nearest a multiple of `step` is
    // the focused one (its angle ≈ 0).
    val angle = remember { Animatable(0f) }
    val decay = rememberSplineBasedDecay<Float>()

    fun focusedOf(a: Float): Int {
        val raw = (a / step).roundToInt() % count
        return (raw + count) % count
    }

    val focused by remember { derivedStateOf { focusedOf(angle.value) } }
    LaunchedEffect(focused) { onFocusedChange(focused) }

    Box(
        modifier = modifier
            .pointerInput(count) {
                val tracker = VelocityTracker()
                detectHorizontalDragGestures(
                    onDragStart = {
                        tracker.resetTracking()
                        scope.launch { angle.stop() }
                    },
                    onDragEnd = {
                        val vx = tracker.calculateVelocity().x
                        val angularV = -vx / dragPerDeg
                        scope.launch {
                            angle.animateDecay(angularV, decay)
                            val snapped = (angle.value / step).roundToInt() * step
                            angle.animateTo(
                                snapped,
                                spring(stiffness = Spring.StiffnessLow),
                            )
                        }
                    },
                    onHorizontalDrag = { change, dragAmount ->
                        change.consume()
                        tracker.addPosition(change.uptimeMillis, change.position)
                        scope.launch { angle.snapTo(angle.value - dragAmount / dragPerDeg) }
                    },
                )
            }
            .pointerInput(count) {
                detectTapGestures { onLaunch(focusedOf(angle.value)) }
            },
        contentAlignment = Alignment.Center,
    ) {
        for (i in 0 until count) {
            // theta: this card's angle on the drum relative to the viewer.
            var theta = i * step - angle.value
            theta = ((theta + 180f) % 360f + 360f) % 360f - 180f
            val absT = abs(theta)
            if (absT >= cutoff) continue

            val rad = theta * (PI / 180f).toFloat()
            val proximity = (1f - absT / step).coerceIn(0f, 1f) // 1 at front
            val fade = (1f - absT / cutoff).coerceIn(0f, 1f)
            val blurDp = (absT / cutoff).coerceIn(0f, 1f) * 10f
            val scrimA = (absT / cutoff).coerceIn(0f, 1f) * 0.55f
            val pop = 1f + 0.07f * proximity

            Box(
                modifier = Modifier
                    .size(cardWidth, cardHeight)
                    .zIndex(cos(rad))
                    .graphicsLayer {
                        // Slight "viewed from above" tilt, applied per small
                        // card (safe) rather than to the giant container.
                        rotationX = -6f
                        rotationY = theta
                        translationX = sin(rad) * radiusPx
                        cameraDistance = cameraPx
                        scaleX = pop
                        scaleY = pop
                        alpha = fade
                    }
                    .then(
                        if (blurDp > 0.5f) Modifier.blur(blurDp.dp) else Modifier,
                    )
                    .clip(RoundedCornerShape(22.dp))
                    .drawBehind {
                        if (proximity > 0.6f) {
                            val g = (proximity - 0.6f) / 0.4f
                            drawCircle(
                                Brush.radialGradient(
                                    listOf(
                                        AlienPink.copy(alpha = 0.45f * g),
                                        Color.Transparent,
                                    ),
                                    radius = size.maxDimension * 0.75f,
                                ),
                                radius = size.maxDimension * 0.75f,
                                center = Offset(size.width / 2f, size.height / 2f),
                            )
                        }
                    },
            ) {
                card(i)

                // Glassmorphism: a frosted sheet + rim light. The scrim
                // greys out edge cards; the front card stays clear.
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(Color.White.copy(alpha = 0.05f))
                        .border(
                            1.dp,
                            Color.White.copy(alpha = 0.20f + 0.10f * proximity),
                            RoundedCornerShape(22.dp),
                        ),
                )
                if (scrimA > 0.01f) {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .background(Color(0xFF14111F).copy(alpha = scrimA)),
                    )
                }
            }
        }
    }
}
