package com.quokkalabs.strangeplanet.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.quokkalabs.strangeplanet.ui.theme.CosmicGradient

@Composable
fun CosmicBackground(
    showStars: Boolean = true,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(CosmicGradient),
    ) {
        if (showStars) StarField()
        content()
    }
}
