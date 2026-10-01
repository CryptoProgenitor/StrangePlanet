package com.quokkalabs.strangeplanet.ui.viewmodel

import androidx.compose.runtime.withFrameNanos

/**
 * Paces a fixed 60 Hz game tick from the display's vsync. Drop-in replacement for
 * `delay(16)` in a game loop:
 *
 * ```
 * loopJob = viewModelScope.launch(AndroidUiDispatcher.Main) {  // provides the frame clock
 *     val ticker = FrameTicker()
 *     while (isActive) {
 *         ticker.awaitTick()
 *         step()
 *     }
 * }
 * ```
 *
 * Engines are tuned per 60 Hz tick, so the simulation rate stays fixed while ticks are
 * scheduled from real frame times: one tick per frame at 60 Hz, one every other frame at
 * 120 Hz, and up to [MAX_CATCH_UP] extra ticks after a dropped frame. A `delay(16)` loop
 * instead drifts against the refresh (16 ms is not 16.67 ms, nor a multiple of 8.33 ms)
 * and silently loses game time whenever the main thread is busy, which shows up as
 * micro-stutter on slower devices.
 *
 * If the loop body suspends for a while (a delay between levels, the app in the
 * background), timing restarts rather than fast-forwarding to catch up.
 */
class FrameTicker {
    private var simNanos = UNSET
    private var frameNanos = 0L

    suspend fun awaitTick() {
        while (true) {
            if (simNanos != UNSET && simNanos + TICK_NANOS <= frameNanos + TOLERANCE_NANOS) {
                simNanos += TICK_NANOS
                return
            }
            frameNanos = withFrameNanos { it }
            if (simNanos == UNSET || frameNanos - simNanos > TICK_NANOS * (MAX_CATCH_UP + 1)) {
                simNanos = frameNanos
                return
            }
        }
    }

    private companion object {
        const val UNSET = Long.MIN_VALUE
        const val TICK_NANOS = 1_000_000_000L / 60

        /** Absorbs vsync timestamp jitter so 120 Hz doesn't alternate 2- and 3-frame ticks. */
        const val TOLERANCE_NANOS = 2_000_000L

        /** Most ticks run in one frame to recover from a dropped frame. */
        const val MAX_CATCH_UP = 3
    }
}
