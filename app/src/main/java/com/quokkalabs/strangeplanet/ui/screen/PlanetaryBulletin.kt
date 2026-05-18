package com.quokkalabs.strangeplanet.ui.screen

import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.quokkalabs.strangeplanet.ui.components.AmbientPoster
import com.quokkalabs.strangeplanet.ui.components.AsteroidPoster
import com.quokkalabs.strangeplanet.ui.components.CreaturePoster
import com.quokkalabs.strangeplanet.ui.components.CylinderCarousel
import com.quokkalabs.strangeplanet.ui.components.InvadersPoster
import com.quokkalabs.strangeplanet.ui.components.MergePoster
import com.quokkalabs.strangeplanet.ui.components.PacPoster
import com.quokkalabs.strangeplanet.ui.components.PongPoster
import com.quokkalabs.strangeplanet.ui.components.StrangeMatchPoster
import com.quokkalabs.strangeplanet.ui.theme.AlienPink

private class Destination(
    val title: String,
    val subtitle: String,
    val icon: String,
    val onClick: () -> Unit,
    val poster: @Composable () -> Unit,
)

@Composable
fun PlanetaryBulletin(
    onNavigateToGame: () -> Unit,
    onNavigateToSpaceInvaders: () -> Unit,
    onNavigateToPacman: () -> Unit,
    onNavigateToAsteroids: () -> Unit,
    onNavigateToStrangeMatch: () -> Unit,
    onNavigateToMerge: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val packageInfo = remember {
        context.packageManager.getPackageInfo(context.packageName, 0)
    }
    val pacHighScore = remember {
        context.getSharedPreferences("pac_prefs", 0).getInt("high_score", 0)
    }
    val asteroidHighScore = remember {
        context.getSharedPreferences("asteroid_prefs", 0).getInt("high_score", 0)
    }
    val strangeMatchHighScore = remember {
        context.getSharedPreferences("strange_match_prefs", 0).getInt("high_score", 0)
    }
    val mergeHighScore = remember {
        context.getSharedPreferences("merge_prefs", 0).getInt("high_score", 0)
    }
    val versionName = packageInfo.versionName ?: "?"
    val versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        packageInfo.longVersionCode
    } else {
        @Suppress("DEPRECATION")
        packageInfo.versionCode.toLong()
    }

    val destinations = remember(
        pacHighScore, asteroidHighScore, strangeMatchHighScore, mergeHighScore,
    ) {
        listOf(
            Destination(
                "Sphere Deflection", "Competitive paddle activity", "🏓",
                onNavigateToGame,
            ) { PongPoster() },
            Destination(
                "Descending Entity Defence", "Neutralise hostile creatures", "👾",
                onNavigateToSpaceInvaders,
            ) { InvadersPoster() },
            Destination(
                "Sustenance Pursuit",
                if (pacHighScore > 0) "Record: $pacHighScore sustenance"
                else "Consume stars; evade perished beings",
                "🟡",
                onNavigateToPacman,
            ) { PacPoster() },
            Destination(
                "Spatial Debris Avoidance",
                if (asteroidHighScore > 0) "Record: $asteroidHighScore debris neutralised"
                else "Neutralise drifting fabric tubes",
                "🌀",
                onNavigateToAsteroids,
            ) { AsteroidPoster() },
            Destination(
                "Strange Match",
                if (strangeMatchHighScore > 0) "Record: $strangeMatchHighScore matches"
                else "Swap strange beings; match 3 or more",
                "🍬",
                onNavigateToStrangeMatch,
            ) { StrangeMatchPoster() },
            Destination(
                "Spherical Agglomeration",
                if (mergeHighScore > 0) "Record: $mergeHighScore mass accumulated"
                else "Coalesce spheres; form the void",
                "🪐",
                onNavigateToMerge,
            ) { MergePoster() },
            Destination(
                "Ambient Decoration", "Animate your device surface", "🎨",
                onNavigateToSettings,
            ) { AmbientPoster() },
            Destination(
                "Creature Interaction", "Observe and stimulate beings", "👽",
                onDismiss,
            ) { CreaturePoster() },
        )
    }

    var focused by remember { mutableIntStateOf(0) }

    BackHandler { onDismiss() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.72f))
            .clickable { onDismiss() }
            .windowInsetsPadding(WindowInsets.statusBars)
            .windowInsetsPadding(WindowInsets.navigationBars),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "Planetary Bulletin",
                color = AlienPink,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
            Text(
                text = "Rotate the array · select a destination",
                color = Color.White.copy(alpha = 0.4f),
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 4.dp),
            )

            IconParade(
                icons = destinations.map { it.icon },
                focused = focused,
            )

            Box(
                modifier = Modifier.fillMaxWidth().weight(1f),
                contentAlignment = Alignment.Center,
            ) {
                CylinderCarousel(
                    count = destinations.size,
                    modifier = Modifier.fillMaxSize(),
                    onFocusedChange = { focused = it },
                    onLaunch = { destinations[it].onClick() },
                ) { idx -> destinations[idx].poster() }
            }

            Crossfade(
                targetState = focused,
                animationSpec = tween(260),
                label = "info",
            ) { idx ->
                val d = destinations[idx]
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = d.title,
                        color = AlienPink,
                        fontSize = 19.sp,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center,
                    )
                    Text(
                        text = d.subtitle,
                        color = Color.White.copy(alpha = 0.55f),
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            Box(
                modifier = Modifier
                    .background(AlienPink.copy(alpha = 0.20f), RoundedCornerShape(14.dp))
                    .clickable { destinations[focused].onClick() }
                    .padding(horizontal = 44.dp, vertical = 14.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "▶  ENGAGE",
                    color = AlienPink,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                )
            }

            Spacer(Modifier.height(14.dp))

            Text(
                text = "Proceed Without Destination",
                color = Color.White.copy(alpha = 0.4f),
                fontSize = 13.sp,
                modifier = Modifier
                    .clickable { onDismiss() }
                    .padding(8.dp),
            )

            Text(
                text = "v$versionName · build $versionCode",
                color = Color.White.copy(alpha = 0.2f),
                fontSize = 11.sp,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

/**
 * A five-slot icon parade centred on the focused card: the current game
 * flanked by the previous two and next two (wrapping around the ring). The
 * centre slot is enlarged, fully opaque and seated on an AlienPink disc;
 * neighbours shrink and fade with distance.
 */
@Composable
private fun IconParade(
    icons: List<String>,
    focused: Int,
) {
    val n = icons.size
    if (n == 0) return
    Row(
        modifier = Modifier.padding(top = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        for (offset in -2..2) {
            val idx = ((focused + offset) % n + n) % n
            val dist = if (offset < 0) -offset else offset
            val slot = when (dist) {
                0 -> 40.dp
                1 -> 32.dp
                else -> 26.dp
            }
            val glyph = when (dist) {
                0 -> 22.sp
                1 -> 17.sp
                else -> 13.sp
            }
            val fade = when (dist) {
                0 -> 1f
                1 -> 0.6f
                else -> 0.3f
            }
            Box(
                modifier = Modifier
                    .size(slot)
                    .then(
                        if (dist == 0) {
                            Modifier.background(
                                AlienPink.copy(alpha = 0.22f),
                                CircleShape,
                            )
                        } else {
                            Modifier
                        },
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = icons[idx],
                    fontSize = glyph,
                    modifier = Modifier.alpha(fade),
                )
            }
        }
    }
}
