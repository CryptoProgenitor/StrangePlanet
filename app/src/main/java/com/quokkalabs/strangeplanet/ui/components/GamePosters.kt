package com.quokkalabs.strangeplanet.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import com.quokkalabs.strangeplanet.ui.theme.AlienPink
import com.quokkalabs.strangeplanet.ui.theme.CardPink
import com.quokkalabs.strangeplanet.ui.theme.SoftPink
import kotlin.math.cos
import kotlin.math.sin

/**
 * Lightweight, engine-free "poster" composables — one per launcher
 * destination. Each draws a representative, gently animated scene using the
 * same visual language (palette, shapes, proportions) as the real game, so
 * the carousel never carries stale screenshot assets. No ViewModels, no game
 * logic — just an ambient Canvas that stays in sync with the codebase.
 */

private val SpaceTop = Color(0xFF120A22)
private val SpaceBottom = Color(0xFF1A0030)
private val FieldNavy = Color(0xFF1B1733)

private data class Star(val fx: Float, val fy: Float, val r: Float, val a: Float)

private fun starfield(count: Int, seed: Int): List<Star> {
    var s = seed
    fun rnd(): Float {
        s = (s * 1664525 + 1013904223) and 0x7FFFFFFF
        return (s % 10000) / 10000f
    }
    return List(count) { Star(rnd(), rnd(), 0.6f + rnd() * 1.4f, 0.25f + rnd() * 0.45f) }
}

private fun DrawScope.space(stars: List<Star>) {
    drawRect(Brush.verticalGradient(listOf(SpaceTop, SpaceBottom)))
    stars.forEach { st ->
        drawCircle(
            Color.White.copy(alpha = st.a),
            st.r * density,
            Offset(st.fx * size.width, st.fy * size.height),
        )
    }
}

private fun DrawScope.navyField() {
    drawRect(Brush.verticalGradient(listOf(FieldNavy, Color(0xFF15122A))))
}

// ── Sphere Deflection (Pong) ────────────────────────────────────────────────

@Composable
fun PongPoster(modifier: Modifier = Modifier) {
    val t = rememberInfiniteTransition(label = "pong")
    val px by t.animateFloat(
        0f, 1f,
        infiniteRepeatable(tween(2600, easing = LinearEasing), RepeatMode.Reverse),
        label = "px",
    )
    val py by t.animateFloat(
        0.25f, 0.78f,
        infiniteRepeatable(tween(1500, easing = LinearEasing), RepeatMode.Reverse),
        label = "py",
    )
    Canvas(modifier.fillMaxSize()) {
        navyField()
        val w = size.width
        val h = size.height

        // Centre dashed line
        drawLine(
            Color.White.copy(alpha = 0.12f),
            Offset(w / 2f, h * 0.06f),
            Offset(w / 2f, h * 0.94f),
            strokeWidth = dpf(2f),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 14f)),
        )

        val padW = w * 0.06f
        val padH = h * 0.16f
        val padR = padW / 2f
        // Left paddle tracks ball loosely, right paddle mirrors.
        val lY = (py * h).coerceIn(padH / 2f, h - padH / 2f)
        val rY = ((1f - py) * h).coerceIn(padH / 2f, h - padH / 2f)
        drawRoundRect(
            CardPink, Offset(w * 0.08f, lY - padH / 2f), Size(padW, padH),
            CornerRadius(padR, padR),
        )
        drawRoundRect(
            CardPink, Offset(w * 0.92f - padW, rY - padH / 2f), Size(padW, padH),
            CornerRadius(padR, padR),
        )

        val bx = w * (0.16f + px * 0.68f)
        val by = h * (0.2f + py * 0.6f)
        val br = w * 0.035f
        // Trail
        for (i in 1..5) {
            val tx = bx - (px - 0.5f) * i * w * 0.03f
            drawCircle(SoftPink.copy(alpha = 0.10f * (6 - i)), br * (1f - i * 0.12f), Offset(tx, by))
        }
        drawCircle(
            Brush.radialGradient(
                listOf(Color.White, AlienPink, SoftPink.copy(alpha = 0f)),
                center = Offset(bx, by), radius = br * 3.2f,
            ),
            br * 3.2f, Offset(bx, by),
        )
        drawCircle(AlienPink, br, Offset(bx, by))
    }
}

// ── Descending Entity Defence (Space Invaders) ──────────────────────────────

@Composable
fun InvadersPoster(modifier: Modifier = Modifier) {
    val stars = remember { starfield(26, 11) }
    val t = rememberInfiniteTransition(label = "si")
    val sway by t.animateFloat(
        -1f, 1f,
        infiniteRepeatable(tween(2200, easing = LinearEasing), RepeatMode.Reverse),
        label = "sway",
    )
    val shot by t.animateFloat(
        0f, 1f,
        infiniteRepeatable(tween(1300, easing = LinearEasing), RepeatMode.Restart),
        label = "shot",
    )
    Canvas(modifier.fillMaxSize()) {
        space(stars)
        val w = size.width
        val h = size.height
        val rowColors = listOf(Color(0xFFFF6B6B), Color(0xFFFFB347), Color(0xFF00E5FF))
        val cols = 4
        val cellW = w * 0.16f
        val cellH = h * 0.10f
        val startX = w * 0.5f - (cols - 1) * cellW * 0.5f + sway * w * 0.05f
        rowColors.forEachIndexed { r, c ->
            for (cIdx in 0 until cols) {
                val cx = startX + cIdx * cellW
                val cy = h * (0.16f + r * 0.13f)
                val bw = cellW * 0.52f
                val bh = cellH * 0.62f
                drawRoundRect(
                    c, Offset(cx - bw / 2f, cy - bh / 2f), Size(bw, bh),
                    CornerRadius(bw * 0.28f, bw * 0.28f),
                )
                // eyes
                drawCircle(Color(0xFF120A22), bw * 0.10f, Offset(cx - bw * 0.18f, cy))
                drawCircle(Color(0xFF120A22), bw * 0.10f, Offset(cx + bw * 0.18f, cy))
            }
        }

        // Player cannon — inverted-T silhouette: wide flat base + short wide turret
        val pcx = w * 0.5f + sway * w * 0.04f
        val pcy = h * 0.88f
        val pw = w * 0.16f
        // Base platform
        drawRoundRect(
            AlienPink, Offset(pcx - pw / 2f, pcy - h * 0.020f), Size(pw, h * 0.040f),
            CornerRadius(pw * 0.20f, pw * 0.20f),
        )
        // Short, wide turret (3:1 aspect ratio — clearly a gun nozzle, not a barrel)
        val tw = w * 0.072f
        val th = h * 0.024f
        drawRoundRect(
            AlienPink, Offset(pcx - tw / 2f, pcy - h * 0.020f - th), Size(tw, th),
            CornerRadius(tw * 0.25f, tw * 0.25f),
        )

        // Projectile
        val sy = pcy - shot * h * 0.55f
        drawCircle(SoftPink.copy(alpha = 0.5f), w * 0.03f, Offset(pcx, sy))
        drawRoundRect(
            SoftPink, Offset(pcx - w * 0.01f, sy - h * 0.02f), Size(w * 0.02f, h * 0.04f),
            CornerRadius(4f, 4f),
        )
    }
}

// ── Strange Match ───────────────────────────────────────────────────────────

@Composable
fun StrangeMatchPoster(modifier: Modifier = Modifier) {
    val stars = remember { starfield(20, 23) }
    val t = rememberInfiniteTransition(label = "sm")
    val pulse by t.animateFloat(
        0.4f, 1f,
        infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Reverse),
        label = "pulse",
    )
    Canvas(modifier.fillMaxSize()) {
        space(stars)
        val palette = listOf(
            Color(0xFF6B35B8), Color(0xFFBF2020), Color(0xFFCC3680), Color(0xFF8B5FBF),
            Color(0xFF2255AA), Color(0xFF2E7D4F), Color(0xFFB8740A),
        )
        val rows = 5
        val colsN = 4
        val gridW = size.width * 0.74f
        val cell = gridW / colsN
        val ox = (size.width - gridW) / 2f
        val oy = (size.height - cell * rows) / 2f
        var k = 7
        for (r in 0 until rows) for (c in 0 until colsN) {
            k = (k * 1103515245 + 12345) and 0x7FFFFFFF
            val color = palette[k % palette.size]
            val pad = cell * 0.10f
            val matched = (r == 2 && c in 1..3)
            drawRoundRect(
                color.copy(alpha = if (matched) pulse else 1f),
                Offset(ox + c * cell + pad, oy + r * cell + pad),
                Size(cell - pad * 2, cell - pad * 2),
                CornerRadius(cell * 0.18f, cell * 0.18f),
            )
            if (r == 1 && c == 1) {
                drawRoundRect(
                    AlienPink, Offset(ox + c * cell + pad, oy + r * cell + pad),
                    Size(cell - pad * 2, cell - pad * 2),
                    CornerRadius(cell * 0.18f, cell * 0.18f),
                    style = Stroke(dpf(2f)),
                )
            }
        }
    }
}

// ── Spherical Agglomeration (Merge) ─────────────────────────────────────────

@Composable
fun MergePoster(modifier: Modifier = Modifier) {
    val stars = remember { starfield(22, 31) }
    val t = rememberInfiniteTransition(label = "merge")
    val bob by t.animateFloat(
        -1f, 1f,
        infiniteRepeatable(tween(2000, easing = LinearEasing), RepeatMode.Reverse),
        label = "bob",
    )
    Canvas(modifier.fillMaxSize()) {
        space(stars)
        val w = size.width
        val h = size.height
        val vesselTop = h * 0.30f
        val vesselBottom = h * 0.92f
        val vesselL = w * 0.16f
        val vesselR = w * 0.84f
        val wall = Color(0xFF9FB6E0).copy(alpha = 0.55f)
        // Dashed capacity line
        drawLine(
            AlienPink.copy(alpha = 0.30f),
            Offset(vesselL, vesselTop), Offset(vesselR, vesselTop),
            strokeWidth = dpf(2f),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 10f)),
        )
        // U-shaped vessel
        val path = Path().apply {
            moveTo(vesselL, vesselTop)
            lineTo(vesselL, vesselBottom)
            lineTo(vesselR, vesselBottom)
            lineTo(vesselR, vesselTop)
        }
        drawPath(path, wall, style = Stroke(dpf(5f), cap = StrokeCap.Round))

        data class Orb(val cx: Float, val cy: Float, val r: Float, val col: Color)
        val orbs = listOf(
            Orb(w * 0.38f, vesselBottom - h * 0.10f, w * 0.13f, Color(0xFF2F6FB0)),
            Orb(w * 0.63f, vesselBottom - h * 0.09f, w * 0.10f, Color(0xFFB05A34)),
            Orb(w * 0.50f, vesselBottom - h * 0.28f, w * 0.16f, Color(0xFFFFD66B)),
            Orb(w * 0.40f, vesselBottom - h * 0.46f, w * 0.09f, Color(0xFFD7E2F0)),
        )
        orbs.forEachIndexed { i, o ->
            val dy = bob * h * 0.006f * (if (i % 2 == 0) 1f else -1f)
            val c = Offset(o.cx, o.cy + dy)
            drawCircle(
                Brush.radialGradient(
                    listOf(Color.White.copy(alpha = 0.85f), o.col, o.col.copy(alpha = 0.85f)),
                    center = Offset(c.x - o.r * 0.35f, c.y - o.r * 0.35f),
                    radius = o.r * 1.6f,
                ),
                o.r, c,
            )
        }
    }
}

// ── Sustenance Pursuit (Pac) ────────────────────────────────────────────────

@Composable
fun PacPoster(modifier: Modifier = Modifier) {
    val t = rememberInfiniteTransition(label = "pac")
    val mouth by t.animateFloat(
        4f, 42f,
        infiniteRepeatable(tween(380, easing = LinearEasing), RepeatMode.Reverse),
        label = "mouth",
    )
    Canvas(modifier.fillMaxSize()) {
        navyField()
        val w = size.width
        val h = size.height
        val wallC = Color(0xFF5A9FD8).copy(alpha = 0.70f)
        val tile = w * 0.13f
        // A few maze wall blocks (a readable fragment, not the full grid).
        val blocks = listOf(
            Offset(1f, 1f), Offset(2f, 1f), Offset(4f, 1f), Offset(5f, 1f),
            Offset(1f, 3f), Offset(5f, 3f),
            Offset(3f, 2f), Offset(3f, 4f),
        )
        val ox = (w - tile * 7) / 2f
        val oy = h * 0.16f
        blocks.forEach { b ->
            drawRoundRect(
                wallC, Offset(ox + b.x * tile, oy + b.y * tile),
                Size(tile * 0.9f, tile * 0.9f),
                CornerRadius(tile * 0.22f, tile * 0.22f),
            )
        }
        // Pellets
        for (c in 0..6) {
            drawCircle(Color(0xFFEAD9FF), tile * 0.10f, Offset(ox + c * tile + tile * 0.45f, oy + 5.6f * tile))
        }

        // Being (player) with glow + animated mouth
        val pc = Offset(ox + tile * 1.0f, oy + tile * 5.6f)
        val pr = tile * 0.42f
        drawCircle(AlienPink.copy(alpha = 0.30f), pr * 1.7f, pc)
        val being = Path().apply {
            moveTo(pc.x, pc.y)
            arcTo(
                androidx.compose.ui.geometry.Rect(pc.x - pr, pc.y - pr, pc.x + pr, pc.y + pr),
                mouth, 360f - mouth * 2f, false,
            )
            close()
        }
        drawPath(being, Color(0xFFFFD66B))

        // Two ghosts
        fun ghost(cx: Float, col: Color) {
            val gc = Offset(cx, oy + tile * 5.6f)
            val gr = tile * 0.42f
            drawArc(col, 180f, 180f, true, Offset(gc.x - gr, gc.y - gr), Size(gr * 2, gr * 2))
            drawRect(col, Offset(gc.x - gr, gc.y), Size(gr * 2, gr * 0.9f))
            drawCircle(Color.White, gr * 0.26f, Offset(gc.x - gr * 0.32f, gc.y - gr * 0.08f))
            drawCircle(Color.White, gr * 0.26f, Offset(gc.x + gr * 0.32f, gc.y - gr * 0.08f))
            drawCircle(Color(0xFF1A0050), gr * 0.12f, Offset(gc.x - gr * 0.30f, gc.y - gr * 0.04f))
            drawCircle(Color(0xFF1A0050), gr * 0.12f, Offset(gc.x + gr * 0.34f, gc.y - gr * 0.04f))
        }
        ghost(ox + tile * 4.0f, Color(0xFFFF6B6B))
        ghost(ox + tile * 5.4f, Color(0xFF00E5FF))
    }
}

// ── Spatial Debris Avoidance (Asteroids) ────────────────────────────────────

@Composable
fun AsteroidPoster(modifier: Modifier = Modifier) {
    val stars = remember { starfield(24, 41) }
    val t = rememberInfiniteTransition(label = "ast")
    val spin by t.animateFloat(
        0f, 360f,
        infiniteRepeatable(tween(9000, easing = LinearEasing), RepeatMode.Restart),
        label = "spin",
    )
    val thrust by t.animateFloat(
        0.2f, 0.5f,
        infiniteRepeatable(tween(500, easing = LinearEasing), RepeatMode.Reverse),
        label = "thrust",
    )
    val bullet by t.animateFloat(
        0f, 1f,
        infiniteRepeatable(tween(1100, easing = LinearEasing), RepeatMode.Restart),
        label = "bullet",
    )
    Canvas(modifier.fillMaxSize()) {
        space(stars)
        val w = size.width
        val h = size.height
        val rock = Color(0xFFB59A6E)

        fun rock(cx: Float, cy: Float, rad: Float, phase: Float) {
            rotate(spin + phase, Offset(cx, cy)) {
                val p = Path()
                val pts = 8
                for (i in 0..pts) {
                    val ang = (i.toFloat() / pts) * 2f * Math.PI.toFloat()
                    val rr = rad * (0.75f + 0.25f * sin(ang * 3f + phase))
                    val x = cx + cos(ang) * rr
                    val y = cy + sin(ang) * rr
                    if (i == 0) p.moveTo(x, y) else p.lineTo(x, y)
                }
                p.close()
                drawPath(p, rock.copy(alpha = 0.85f))
                drawPath(p, Color(0xFF8A7444), style = Stroke(dpf(2f)))
            }
        }
        rock(w * 0.26f, h * 0.28f, w * 0.13f, 0f)
        rock(w * 0.74f, h * 0.34f, w * 0.10f, 120f)
        rock(w * 0.62f, h * 0.70f, w * 0.15f, 240f)

        // Ship
        val sc = Offset(w * 0.40f, h * 0.62f)
        val ss = w * 0.16f
        drawCircle(AlienPink.copy(alpha = thrust), ss * 0.7f, Offset(sc.x, sc.y + ss * 0.6f))
        val ship = Path().apply {
            moveTo(sc.x, sc.y - ss)
            lineTo(sc.x - ss * 0.7f, sc.y + ss * 0.7f)
            lineTo(sc.x, sc.y + ss * 0.35f)
            lineTo(sc.x + ss * 0.7f, sc.y + ss * 0.7f)
            close()
        }
        drawPath(ship, AlienPink)
        drawPath(ship, Color.White.copy(alpha = 0.4f), style = Stroke(dpf(2f)))

        // Bullet
        val by = sc.y - ss - bullet * h * 0.4f
        drawCircle(AlienPink, w * 0.012f, Offset(sc.x, by))
    }
}

// ── Ambient Decoration (Wallpaper) ──────────────────────────────────────────

@Composable
fun AmbientPoster(modifier: Modifier = Modifier) {
    val t = rememberInfiniteTransition(label = "amb")
    val ring by t.animateFloat(
        -15f, 15f,
        infiniteRepeatable(tween(3400, easing = LinearEasing), RepeatMode.Reverse),
        label = "ring",
    )
    val glow by t.animateFloat(
        0.18f, 0.34f,
        infiniteRepeatable(tween(2200, easing = LinearEasing), RepeatMode.Reverse),
        label = "glow",
    )
    val stars = remember { starfield(18, 7) }
    Canvas(modifier.fillMaxSize()) {
        space(stars)
        val cx = size.width / 2f
        val cy = size.height / 2f
        val pr = size.minDimension * 0.22f
        val planet = Color(0xFFE8B4C8)
        val ringC = Color(0xFF9B7FB8)
        val glowC = Color(0xFFC77DA3)
        val crater = Color(0xFFD4A5B9)

        drawCircle(
            Brush.radialGradient(
                listOf(glowC.copy(alpha = glow), glowC.copy(alpha = glow * 0.4f), Color.Transparent),
                center = Offset(cx, cy), radius = pr * 2.6f,
            ),
            pr * 2.6f, Offset(cx, cy),
        )
        rotate(ring, Offset(cx, cy)) {
            drawArc(
                ringC, 180f, 180f, false,
                Offset(cx - pr * 2.3f, cy - pr * 0.5f), Size(pr * 4.6f, pr),
                style = Stroke(dpf(5f), cap = StrokeCap.Round),
            )
        }
        drawCircle(
            Brush.radialGradient(
                listOf(planet, planet.copy(alpha = 0.9f), crater),
                center = Offset(cx - pr * 0.3f, cy - pr * 0.3f), radius = pr * 1.5f,
            ),
            pr, Offset(cx, cy),
        )
        rotate(ring, Offset(cx, cy)) {
            drawArc(
                ringC, 0f, 180f, false,
                Offset(cx - pr * 2.3f, cy - pr * 0.5f), Size(pr * 4.6f, pr),
                style = Stroke(dpf(5f), cap = StrokeCap.Round),
            )
        }
        drawCircle(crater, pr * 0.17f, Offset(cx + pr * 0.35f, cy - pr * 0.3f))
        drawCircle(crater.copy(alpha = 0.7f), pr * 0.12f, Offset(cx + pr * 0.45f, cy + pr * 0.15f))
    }
}

// ── Creature Interaction ────────────────────────────────────────────────────

@Composable
fun CreaturePoster(modifier: Modifier = Modifier) {
    val t = rememberInfiniteTransition(label = "crt")
    val bob by t.animateFloat(
        -1f, 1f,
        infiniteRepeatable(tween(1800, easing = LinearEasing), RepeatMode.Reverse),
        label = "bob",
    )
    val stars = remember { starfield(20, 17) }
    Canvas(modifier.fillMaxSize()) {
        space(stars)
        val w = size.width
        val h = size.height

        // Small planet
        val pc = Offset(w * 0.5f, h * 0.4f)
        val pr = w * 0.16f
        drawCircle(Color(0xFFC77DA3).copy(alpha = 0.25f), pr * 2.2f, pc)
        drawCircle(
            Brush.radialGradient(
                listOf(Color(0xFFE8B4C8), Color(0xFFD4A5B9)),
                center = Offset(pc.x - pr * 0.3f, pc.y - pr * 0.3f), radius = pr * 1.4f,
            ),
            pr, pc,
        )

        fun creature(cx: Float, cy: Float, col: Color, mirrored: Boolean) {
            val br = w * 0.10f
            val c = Offset(cx, cy + bob * h * 0.015f)
            // antennae
            drawLine(col, c, Offset(c.x - br * 0.4f, c.y - br * 1.5f), dpf(3f))
            drawLine(col, c, Offset(c.x + br * 0.4f, c.y - br * 1.5f), dpf(3f))
            drawCircle(col, br * 0.16f, Offset(c.x - br * 0.4f, c.y - br * 1.5f))
            drawCircle(col, br * 0.16f, Offset(c.x + br * 0.4f, c.y - br * 1.5f))
            // body
            drawCircle(col, br, c)
            drawCircle(Color.White, br * 0.22f, Offset(c.x - br * 0.3f, c.y - br * 0.1f))
            drawCircle(Color.White, br * 0.22f, Offset(c.x + br * 0.3f, c.y - br * 0.1f))
            drawCircle(Color(0xFF1A0050), br * 0.10f, Offset(c.x - br * 0.28f, c.y - br * 0.08f))
            drawCircle(Color(0xFF1A0050), br * 0.10f, Offset(c.x + br * 0.32f, c.y - br * 0.08f))
            if (!mirrored) {
                // speech dot
                val sb = Offset(c.x + br * 1.5f, c.y - br * 1.3f)
                drawRoundRect(
                    Color.White.copy(alpha = 0.85f),
                    Offset(sb.x - br * 0.5f, sb.y - br * 0.35f), Size(br, br * 0.7f),
                    CornerRadius(br * 0.2f, br * 0.2f),
                )
            }
        }
        creature(w * 0.30f, h * 0.74f, AlienPink, false)
        creature(w * 0.70f, h * 0.78f, Color(0xFF6B9FD4), true)
    }
}

private fun DrawScope.dpf(value: Float = 1f): Float = value * density

// ── Territorial Configuration (Block Blast) ─────────────────────────────────

@Composable
fun BlockBlastPoster(modifier: Modifier = Modifier) {
    val t = rememberInfiniteTransition(label = "bb")
    val bob by t.animateFloat(
        -0.4f, 0.4f,
        infiniteRepeatable(tween(1300, easing = LinearEasing), RepeatMode.Reverse),
        label = "bob",
    )
    val flash by t.animateFloat(
        0.25f, 1f,
        infiniteRepeatable(tween(700, easing = LinearEasing), RepeatMode.Reverse),
        label = "flash",
    )
    val stars = remember { starfield(16, 55) }
    Canvas(modifier.fillMaxSize()) {
        space(stars)
        val w = size.width
        val h = size.height
        val cols = 6
        val gridW = w * 0.74f
        val cellSz = gridW / cols
        val gridL = (w - gridW) / 2f
        val gridT = h * 0.32f

        // Grid panel
        drawRoundRect(
            Color.White.copy(alpha = 0.05f),
            Offset(gridL - 3f, gridT - 3f), Size(gridW + 6f, gridW + 6f),
            CornerRadius(10f, 10f),
        )
        // Grid lines
        for (i in 0..cols) {
            val x = gridL + i * cellSz; val y = gridT + i * cellSz
            drawLine(Color.White.copy(alpha = 0.09f), Offset(x, gridT), Offset(x, gridT + gridW))
            drawLine(Color.White.copy(alpha = 0.09f), Offset(gridL, y), Offset(gridL + gridW, y))
        }

        // Placed blocks (partial board)
        data class Cell(val r: Int, val c: Int, val color: Color)
        val placed = listOf(
            Cell(0, 0, Color(0xFFFF6B6B)), Cell(0, 1, Color(0xFFFF6B6B)),
            Cell(1, 0, Color(0xFF00E5FF)), Cell(1, 1, Color(0xFF00E5FF)), Cell(1, 2, Color(0xFF00E5FF)),
            Cell(2, 0, Color(0xFF9B7FB8)), Cell(2, 2, Color(0xFFFFD66B)), Cell(2, 3, Color(0xFFFFD66B)),
            Cell(3, 3, AlienPink),         Cell(3, 4, AlienPink),
            Cell(4, 1, Color(0xFF9FB6E0)), Cell(4, 2, Color(0xFF9FB6E0)), Cell(4, 3, Color(0xFF9FB6E0)),
            // Row 5: complete — shows flash
            Cell(5, 0, AlienPink), Cell(5, 1, AlienPink), Cell(5, 2, AlienPink),
            Cell(5, 3, AlienPink), Cell(5, 4, AlienPink), Cell(5, 5, AlienPink),
        )
        placed.forEach { (r, c, color) ->
            val bx = gridL + c * cellSz; val by = gridT + r * cellSz
            val inset = cellSz * 0.06f; val inner = cellSz - inset * 2f
            val corner = CornerRadius(cellSz * 0.22f, cellSz * 0.22f)
            if (r == 5) {
                drawRoundRect(AlienPink.copy(alpha = flash * 0.90f), Offset(bx + inset, by + inset), Size(inner, inner), corner)
                drawCircle(AlienPink.copy(alpha = flash * 0.30f), cellSz * 0.6f, Offset(bx + cellSz / 2f, by + cellSz / 2f))
            } else {
                drawRoundRect(
                    Brush.radialGradient(
                        listOf(Color.White.copy(0.28f), color, color.copy(0.70f)),
                        Offset(bx + inset + inner * 0.28f, by + inset + inner * 0.28f), inner,
                    ),
                    Offset(bx + inset, by + inset), Size(inner, inner), corner,
                )
            }
        }

        // Floating L-piece above the grid, bobbing gently
        val pTop = gridT - cellSz * 2.8f + bob * cellSz * 0.28f
        val pLeft = gridL + 3.5f * cellSz
        listOf(0 to 0, 1 to 0, 2 to 0, 2 to 1).forEach { (dr, dc) ->
            val bx = pLeft + dc * cellSz; val by = pTop + dr * cellSz
            val inset = cellSz * 0.06f; val inner = cellSz - inset * 2f
            drawRoundRect(
                Brush.radialGradient(
                    listOf(Color.White.copy(0.28f), AlienPink, AlienPink.copy(0.70f)),
                    Offset(bx + inset + inner * 0.28f, by + inset + inner * 0.28f), inner,
                ),
                Offset(bx + inset, by + inset), Size(inner, inner),
                CornerRadius(cellSz * 0.22f, cellSz * 0.22f),
            )
        }
        drawCircle(
            AlienPink.copy(alpha = 0.18f + bob * 0.04f),
            cellSz * 0.9f,
            Offset(pLeft + cellSz * 0.5f, pTop + cellSz * 1.5f),
        )
    }
}
