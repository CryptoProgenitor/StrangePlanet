package com.quokkalabs.strangeplanet.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.quokkalabs.strangeplanet.data.model.CreatureState

@Composable
fun CreatureSprite(
    creature: CreatureState,
    sizeScale: Float = 1f,
    onTap: () -> Unit,
) {
    val density = LocalDensity.current
    val sizePx = creature.size * sizeScale * density.density

    Box(
        modifier = Modifier
            .offset(
                x = with(density) { (creature.x - sizePx / 2).toDp() },
                y = with(density) { (creature.y - sizePx / 2).toDp() },
            )
            .size((creature.size * sizeScale).dp),
    ) {
        Image(
            painter = painterResource(id = creature.type.drawableRes),
            contentDescription = creature.type.displayName,
            modifier = Modifier
                .fillMaxSize()
                .rotate(creature.rotation)
                .pointerInput(creature.type) {
                    detectTapGestures { onTap() }
                },
        )
    }
}
