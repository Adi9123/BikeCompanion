package dev.rtrcompanion.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.rtrcompanion.data.LinkPhase
import dev.rtrcompanion.data.LinkStatus
import kotlinx.coroutines.delay

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
        text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(top = 16.dp, bottom = 8.dp),
    )
}

@Composable
fun StatusBanner(s: LinkStatus) {
    val color = when (s.phase) {
        LinkPhase.LIVE -> Color(0xFF2E7D32)
        LinkPhase.IDLE -> MaterialTheme.colorScheme.surfaceVariant
        LinkPhase.FAILED -> MaterialTheme.colorScheme.errorContainer
        else -> Color(0xFFF9A825)
    }
    val onColor = when (s.phase) {
        LinkPhase.LIVE -> Color.White
        LinkPhase.IDLE -> MaterialTheme.colorScheme.onSurfaceVariant
        LinkPhase.FAILED -> MaterialTheme.colorScheme.onErrorContainer
        else -> Color.Black
    }
    Card(
        colors = CardDefaults.cardColors(containerColor = color, contentColor = onColor),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(12.dp)) {
            Row {
                Text(s.phase.label, fontWeight = FontWeight.Bold)
                s.deviceName?.let { Text("  ·  $it") }
            }
            if (s.detail.isNotBlank()) Text(s.detail, style = MaterialTheme.typography.bodySmall)
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
