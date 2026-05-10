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
import com.quokkalabs.strangeplanet.ui.screen.SettingsScreen
import com.quokkalabs.strangeplanet.ui.theme.StrangePlanetTheme
import com.quokkalabs.strangeplanet.ui.viewmodel.StrangePlanetViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            StrangePlanetTheme {
                val viewModel: StrangePlanetViewModel = viewModel()
                var showSettings by rememberSaveable { mutableStateOf(false) }

                if (showSettings) {
                    SettingsScreen(
                        viewModel = viewModel,
                        onBack = { showSettings = false },
                    )
                } else {
                    InteractiveScreen(
                        viewModel = viewModel,
                        onOpenSettings = { showSettings = true },
                    )
                }
            }
        }
    }
}
