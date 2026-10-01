package com.quokkalabs.strangeplanet.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.quokkalabs.strangeplanet.data.model.CreatureState
import kotlin.math.roundToInt

/**
 * [creature] supplies the type and size; [current] supplies the live position and
 * rotation and is only read in the layout/draw phases, so a moving creature neither
 * recomposes nor re-measures — it is just placed and drawn again.
 */
@Composable
fun CreatureSprite(
    creature: CreatureState,
    sizeScale: Float = 1f,
    current: () -> CreatureState = { creature },
    onTap: () -> Unit,
) {
    val density = LocalDensity.current
    val sizePx = creature.size * sizeScale * density.density

    Box(
        modifier = Modifier
            .offset {
                val c = current()
                IntOffset(
                    (c.x - sizePx / 2).roundToInt(),
                    (c.y - sizePx / 2).roundToInt(),
                )
            }
            .size((creature.size * sizeScale).dp),
    ) {
        Image(
            painter = painterResource(id = creature.type.drawableRes),
            contentDescription = creature.type.displayName,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { rotationZ = current().rotation }
                .pointerInput(creature.type) {
                    detectTapGestures { onTap() }
                },
        )
    }
}
