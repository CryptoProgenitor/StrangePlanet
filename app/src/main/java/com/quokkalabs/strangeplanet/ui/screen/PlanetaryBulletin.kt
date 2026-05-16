package com.quokkalabs.strangeplanet.ui.screen

import android.os.Build
import androidx.compose.foundation.background
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.quokkalabs.strangeplanet.ui.theme.AlienPink
import com.quokkalabs.strangeplanet.ui.theme.DeepNavy

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
    val asteroidHighScore = remember {
        context.getSharedPreferences("asteroid_prefs", 0).getInt("high_score", 0)
    }
    val pacHighScore = remember {
        context.getSharedPreferences("pac_prefs", 0).getInt("high_score", 0)
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

    BackHandler { onDismiss() }

    // Full-screen scrim — tapping the scrim dismisses the bulletin
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.6f))
            .clickable { onDismiss() }
            .windowInsetsPadding(WindowInsets.navigationBars),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 340.dp)
                .fillMaxWidth(0.9f)
                .background(DeepNavy.copy(alpha = 0.95f), RoundedCornerShape(24.dp))
                .verticalScroll(rememberScrollState())
                .padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Header
            Text(
                text = "Planetary Bulletin",
                color = AlienPink,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.height(8.dp))

            // Intro
            Text(
                text = "This application contains multiple\nexperiences for your stimulation.",
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.height(6.dp))

            // Readme
            Text(
                text = "Select a destination to commence activity.",
                color = Color.White.copy(alpha = 0.4f),
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.height(20.dp))

            // ── Nav cards ──

            BulletinCard(
                emoji = "🎨",
                title = "Ambient Decoration",
                subtitle = "Animate your device surface",
                onClick = onNavigateToSettings,
            )

            Spacer(Modifier.height(10.dp))

            BulletinCard(
                emoji = "🏓",
                title = "Sphere Deflection",
                subtitle = "Competitive paddle activity",
                onClick = onNavigateToGame,
            )

            Spacer(Modifier.height(10.dp))

            BulletinCard(
                emoji = "👾",
                title = "Descending Entity Defence",
                subtitle = "Neutralise hostile creatures",
                onClick = onNavigateToSpaceInvaders,
            )

            Spacer(Modifier.height(10.dp))

            BulletinCard(
                emoji = "🍬",
                title = "Strange Match",
                subtitle = if (strangeMatchHighScore > 0) "Record: $strangeMatchHighScore matches"
                    else "Swap strange beings; match 3 or more",
                onClick = onNavigateToStrangeMatch,
            )

            Spacer(Modifier.height(10.dp))

            BulletinCard(
                emoji = "🪐",
                title = "Spherical Agglomeration",
                subtitle = if (mergeHighScore > 0) "Record: $mergeHighScore mass accumulated"
                    else "Coalesce spheres; form the void",
                onClick = onNavigateToMerge,
            )

            Spacer(Modifier.height(10.dp))

            BulletinCard(
                emoji = "🟡",
                title = "Sustenance Pursuit",
                subtitle = if (pacHighScore > 0) "Record: $pacHighScore sustenance"
                    else "Consume stars; evade perished beings",
                onClick = onNavigateToPacman,
            )

            Spacer(Modifier.height(10.dp))

            BulletinCard(
                emoji = "🌀",
                title = "Spatial Debris Avoidance",
                subtitle = if (asteroidHighScore > 0) "Record: $asteroidHighScore debris neutralised"
                    else "Neutralise drifting fabric tubes",
                onClick = onNavigateToAsteroids,
            )

            Spacer(Modifier.height(10.dp))

            BulletinCard(
                emoji = "👽",
                title = "Creature Interaction",
                subtitle = "Observe and stimulate beings",
                onClick = onDismiss,
            )

            Spacer(Modifier.height(20.dp))

            // Dismiss
            Text(
                text = "Proceed Without Destination",
                color = Color.White.copy(alpha = 0.4f),
                fontSize = 13.sp,
                modifier = Modifier
                    .clickable { onDismiss() }
                    .padding(8.dp),
            )

            Spacer(Modifier.height(12.dp))

            // Version footer
            Text(
                text = "v$versionName · build $versionCode",
                color = Color.White.copy(alpha = 0.2f),
                fontSize = 11.sp,
            )
        }
    }
}

@Composable
private fun BulletinCard(
    emoji: String,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Color.White.copy(alpha = 0.08f),
                RoundedCornerShape(14.dp),
            )
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = emoji,
            fontSize = 24.sp,
        )
        Spacer(Modifier.width(14.dp))
        Column {
            Text(
                text = title,
                color = AlienPink,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = subtitle,
                color = Color.White.copy(alpha = 0.5f),
                fontSize = 12.sp,
            )
        }
    }
}
