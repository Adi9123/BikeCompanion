package dev.bikecompanion.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import dev.bikecompanion.data.AppState
import dev.bikecompanion.data.Prefs
import dev.bikecompanion.protocol.BikeModel
import dev.bikecompanion.protocol.Fields
import dev.bikecompanion.protocol.Support

/**
 * Pick the bike model. Grouped by how well each is supported, so nobody
 * picks a model expecting more than the app can do (honest data, story S1).
 */
@Composable
fun ModelPage(onDone: () -> Unit) {
    var current by remember { mutableStateOf(Fields.model) }
    Text(
        "Bikes put their data in different places. Pick yours and the app shows only what your model sends. " +
            "You can change this any time.",
        style = MaterialTheme.typography.bodyMedium,
        color = Tone.Muted,
    )
    for (group in Support.entries) {
        val models = BikeModel.entries.filter { it.support == group }
        SectionTitle(
            when (group) {
                Support.TESTED -> "Tested"
                Support.UNTESTED -> "Should work, not tested yet"
                Support.UNSUPPORTED -> "Not supported yet"
            },
        )
        if (group == Support.UNTESTED) {
            Text(
                "Built from the official app's data layout but not tried on a real bike. If something looks wrong, " +
                    "send a log from Settings > Diagnostics.",
                style = MaterialTheme.typography.bodySmall,
                color = Tone.Muted,
                modifier = Modifier.padding(start = 4.dp, bottom = 10.dp),
            )
        }
        Column {
            models.forEachIndexed { i, m ->
                if (i > 0) Spacer(Modifier.height(8.dp))
                ModelRow(m, m == current) {
                    if (m.support == Support.UNSUPPORTED) return@ModelRow
                    if (m != current) {
                        current = m
                        Prefs.bikeModel = m.name
                        // Values read with the old layout would be wrong now.
                        AppState.readings.value = emptyMap()
                    }
                    onDone()
                }
            }
        }
    }
    Spacer(Modifier.height(16.dp))
    Text(
        "Fuel tank size and gauge bars are set in Garage > Fuel. Check them for your model.",
        style = MaterialTheme.typography.bodySmall,
        color = Tone.Muted,
    )
}

@Composable
private fun ModelRow(m: BikeModel, selected: Boolean, onClick: () -> Unit) {
    val enabled = m.support != Support.UNSUPPORTED
    Row(
        Modifier
            .fillMaxWidth()
            .alpha(if (enabled) 1f else 0.5f)
            .then(if (enabled) Modifier.pressable(onClick = onClick) else Modifier)
            .clip(Shapes.control)
            .background(if (selected) Tone.Accent.copy(alpha = if (Tone.isDark) 0.16f else 0.35f) else Tone.Panel)
            .border(1.dp, if (selected) Tone.AccentText else Tone.Line, Shapes.control)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(m.title, style = MaterialTheme.typography.titleMedium, color = Tone.Text)
            Text("${m.kind} · ${m.note}", style = MaterialTheme.typography.bodySmall, color = Tone.Muted)
        }
        Spacer(Modifier.width(10.dp))
        if (selected) {
            Text("Selected", style = MaterialTheme.typography.labelMedium, color = Tone.AccentText)
        } else {
            SupportBadge(m.support)
        }
    }
}

/** Small capsule saying how far a model is supported. */
@Composable
fun SupportBadge(s: Support) {
    val color = when (s) {
        Support.TESTED -> Tone.AccentText
        Support.UNTESTED -> Tone.Amber
        Support.UNSUPPORTED -> Tone.Muted
    }
    Text(
        s.label,
        style = MaterialTheme.typography.labelSmall,
        color = color,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(color.copy(alpha = 0.14f))
            .padding(horizontal = 10.dp, vertical = 4.dp),
    )
}
