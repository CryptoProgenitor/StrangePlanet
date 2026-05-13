package com.quokkalabs.strangeplanet.debug

import android.util.Log
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

object PongDebugMetrics {

    val clientHitsSent = AtomicInteger(0)
    val clientHitsAdopted = AtomicInteger(0)
    val lastSnapPx = AtomicReference(0f)
    val snapCount = AtomicInteger(0)
    val totalSnapPx = AtomicReference(0f)
    val packetsReceived = AtomicInteger(0)
    val packetsPerSec = AtomicInteger(0)
    val lastRoundTripMs = AtomicLong(0L)
    val pendingHitSentAtMs = AtomicLong(0L)
    val lastPacketAgeMs = AtomicLong(0L)
    // 0=center 1=left75 2=right75 3=leftEdge 4=rightEdge 5=miss
    val botSweepPhase = AtomicInteger(0)

    fun tickSecond() {
        packetsPerSec.set(packetsReceived.getAndSet(0))
    }

    fun emitLogcat() {
        val sent = clientHitsSent.get()
        val adopted = clientHitsAdopted.get()
        val snap = lastSnapPx.get()
        val count = snapCount.get()
        val meanSnap = if (count > 0) totalSnapPx.get() / count else 0f
        val pkts = packetsPerSec.get()
        val rtt = lastRoundTripMs.get()
        val sweepPhase = botSweepPhase.get()
        val sweepLabel = when (sweepPhase) {
            0 -> "center"
            1 -> "left75"
            2 -> "right75"
            3 -> "leftEdge"
            4 -> "rightEdge"
            5 -> "nearMissL"
            6 -> "nearMissR"
            7 -> "MISS"
            else -> "$sweepPhase"
        }
        Log.i(
            "PongMetrics",
            "hits_sent=$sent hits_adopted=$adopted ghost_suspects=${maxOf(0, sent - adopted)} " +
                "last_snap_px=${"%.1f".format(snap)} mean_snap_px=${"%.1f".format(meanSnap)} " +
                "pkts_per_sec=$pkts rtt_ms=$rtt pkt_age_ms=${lastPacketAgeMs.get()} sweep=$sweepLabel",
        )
    }

    fun reset() {
        clientHitsSent.set(0)
        clientHitsAdopted.set(0)
        lastSnapPx.set(0f)
        snapCount.set(0)
        totalSnapPx.set(0f)
        packetsReceived.set(0)
        packetsPerSec.set(0)
        lastRoundTripMs.set(0L)
        pendingHitSentAtMs.set(0L)
        lastPacketAgeMs.set(0L)
        botSweepPhase.set(0)
    }
}
