package dev.rtrcompanion.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.rtrcompanion.data.AppState
import dev.rtrcompanion.data.Reading
import dev.rtrcompanion.protocol.ApacheFields

/** Values shown on the ride screen, in order. Everything else is on Diagnostics. */
private val rideKeys = listOf(
    "odometer", "engineTemp", "battery", "fuel",
    "tripDistance", "range", "throttle", "topSpeed",
    "avgSpeed", "mileage", "mil", "absMil",
)

private const val STALE_MS = 5_000L

private fun valueOf(r: Map<String, Reading>, key: String): String {
    val reading = r[key] ?: return "—"
    val spec = ApacheFields.spec(key) ?: return reading.raw.toString()
    return ApacheFields.format(spec, reading.raw)
}

@Composable
fun RideScreen() {
    val link by AppState.link.collectAsState()
    val readings by AppState.readings.collectAsState()
    val now = rememberNow()

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        StatusBanner(link)
        Spacer(Modifier.height(12.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            BigTile("Speed", valueOf(readings, "speed"), "km/h", readings["speed"], now, Modifier.weight(1f))
            BigTile("RPM", valueOf(readings, "rpm"), "rpm", readings["rpm"], now, Modifier.weight(1f))
            BigTile("Gear", valueOf(readings, "gear"), "", readings["gear"], now, Modifier.weight(0.7f))
        }
        Spacer(Modifier.height(12.dp))

        rideKeys.chunked(2).forEach { pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                pair.forEach { key ->
                    val spec = ApacheFields.spec(key)
                    SmallTile(
                        label = spec?.label ?: key,
                        value = valueOf(readings, key),
                        unit = spec?.unit ?: "",
                        reading = readings[key],
                        now = now,
                        modifier = Modifier.weight(1f),
                    )
                }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
            Spacer(Modifier.height(12.dp))
        }

        Text(
            "— means the bike has not sent this value. Faded values are older than 5 seconds. " +
                "Fields your cluster never sends will stay at — or 0; see Diagnostics for the raw bytes.",
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun BigTile(label: String, value: String, unit: String, reading: Reading?, now: Long, modifier: Modifier) {
    val stale = reading == null || now - reading.at > STALE_MS
    Card(modifier.alpha(if (stale) 0.55f else 1f)) {
        Column(Modifier.padding(12.dp).fillMaxWidth()) {
            Text(label, style = MaterialTheme.typography.labelMedium)
            Text(value, fontSize = 34.sp, fontWeight = FontWeight.Bold)
            if (unit.isNotEmpty()) Text(unit, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun SmallTile(label: String, value: String, unit: String, reading: Reading?, now: Long, modifier: Modifier) {
    val stale = reading == null || now - reading.at > STALE_MS
    Card(modifier.alpha(if (stale) 0.55f else 1f)) {
        Column(Modifier.padding(12.dp).fillMaxWidth()) {
            Text(label, style = MaterialTheme.typography.labelSmall, maxLines = 1)
            Row {
                Text(value, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
                if (unit.isNotEmpty()) Text(" $unit", style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 10.dp))
            }
        }
    }
}
