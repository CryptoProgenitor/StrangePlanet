package com.quokkalabs.strangeplanet.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.quokkalabs.strangeplanet.ui.theme.AlienPink
import com.quokkalabs.strangeplanet.ui.theme.CosmicPurple
import com.quokkalabs.strangeplanet.ui.theme.DeepNavy
import kotlinx.coroutines.delay

private val MAX_BUBBLE_HEIGHT = 90.dp

@Composable
fun SpeechBubble(
    text: String,
    visible: Boolean,
    spokenRange: IntRange? = null,
    pointerOffsetDp: Dp = 0.dp,
    modifier: Modifier = Modifier,
) {
    val scrollState = rememberScrollState()
    val density = LocalDensity.current
    var overflows by remember { mutableStateOf(false) }
    val maxHeightPx = with(density) { MAX_BUBBLE_HEIGHT.toPx() }
    val pointerOffsetPx = with(density) { pointerOffsetDp.toPx() }
    var textLayoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }

    val rippleTransition = rememberInfiniteTransition(label = "ripple")
    val rippleAlpha by rippleTransition.animateFloat(
        initialValue = 0.15f,
        targetValue = 0.45f,
        animationSpec = infiniteRepeatable(
            animation = tween(400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "rippleAlpha",
    )

    val styledText = buildAnnotatedString {
        append(text)
        if (spokenRange != null) {
            val safeStart = spokenRange.first.coerceIn(0, text.length)
            val safeEnd = (spokenRange.last + 1).coerceIn(0, text.length)
            if (safeStart > 0) {
                addStyle(SpanStyle(color = CosmicPurple), 0, safeStart)
            }
            if (safeStart < safeEnd) {
                addStyle(
                    SpanStyle(color = AlienPink, fontWeight = FontWeight.SemiBold),
                    safeStart,
                    safeEnd,
                )
            }
        }
    }

    LaunchedEffect(visible, text, overflows) {
        if (!visible || !overflows) return@LaunchedEffect
        scrollState.scrollTo(0)
        delay(1200)
        while (true) {
            scrollState.animateScrollTo(scrollState.maxValue)
            delay(1500)
            scrollState.animateScrollTo(0)
            delay(1500)
        }
    }

    AnimatedVisibility(
        visible = visible,
        enter = scaleIn(initialScale = 0.5f) + fadeIn(),
        exit = scaleOut(targetScale = 0.5f) + fadeOut(),
        modifier = modifier,
    ) {
        Box(contentAlignment = Alignment.BottomCenter) {
            Box(
                modifier = Modifier
                    .background(Color.White.copy(alpha = 0.85f), RoundedCornerShape(16.dp))
                    .drawBehind {
                        val pointerX = (size.width / 2 + pointerOffsetPx)
                            .coerceIn(20.dp.toPx(), size.width - 20.dp.toPx())
                        val path = Path().apply {
                            moveTo(pointerX - 10.dp.toPx(), size.height)
                            lineTo(pointerX, size.height + 12.dp.toPx())
                            lineTo(pointerX + 10.dp.toPx(), size.height)
                        }
                        drawPath(path, Color.White.copy(alpha = 0.85f))
                    }
                    .widthIn(min = 160.dp, max = 320.dp)
                    .heightIn(min = 54.dp, max = MAX_BUBBLE_HEIGHT)
                    .verticalScroll(scrollState)
                    .padding(horizontal = 14.dp, vertical = 10.dp)
                    .onSizeChanged { size ->
                        overflows = size.height > maxHeightPx
                    },
            ) {
                Text(
                    text = styledText,
                    color = DeepNavy,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center,
                    lineHeight = 17.sp,
                    onTextLayout = { textLayoutResult = it },
                    modifier = Modifier.drawWithContent {
                        if (spokenRange != null && text.isNotEmpty()) {
                            textLayoutResult?.let { layout ->
                                val start = spokenRange.first.coerceIn(0, text.length - 1)
                                val end = spokenRange.last.coerceIn(0, text.length - 1)
                                if (start <= end) {
                                    val lineStart = layout.getLineForOffset(start)
                                    val lineEnd = layout.getLineForOffset(end)
                                    for (line in lineStart..lineEnd) {
                                        val lcs = if (line == lineStart) start
                                            else layout.getLineStart(line)
                                        val lce = if (line == lineEnd) end
                                            else (layout.getLineEnd(line) - 1).coerceAtLeast(lcs)
                                        val startBox = layout.getBoundingBox(lcs)
                                        val endBox = layout.getBoundingBox(lce)
                                        drawRoundRect(
                                            color = CosmicPurple.copy(alpha = rippleAlpha * 0.4f),
                                            topLeft = Offset(
                                                startBox.left - 4.dp.toPx(),
                                                startBox.top - 2.dp.toPx(),
                                            ),
                                            size = Size(
                                                endBox.right - startBox.left + 8.dp.toPx(),
                                                endBox.bottom - startBox.top + 4.dp.toPx(),
                                            ),
                                            cornerRadius = CornerRadius(6.dp.toPx()),
                                        )
                                        drawRoundRect(
                                            color = AlienPink.copy(alpha = rippleAlpha),
                                            topLeft = Offset(
                                                startBox.left - 2.dp.toPx(),
                                                startBox.top - 1.dp.toPx(),
                                            ),
                                            size = Size(
                                                endBox.right - startBox.left + 4.dp.toPx(),
                                                endBox.bottom - startBox.top + 2.dp.toPx(),
                                            ),
                                            cornerRadius = CornerRadius(4.dp.toPx()),
                                        )
                                    }
                                }
                            }
                        }
                        drawContent()
                    },
                )
            }
        }
    }
}
