package com.quokkalabs.strangeplanet.ui.components

import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.annotation.DrawableRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Decodes a sprite once at roughly the size it is drawn, so the GPU isn't sampling a
 * 500 px texture down to ~80 px for every sprite on every frame (there are no mipmaps,
 * so that is slow on some GPUs and shimmers when moving).
 */
fun decodeScaled(res: Resources, @DrawableRes id: Int, maxSidePx: Int): ImageBitmap {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeResource(res, id, bounds)
    val target = maxSidePx.coerceAtLeast(1)
    var sample = 1
    while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= target) sample *= 2
    val src = BitmapFactory.decodeResource(res, id, BitmapFactory.Options().apply { inSampleSize = sample })
    val scale = target.toFloat() / max(src.width, src.height)
    if (scale >= 1f) return src.asImageBitmap()
    val scaled = Bitmap.createScaledBitmap(
        src,
        (src.width * scale).roundToInt().coerceAtLeast(1),
        (src.height * scale).roundToInt().coerceAtLeast(1),
        true,
    )
    if (scaled !== src) src.recycle()
    return scaled.asImageBitmap()
}

@Composable
fun rememberScaledImage(@DrawableRes id: Int, maxSidePx: Int): ImageBitmap {
    val res = LocalContext.current.resources
    return remember(id, maxSidePx) { decodeScaled(res, id, maxSidePx) }
}
