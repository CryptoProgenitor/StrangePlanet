package com.quokkalabs.strangeplanet.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val StrangePlanetColorScheme = darkColorScheme(
    primary = AlienPink,
    secondary = CosmicPurple,
    tertiary = SoftPink,
    background = DeepNavy,
    surface = CardPink,
    onPrimary = DeepNavy,
    onSecondary = Color.White,
    onTertiary = Color.White,
    onBackground = Color.White,
    onSurface = DeepNavy,
)

@Composable
fun StrangePlanetTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = StrangePlanetColorScheme,
        typography = Typography,
        content = content,
    )
}
