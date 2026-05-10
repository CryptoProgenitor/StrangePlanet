package com.quokkalabs.strangeplanet

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel
import com.quokkalabs.strangeplanet.ui.screen.InteractiveScreen
import com.quokkalabs.strangeplanet.ui.screen.PongScreen
import com.quokkalabs.strangeplanet.ui.screen.SettingsScreen
import com.quokkalabs.strangeplanet.ui.theme.StrangePlanetTheme
import com.quokkalabs.strangeplanet.ui.viewmodel.PongViewModel
import com.quokkalabs.strangeplanet.ui.viewmodel.StrangePlanetViewModel

private enum class Screen {
    INTERACTIVE, SETTINGS, PONG
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            StrangePlanetTheme {
                val viewModel: StrangePlanetViewModel = viewModel()
                var screen by rememberSaveable { mutableStateOf(Screen.INTERACTIVE) }

                when (screen) {
                    Screen.INTERACTIVE -> InteractiveScreen(
                        viewModel = viewModel,
                        onOpenSettings = { screen = Screen.SETTINGS },
                        onOpenGame = { screen = Screen.PONG },
                    )
                    Screen.SETTINGS -> SettingsScreen(
                        viewModel = viewModel,
                        onBack = { screen = Screen.INTERACTIVE },
                    )
                    Screen.PONG -> {
                        val pongViewModel: PongViewModel = viewModel()
                        PongScreen(
                            viewModel = pongViewModel,
                            onBack = { screen = Screen.INTERACTIVE },
                        )
                    }
                }
            }
        }
    }
}
