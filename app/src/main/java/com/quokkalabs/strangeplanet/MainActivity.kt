package com.quokkalabs.strangeplanet

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel
import com.quokkalabs.strangeplanet.ui.screen.InteractiveScreen
import com.quokkalabs.strangeplanet.ui.screen.AsteroidScreen
import com.quokkalabs.strangeplanet.ui.screen.MergeScreen
import com.quokkalabs.strangeplanet.ui.screen.PlanetaryBulletin
import com.quokkalabs.strangeplanet.ui.screen.PacScreen
import com.quokkalabs.strangeplanet.ui.screen.PongScreen
import com.quokkalabs.strangeplanet.ui.screen.SettingsScreen
import com.quokkalabs.strangeplanet.ui.screen.SpaceInvadersScreen
import com.quokkalabs.strangeplanet.ui.screen.StrangeMatchScreen
import com.quokkalabs.strangeplanet.ui.theme.StrangePlanetTheme
import com.quokkalabs.strangeplanet.ui.viewmodel.AsteroidViewModel
import com.quokkalabs.strangeplanet.ui.viewmodel.MergeViewModel
import com.quokkalabs.strangeplanet.ui.viewmodel.PacViewModel
import com.quokkalabs.strangeplanet.ui.viewmodel.PongViewModel
import com.quokkalabs.strangeplanet.ui.viewmodel.SpaceInvadersViewModel
import com.quokkalabs.strangeplanet.ui.viewmodel.StrangePlanetViewModel
import com.quokkalabs.strangeplanet.ui.viewmodel.StrangeMatchViewModel

private enum class Screen {
    INTERACTIVE, SETTINGS, PONG, SPACE_INVADERS, PACMAN, ASTEROIDS, STRANGE_MATCH, MERGE
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            StrangePlanetTheme {
                val viewModel: StrangePlanetViewModel = viewModel()
                var screen by rememberSaveable { mutableStateOf(Screen.INTERACTIVE) }
                var showBulletin by remember { mutableStateOf(true) }

                Box {
                    when (screen) {
                        Screen.INTERACTIVE -> InteractiveScreen(
                            viewModel = viewModel,
                            onOpenSettings = { screen = Screen.SETTINGS },
                            onOpenGame = { showBulletin = true },
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
                        Screen.SPACE_INVADERS -> {
                            val siViewModel: SpaceInvadersViewModel = viewModel()
                            SpaceInvadersScreen(
                                viewModel = siViewModel,
                                onBack = { screen = Screen.INTERACTIVE },
                            )
                        }
                        Screen.PACMAN -> {
                            val pacViewModel: PacViewModel = viewModel()
                            PacScreen(
                                viewModel = pacViewModel,
                                onBack = { screen = Screen.INTERACTIVE },
                            )
                        }
                        Screen.ASTEROIDS -> {
                            val asteroidViewModel: AsteroidViewModel = viewModel()
                            AsteroidScreen(
                                viewModel = asteroidViewModel,
                                onBack = { screen = Screen.INTERACTIVE },
                            )
                        }
                        Screen.STRANGE_MATCH -> {
                            val smViewModel: StrangeMatchViewModel = viewModel()
                            StrangeMatchScreen(
                                viewModel = smViewModel,
                                onBack = { screen = Screen.INTERACTIVE },
                            )
                        }
                        Screen.MERGE -> {
                            val mergeViewModel: MergeViewModel = viewModel()
                            MergeScreen(
                                viewModel = mergeViewModel,
                                onBack = { screen = Screen.INTERACTIVE },
                            )
                        }
                    }

                    if (showBulletin && screen == Screen.INTERACTIVE) {
                        PlanetaryBulletin(
                            onNavigateToGame = {
                                showBulletin = false
                                screen = Screen.PONG
                            },
                            onNavigateToSpaceInvaders = {
                                showBulletin = false
                                screen = Screen.SPACE_INVADERS
                            },
                            onNavigateToPacman = {
                                showBulletin = false
                                screen = Screen.PACMAN
                            },
                            onNavigateToAsteroids = {
                                showBulletin = false
                                screen = Screen.ASTEROIDS
                            },
                            onNavigateToStrangeMatch = {
                                showBulletin = false
                                screen = Screen.STRANGE_MATCH
                            },
                            onNavigateToMerge = {
                                showBulletin = false
                                screen = Screen.MERGE
                            },
                            onNavigateToSettings = {
                                showBulletin = false
                                screen = Screen.SETTINGS
                            },
                            onDismiss = {
                                showBulletin = false
                            },
                        )
                    }
                }
            }
        }
    }
}
