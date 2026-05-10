package com.quokkalabs.strangeplanet.wallpaper

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.service.wallpaper.WallpaperService
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.view.MotionEvent
import android.view.SurfaceHolder
import com.quokkalabs.strangeplanet.StrangePlanetApp
import com.quokkalabs.strangeplanet.data.WallpaperSettings
import com.quokkalabs.strangeplanet.data.wallpaperDataStore
import com.quokkalabs.strangeplanet.data.wallpaperSettings
import com.quokkalabs.strangeplanet.data.model.CreatureDefaults
import com.quokkalabs.strangeplanet.data.model.CreatureState
import com.quokkalabs.strangeplanet.data.model.CreatureType
import com.quokkalabs.strangeplanet.domain.PhysicsEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

private data class Star(val xFrac: Float, val yFrac: Float, val phase: Float)

class StrangePlanetWallpaperService : WallpaperService() {

    override fun onCreateEngine(): Engine = StrangePlanetEngine()

    inner class StrangePlanetEngine : Engine() {
        private val handler = Handler(Looper.getMainLooper())
        private val drawRunnable = Runnable { draw() }
        private val scope = CoroutineScope(Dispatchers.Main + Job())

        private var creatures = emptyList<CreatureState>()
        private var physicsEngine: PhysicsEngine? = null
        private var visible = false
        private var width = 0f
        private var height = 0f
        private var density = 1f

        private var settings = WallpaperSettings()

        // Audio & sayings
        private val app by lazy { application as StrangePlanetApp }
        private val soundManager by lazy { app.soundManager }
        private val ttsManager by lazy { app.ttsManager }
        private val sayingsRepo by lazy { app.sayingsRepository }
        private val dayContextResolver by lazy { app.dayContextResolver }

        // Speech bubble state: creature type -> (text, show-until timestamp)
        private val activeBubbles = mutableMapOf<CreatureType, Pair<String, Long>>()

        // Paints
        private val gradientPaint = Paint()
        private val bitmapCache = mutableMapOf<CreatureType, Bitmap>()
        private val bubblePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(217, 255, 255, 255) // 0.85 alpha
            style = Paint.Style.FILL
        }
        private val bubbleTextPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(255, 46, 42, 74) // DeepNavy
            textSize = 13f // will be scaled by density
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        private val bubblePointerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(217, 255, 255, 255)
            style = Paint.Style.FILL
        }

        // Planet paints
        private val planetBodyPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val planetGlowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val planetRingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(255, 155, 127, 184) // ringColor
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
        }
        private val planetRingMidPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(204, 199, 125, 163) // glowColor 0.8
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
        }
        private val planetRingInnerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(128, 155, 127, 184) // ringColor 0.5
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
        }
        private val craterPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(255, 212, 165, 185) // craterColor
        }

        // Star state
        private val stars = listOf(
            Star(0.08f, 0.12f, 0.0f), Star(0.88f, 0.08f, 0.1f),
            Star(0.45f, 0.18f, 0.2f), Star(0.58f, 0.32f, 0.3f),
            Star(0.05f, 0.25f, 0.4f), Star(0.78f, 0.15f, 0.5f),
            Star(0.30f, 0.05f, 0.6f), Star(0.92f, 0.40f, 0.7f),
            Star(0.15f, 0.55f, 0.8f), Star(0.70f, 0.48f, 0.9f),
        )
        private var starBitmap: Bitmap? = null
        private var animTime = 0f

        // Planet animation
        private var planetRingRotation = 0f
        private var planetGlowAlpha = 0.15f
        private var planetAnimPhase = 0f

        override fun onCreate(surfaceHolder: SurfaceHolder?) {
            super.onCreate(surfaceHolder)
            setTouchEventsEnabled(true)

            scope.launch {
                applicationContext.wallpaperDataStore.wallpaperSettings().collect { s ->
                    settings = s
                }
            }
        }

        override fun onSurfaceChanged(holder: SurfaceHolder?, format: Int, w: Int, h: Int) {
            super.onSurfaceChanged(holder, format, w, h)
            width = w.toFloat()
            height = h.toFloat()
            density = resources.displayMetrics.density

            bubbleTextPaint.textSize = 13f * density

            gradientPaint.shader = LinearGradient(
                0f, 0f, 0f, height,
                intArrayOf(0xFF5B8FC4.toInt(), 0xFF6B9FD4.toInt(), 0xFF8B8FC8.toInt(), 0xFF9B7FB8.toInt()),
                floatArrayOf(0f, 0.33f, 0.66f, 1f),
                Shader.TileMode.CLAMP,
            )

            planetRingPaint.strokeWidth = 6f * density
            planetRingMidPaint.strokeWidth = 4f * density
            planetRingInnerPaint.strokeWidth = 3f * density

            physicsEngine = PhysicsEngine(width, height)
            creatures = CreatureDefaults.create(width, height)

            loadBitmaps()
        }

        override fun onVisibilityChanged(isVisible: Boolean) {
            visible = isVisible
            if (isVisible) {
                handler.post(drawRunnable)
            } else {
                handler.removeCallbacks(drawRunnable)
            }
        }

        override fun onTouchEvent(event: MotionEvent?) {
            if (event?.action == MotionEvent.ACTION_DOWN) {
                val touchX = event.x
                val touchY = event.y

                // Check planet tap first
                val planetRadius = min(width, height) * 0.12f
                val pdx = touchX - width / 2f
                val pdy = touchY - height / 2f
                if (sqrt(pdx * pdx + pdy * pdy) < planetRadius * 2.5f) {
                    val engine = physicsEngine
                    if (engine != null && !engine.isOrbiting) {
                        engine.startOrbit(creatures)
                    }
                } else {
                    creatures.minByOrNull { c ->
                        val dx = c.x - touchX
                        val dy = c.y - touchY
                        sqrt(dx * dx + dy * dy)
                    }?.let { closest ->
                        val dx = closest.x - touchX
                        val dy = closest.y - touchY
                        if (sqrt(dx * dx + dy * dy) < closest.radius * density * settings.creatureSizeScale * 2) {
                            onCreatureTapped(closest.type)
                        }
                    }
                }
            }
            super.onTouchEvent(event)
        }

        private fun onCreatureTapped(type: CreatureType) {
            if (settings.soundEnabled.lwp) {
                soundManager.play(type)
            }

            if (settings.showSpeechBubbles.lwp) {
                val context = dayContextResolver.resolve()
                val saying = sayingsRepo.getSaying(type, context.timeOfDay, context.dayType)
                activeBubbles[type] = saying to (System.currentTimeMillis() + 4000)

                if (settings.ttsEnabled.lwp) {
                    ttsManager.speak(saying, type)
                }
            }
        }

        override fun onSurfaceDestroyed(holder: SurfaceHolder?) {
            visible = false
            handler.removeCallbacks(drawRunnable)
            bitmapCache.values.forEach { it.recycle() }
            bitmapCache.clear()
            starBitmap?.recycle()
            starBitmap = null
            super.onSurfaceDestroyed(holder)
        }

        override fun onDestroy() {
            scope.cancel()
            super.onDestroy()
        }

        private fun draw() {
            if (!visible) return

            val engine = physicsEngine ?: return
            creatures = engine.update(creatures)

            // Advance animation timers
            animTime += 0.033f
            planetAnimPhase += 0.033f
            planetRingRotation = sin(planetAnimPhase * 2f * PI.toFloat() / 6f) * 15f
            val orbitGlowBoost = if (physicsEngine?.isOrbiting == true) {
                val p = physicsEngine!!.orbitProgress
                if (p < 0.1f) p / 0.1f * 0.5f
                else if (p < 0.85f) 0.35f
                else (1f - p) / 0.15f * 0.35f
            } else 0f
            planetGlowAlpha = 0.15f + (sin(planetAnimPhase * 2f * PI.toFloat() / 4f) + 1f) / 2f * 0.15f + orbitGlowBoost

            // Expire old bubbles
            val now = System.currentTimeMillis()
            activeBubbles.entries.removeAll { it.value.second < now }

            val holder = surfaceHolder
            var canvas: Canvas? = null
            try {
                canvas = holder.lockCanvas()
                if (canvas != null) {
                    drawBackground(canvas)
                    if (settings.showStars.lwp) drawStars(canvas)
                    if (settings.showCreatures.lwp) drawCreatures(canvas, behindPlanet = true)
                    drawPlanet(canvas)
                    if (settings.showCreatures.lwp) drawCreatures(canvas, behindPlanet = false)
                }
            } finally {
                if (canvas != null) {
                    holder.unlockCanvasAndPost(canvas)
                }
            }

            handler.postDelayed(drawRunnable, 33) // ~30fps
        }

        private fun drawBackground(canvas: Canvas) {
            canvas.drawRect(0f, 0f, width, height, gradientPaint)
        }

        private fun drawStars(canvas: Canvas) {
            val bitmap = starBitmap ?: return
            val starPaint = Paint(Paint.ANTI_ALIAS_FLAG)

            stars.forEach { star ->
                val phase = (animTime / 10f + star.phase) % 1f
                val pulse = (sin(phase * 2f * PI.toFloat()) + 1f) / 2f * 0.7f + 0.3f

                val cx = width * star.xFrac
                val cy = height * star.yFrac
                val starSize = 24f * density * (0.8f + pulse * 0.4f)

                // Aura glow
                val auraSize = starSize * 1.8f
                val auraAlpha = (0.5f + pulse * 0.4f)
                val auraPaint = Paint(Paint.ANTI_ALIAS_FLAG)
                auraPaint.shader = RadialGradient(
                    cx, cy, auraSize / 2f,
                    intArrayOf(
                        Color.argb((230 * auraAlpha).toInt(), 255, 255, 255),
                        Color.argb((102 * auraAlpha).toInt(), 255, 255, 255),
                        Color.TRANSPARENT,
                    ),
                    floatArrayOf(0f, 0.5f, 1f),
                    Shader.TileMode.CLAMP,
                )
                canvas.drawCircle(cx, cy, auraSize / 2f, auraPaint)

                // Star bitmap
                starPaint.alpha = 242
                val half = starSize / 2f
                canvas.drawBitmap(
                    bitmap, null,
                    RectF(cx - half, cy - half, cx + half, cy + half),
                    starPaint,
                )
            }
        }

        private fun drawPlanet(canvas: Canvas) {
            val cx = width / 2f
            val cy = height / 2f
            val planetRadius = min(width, height) * 0.12f

            val ringOuterW = planetRadius * 2.3f
            val ringOuterH = planetRadius * 0.5f
            val ringMidW = planetRadius * 1.9f
            val ringMidH = planetRadius * 0.4f
            val ringInnerW = planetRadius * 1.5f
            val ringInnerH = planetRadius * 0.32f

            // Ambient glow
            planetGlowPaint.shader = RadialGradient(
                cx, cy, planetRadius * 2.5f,
                intArrayOf(
                    Color.argb((planetGlowAlpha * 255).toInt(), 199, 125, 163),
                    Color.argb((planetGlowAlpha * 0.4f * 255).toInt(), 199, 125, 163),
                    Color.TRANSPARENT,
                ),
                floatArrayOf(0f, 0.5f, 1f),
                Shader.TileMode.CLAMP,
            )
            canvas.drawCircle(cx, cy, planetRadius * 2.5f, planetGlowPaint)

            // Back ring arcs (behind planet)
            canvas.save()
            canvas.rotate(planetRingRotation, cx, cy)
            drawRingArc(canvas, cx, cy, ringOuterW, ringOuterH, 180f, 180f, planetRingPaint)
            drawRingArc(canvas, cx, cy, ringMidW, ringMidH, 180f, 180f, planetRingMidPaint)
            drawRingArc(canvas, cx, cy, ringInnerW, ringInnerH, 180f, 180f, planetRingInnerPaint)
            canvas.restore()

            // Planet body
            planetBodyPaint.shader = RadialGradient(
                cx - planetRadius * 0.3f, cy - planetRadius * 0.3f, planetRadius * 1.5f,
                intArrayOf(
                    Color.argb(255, 232, 180, 200), // planetColor
                    Color.argb(230, 232, 180, 200), // 0.9
                    Color.argb(255, 212, 165, 185), // craterColor
                ),
                floatArrayOf(0f, 0.5f, 1f),
                Shader.TileMode.CLAMP,
            )
            canvas.drawCircle(cx, cy, planetRadius, planetBodyPaint)

            // Front ring arcs (in front of planet)
            canvas.save()
            canvas.rotate(planetRingRotation, cx, cy)
            drawRingArc(canvas, cx, cy, ringOuterW, ringOuterH, 0f, 180f, planetRingPaint)
            drawRingArc(canvas, cx, cy, ringMidW, ringMidH, 0f, 180f, planetRingMidPaint)
            drawRingArc(canvas, cx, cy, ringInnerW, ringInnerH, 0f, 180f, planetRingInnerPaint)
            canvas.restore()

            // Craters
            craterPaint.alpha = 255
            canvas.drawCircle(cx + planetRadius * 0.35f, cy - planetRadius * 0.3f, planetRadius * 0.17f, craterPaint)
            craterPaint.alpha = 179
            canvas.drawCircle(cx + planetRadius * 0.45f, cy + planetRadius * 0.15f, planetRadius * 0.12f, craterPaint)
            craterPaint.alpha = 153
            canvas.drawCircle(cx - planetRadius * 0.25f, cy + planetRadius * 0.4f, planetRadius * 0.09f, craterPaint)
        }

        private fun drawRingArc(
            canvas: Canvas, cx: Float, cy: Float,
            halfW: Float, halfH: Float,
            startAngle: Float, sweepAngle: Float,
            paint: Paint,
        ) {
            canvas.drawArc(
                RectF(cx - halfW, cy - halfH, cx + halfW, cy + halfH),
                startAngle, sweepAngle, false, paint,
            )
        }

        private fun drawCreatures(canvas: Canvas, behindPlanet: Boolean) {
            val sizeScale = settings.creatureSizeScale
            val engine = physicsEngine

            for (creature in creatures) {
                val isBehind = engine?.isCreatureBehindPlanet(creature) == true
                if (isBehind != behindPlanet) continue

                val bitmap = bitmapCache[creature.type] ?: continue
                val drawSize = creature.size * density * sizeScale
                val halfSize = drawSize / 2f

                canvas.save()
                canvas.translate(creature.x, creature.y)
                canvas.rotate(creature.rotation)

                canvas.drawBitmap(
                    bitmap, null,
                    RectF(-halfSize, -halfSize, halfSize, halfSize),
                    null,
                )

                canvas.restore()

                // Speech bubble
                if (settings.showSpeechBubbles.lwp) {
                    val bubble = activeBubbles[creature.type]
                    if (bubble != null) {
                        drawSpeechBubble(canvas, creature.x, creature.y - halfSize - 12f * density, bubble.first)
                    }
                }
            }
        }

        private fun drawSpeechBubble(canvas: Canvas, cx: Float, bottomY: Float, text: String) {
            val maxWidth = (320 * density).toInt()
            val minWidth = (160 * density).toInt()
            val minHeight = 54f * density
            val padH = 14f * density
            val padV = 10f * density
            val cornerRadius = 16f * density
            val pointerHeight = 12f * density
            val pointerHalfWidth = 10f * density
            val margin = 8f * density

            val layout = StaticLayout.Builder
                .obtain(text, 0, text.length, bubbleTextPaint, maxWidth)
                .setAlignment(Layout.Alignment.ALIGN_CENTER)
                .setLineSpacing(4f * density, 1f)
                .setMaxLines(4)
                .build()

            val textWidth = (0 until layout.lineCount).maxOf { layout.getLineWidth(it) }
            val bubbleWidth = (textWidth + padH * 2).coerceIn(minWidth.toFloat(), maxWidth.toFloat())
            val bubbleHeight = (layout.height + padV * 2).coerceAtLeast(minHeight)

            var left = cx - bubbleWidth / 2f
            var top = bottomY - pointerHeight - bubbleHeight
            var right = cx + bubbleWidth / 2f
            var bottom = bottomY - pointerHeight

            // Clamp horizontally
            val shiftX = when {
                left < margin -> margin - left
                right > width - margin -> (width - margin) - right
                else -> 0f
            }

            // Clamp vertically — if bubble goes above screen, push down
            val shiftY = if (top < margin) margin - top else 0f

            canvas.save()
            canvas.translate(shiftX, shiftY)

            // Bubble body
            canvas.drawRoundRect(RectF(left, top, right, bottom), cornerRadius, cornerRadius, bubblePaint)

            // Pointer triangle — stays anchored at creature X regardless of bubble shift
            val pointerX = (cx - shiftX).coerceIn(left + 20f * density, right - 20f * density)
            val pointerPath = Path().apply {
                moveTo(pointerX - pointerHalfWidth, bottom)
                lineTo(pointerX, bottom + pointerHeight)
                lineTo(pointerX + pointerHalfWidth, bottom)
                close()
            }
            canvas.drawPath(pointerPath, bubblePointerPaint)

            // Text
            canvas.save()
            canvas.translate((left + right) / 2f, top + padV)
            layout.draw(canvas)
            canvas.restore()

            canvas.restore()
        }

        private fun loadBitmaps() {
            bitmapCache.values.forEach { it.recycle() }
            bitmapCache.clear()

            CreatureType.entries.forEach { type ->
                val bitmap = BitmapFactory.decodeResource(resources, type.drawableRes)
                if (bitmap != null) {
                    bitmapCache[type] = bitmap
                }
            }

            starBitmap?.recycle()
            starBitmap = BitmapFactory.decodeResource(resources, com.quokkalabs.strangeplanet.R.drawable.sp_star)
        }
    }
}
