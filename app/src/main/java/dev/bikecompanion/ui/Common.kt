package dev.bikecompanion.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.bikecompanion.data.LinkPhase
import dev.bikecompanion.data.LinkStatus
import dev.bikecompanion.ride.RecPhase
import dev.bikecompanion.ride.RecState
import kotlinx.coroutines.delay
import java.util.Locale

/** Current time, refreshed every second, for "seconds ago" labels and staleness. */
@Composable
fun rememberNow(): Long {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1_000)
            now = System.currentTimeMillis()
        }
    }
    return now
}

@Composable
fun SectionTitle(text: String) {
    Text(
        text.uppercase(Locale.US),
        style = MaterialTheme.typography.labelSmall,
        color = Rtr.Muted,
        modifier = Modifier.padding(top = 20.dp, bottom = 8.dp, start = 4.dp),
    )
}

/** The standard rounded panel used across the app. */
@Composable
fun Panel(
    modifier: Modifier = Modifier,
    color: Color = Rtr.Panel,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = color),
        border = BorderStroke(1.dp, Rtr.Line),
    ) {
        Column(Modifier.padding(16.dp), content = content)
    }
}

/** A labelled value: label on top, big number, small unit. */
@Composable
fun StatTile(
    label: String,
    value: String,
    unit: String = "",
    modifier: Modifier = Modifier,
    accent: Color = Rtr.Text,
    stale: Boolean = false,
) {
    Panel(modifier.alpha(if (stale) 0.5f else 1f)) {
        Text(label.uppercase(Locale.US), style = MaterialTheme.typography.labelSmall, color = Rtr.Muted, maxLines = 1)
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(value, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = accent, maxLines = 1)
            if (unit.isNotEmpty()) {
                Text(
                    " $unit",
                    style = MaterialTheme.typography.bodySmall,
                    color = Rtr.Muted,
                    modifier = Modifier.padding(bottom = 3.dp),
                )
            }
        }
    }
}

/** Two tiles side by side. */
@Composable
fun TileRow(content: @Composable (Modifier) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
        content(Modifier.weight(1f))
    }
}

@Composable
fun Dot(color: Color, size: Int = 8) {
    Box(Modifier.size(size.dp).clip(CircleShape).background(color))
}

/** Small rounded label with a coloured dot. */
@Composable
fun Pill(text: String, dot: Color, modifier: Modifier = Modifier) {
    Row(
        modifier
            .clip(RoundedCornerShape(50))
            .background(Rtr.Panel2)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Dot(dot)
        Spacer(Modifier.width(8.dp))
        Text(text, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

fun linkColor(phase: LinkPhase): Color = when (phase) {
    LinkPhase.LIVE -> Rtr.Green
    LinkPhase.IDLE -> Rtr.Muted
    LinkPhase.FAILED -> Rtr.Red
    else -> Rtr.Amber
}

@Composable
fun StatusPill(s: LinkStatus, modifier: Modifier = Modifier) {
    val text = if (s.phase == LinkPhase.LIVE) (s.deviceName ?: "Connected") else s.phase.label
    Pill(text, linkColor(s.phase), modifier)
}

@Composable
fun RecordingPill(r: RecState, modifier: Modifier = Modifier) {
    when (r.phase) {
        RecPhase.RECORDING -> Pill("REC  ${km(r.distanceKm)} km", Rtr.Red, modifier)
        RecPhase.PAUSED -> Pill("Paused", Rtr.Amber, modifier)
        RecPhase.IDLE -> Unit
    }
}

/** Full-width connection banner with the detail line, used on the Bike tab. */
@Composable
fun StatusBanner(s: LinkStatus) {
    Panel(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Dot(linkColor(s.phase), 10)
            Spacer(Modifier.width(10.dp))
            Text(s.phase.label, fontWeight = FontWeight.Bold)
            s.deviceName?.let { Text("  ·  $it", color = Rtr.Muted) }
        }
        if (s.detail.isNotBlank()) {
            Text(s.detail, style = MaterialTheme.typography.bodySmall, color = Rtr.Muted, modifier = Modifier.padding(top = 4.dp))
        }
    }
}

fun ago(now: Long, at: Long?): String {
    if (at == null) return "never"
    val s = ((now - at) / 1000).coerceAtLeast(0)
    return when {
        s < 2 -> "just now"
        s < 120 -> "${s}s ago"
        else -> "${s / 60}m ago"
    }
}

fun km(v: Double): String = String.format(Locale.US, "%.1f", v)

fun duration(seconds: Long): String {
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60
    return if (h > 0) String.format(Locale.US, "%dh %02dm", h, m) else String.format(Locale.US, "%dm %02ds", m, s)
}
