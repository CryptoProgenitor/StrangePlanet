package com.quokkalabs.strangeplanet.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.quokkalabs.strangeplanet.debug.PongDebugMetrics
import kotlinx.coroutines.delay

private data class MetricSnapshot(
    val hitsSent: Int = 0,
    val hitsAdopted: Int = 0,
    val lastSnapPx: Float = 0f,
    val meanSnapPx: Float = 0f,
    val packetsPerSec: Int = 0,
    val rttMs: Long = 0L,
    val sweepPhase: Int = 0,
)

private val SWEEP_LABELS = arrayOf("ctr", "L75", "R75", "Ledge", "Redge", "MISS")

@Composable
fun DebugHud(modifier: Modifier = Modifier) {
    var snap by remember { mutableStateOf(MetricSnapshot()) }

    LaunchedEffect(Unit) {
        while (true) {
            delay(500)
            val count = PongDebugMetrics.snapCount.get()
            snap = MetricSnapshot(
                hitsSent = PongDebugMetrics.clientHitsSent.get(),
                hitsAdopted = PongDebugMetrics.clientHitsAdopted.get(),
                lastSnapPx = PongDebugMetrics.lastSnapPx.get(),
                meanSnapPx = if (count > 0) PongDebugMetrics.totalSnapPx.get() / count else 0f,
                packetsPerSec = PongDebugMetrics.packetsPerSec.get(),
                rttMs = PongDebugMetrics.lastRoundTripMs.get(),
                sweepPhase = PongDebugMetrics.botSweepPhase.get(),
            )
        }
    }

    val ghostSuspects = maxOf(0, snap.hitsSent - snap.hitsAdopted)
    val hitsOk = snap.hitsSent == 0 || ghostSuspects == 0
    val snapOk = snap.lastSnapPx < 15f

    Column(
        modifier = modifier
            .background(Color.Black.copy(alpha = 0.65f), RoundedCornerShape(6.dp))
            .padding(horizontal = 7.dp, vertical = 5.dp),
    ) {
        Text(
            text = "hits: ${snap.hitsAdopted}/${snap.hitsSent}  ghosts: $ghostSuspects",
            color = if (hitsOk) Color(0xFF66BB6A) else Color(0xFFEF5350),
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium,
            fontFamily = FontFamily.Monospace,
        )
        Text(
            text = "snap: ${"%.1f".format(snap.lastSnapPx)}px avg ${"%.1f".format(snap.meanSnapPx)}",
            color = if (snapOk) Color(0xFF66BB6A) else Color(0xFFFFA726),
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium,
            fontFamily = FontFamily.Monospace,
        )
        val sweepLabel = SWEEP_LABELS.getOrElse(snap.sweepPhase) { "${snap.sweepPhase}" }
        val isMissPhase = snap.sweepPhase == 5
        Text(
            text = "pkts: ${snap.packetsPerSec}/s  rtt: ${snap.rttMs}ms",
            color = Color.White.copy(alpha = 0.75f),
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium,
            fontFamily = FontFamily.Monospace,
        )
        Text(
            text = "sweep: $sweepLabel",
            color = if (isMissPhase) Color(0xFFFFA726) else Color(0xFF42A5F5),
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium,
            fontFamily = FontFamily.Monospace,
        )
    }
}
