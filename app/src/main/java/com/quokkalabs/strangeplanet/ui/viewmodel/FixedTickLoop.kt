package com.quokkalabs.strangeplanet.ui.viewmodel

import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.platform.AndroidUiDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

private const val TICK_NANOS = 1_000_000_000L / 60
private const val MAX_TICKS_PER_FRAME = 4
private const val SUSPENDED_TICK_NANOS = 100_000_000L

/**
 * Runs [tick] at a fixed 60 ticks per second, paced by the display's vsync.
 *
 * Game engines are tuned per 60 Hz tick, so the simulation rate stays fixed while
 * ticks are scheduled from real frame times: one tick per frame at 60 Hz, one every
 * other frame at 120 Hz, and catch-up ticks after a dropped frame. Unlike a
 * `delay(16)` loop this doesn't drift against the display refresh, which is what
 * causes micro-stutter on slower devices.
 */
fun CoroutineScope.launchFixedTickLoop(tick: suspend () -> Unit): Job =
    launch(AndroidUiDispatcher.Main) {
        var lastFrameNanos = withFrameNanos { it }
        var accumulated = 0L
        while (isActive) {
            val frameNanos = withFrameNanos { it }
            accumulated = (accumulated + frameNanos - lastFrameNanos)
                .coerceAtMost(TICK_NANOS * MAX_TICKS_PER_FRAME)
            lastFrameNanos = frameNanos
            while (accumulated >= TICK_NANOS) {
                accumulated -= TICK_NANOS
                val start = System.nanoTime()
                tick()
                if (System.nanoTime() - start > SUSPENDED_TICK_NANOS) {
                    // The tick deliberately paused (e.g. a delay between waves);
                    // restart timing rather than fast-forwarding to catch up.
                    accumulated = 0L
                    lastFrameNanos = withFrameNanos { it }
                    break
                }
            }
        }
    }
