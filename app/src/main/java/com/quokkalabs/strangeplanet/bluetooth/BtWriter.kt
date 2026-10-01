package com.quokkalabs.strangeplanet.bluetooth

import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

/**
 * Sends Bluetooth messages, in order, from one background thread.
 *
 * Socket writes end in flush(), which blocks until the bytes are handed to the radio:
 * usually ~0.1 ms, but indefinitely when the link is congested. The game loops called
 * the send functions every tick on the main thread, so a slow link froze the game.
 * If the link stalls for long enough to back up [MAX_QUEUED] messages, the oldest are
 * dropped (every game re-sends full state regularly, so it catches up).
 */
internal class BtWriter(name: String) {
    private val executor = ThreadPoolExecutor(
        1, 1, 5L, TimeUnit.SECONDS,
        ArrayBlockingQueue(MAX_QUEUED),
        { r -> Thread(r, name).apply { isDaemon = true } },
        ThreadPoolExecutor.DiscardOldestPolicy(),
    ).apply { allowCoreThreadTimeOut(true) }

    fun send(block: () -> Unit) = executor.execute(block)

    private companion object {
        const val MAX_QUEUED = 240
    }
}
