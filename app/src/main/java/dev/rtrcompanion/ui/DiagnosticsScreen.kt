package dev.rtrcompanion.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.rtrcompanion.data.AppState
import dev.rtrcompanion.data.DecoderStatus
import dev.rtrcompanion.data.FrameStat
import dev.rtrcompanion.data.LinkStatus
import dev.rtrcompanion.protocol.ApacheFields
import dev.rtrcompanion.protocol.Codec
import dev.rtrcompanion.protocol.Scramble
import java.util.Locale

@Composable
fun DiagnosticsScreen() {
    var sub by rememberSaveable { mutableIntStateOf(0) }
    Column(Modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = sub, containerColor = Rtr.Bg, contentColor = Rtr.Text) {
            Tab(selected = sub == 0, onClick = { sub = 0 }, text = { Text("Live data") })
            Tab(selected = sub == 1, onClick = { sub = 1 }, text = { Text("Log") })
        }
        if (sub == 0) FramesPane() else LogScreen()
    }
}

@Composable
private fun FramesPane() {
    val link by AppState.link.collectAsState()
    val dec by AppState.decoder.collectAsState()
    val frames by AppState.frames.collectAsState()
    val now = rememberNow()

    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { Box(Modifier.padding(top = 16.dp)) { ConnectionCard(link, frames.values, now) } }
        item { DecoderCard(dec) }
        if (frames.isEmpty()) {
            item {
                Text(
                    "No frames received yet. Connect on the Bike tab with the ignition on. " +
                        "If it connects but nothing arrives here, the problem is the link, not decoding.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
        items(frames.values.sortedBy { it.id }, key = { it.id }) { FrameCard(it, now) }
        item { Box(Modifier.padding(bottom = 16.dp)) }
    }
}

@Composable
private fun KeyValue(k: String, v: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 1.dp)) {
        Text(k, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
        Text(v, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun ConnectionCard(s: LinkStatus, frames: Collection<FrameStat>, now: Long) {
    val total = frames.sumOf { it.count }
    val last = frames.maxOfOrNull { it.lastAt }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Text("Connection", fontWeight = FontWeight.Bold)
            KeyValue("State", s.phase.label)
            KeyValue("Detail", s.detail.ifBlank { "—" })
            KeyValue("Device", listOfNotNull(s.deviceName, s.address).joinToString("  ").ifBlank { "—" })
            KeyValue("Signal (RSSI)", s.rssi?.let { "$it dBm" } ?: "—")
            KeyValue("Connected for", s.connectedAt?.let { "${(now - it) / 1000} s" } ?: "—")
            KeyValue("Connect attempts", s.attempts.toString())
            KeyValue("Drops / failures", s.drops.toString())
            KeyValue("Last drop", s.lastDrop ?: "—")
            KeyValue("Frames received", total.toString())
            KeyValue("Last frame", ago(now, last))
        }
    }
}

@Composable
private fun DecoderCard(d: DecoderStatus) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Text("Decoding", fontWeight = FontWeight.Bold)
            KeyValue("Bike's scramble", d.locked?.label ?: "not detected yet")
            Scramble.entries.forEach { s ->
                KeyValue("  checksum valid as ${s.name}", "${d.passes[s] ?: 0} / ${d.total}")
            }
            KeyValue("  valid under none", "${d.noScheme} / ${d.total}")
            KeyValue("Empty wake-up frames", d.idleFrames.toString())
            KeyValue("Sent to bike", "${d.txCount} frames (${d.txRejected} rejected)")
            KeyValue("Sending with", d.outbound?.name ?: "—")
            KeyValue("Last sent", d.lastTx ?: "—")
        }
    }
}

@Composable
private fun FrameCard(f: FrameStat, now: Long) {
    val seconds = (f.lastAt - f.firstAt) / 1000.0
    val rate = if (seconds >= 1.0) String.format(Locale.US, "%.1f/s", (f.count - 1) / seconds) else "—"
    val okPct = if (f.count > 0) f.checksumOk * 100 / f.count else 0
    val name = ApacheFields.frameNames[f.id] ?: "Unknown"
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Text(String.format(Locale.US, "5A %02X  ·  %s", f.id, name), fontWeight = FontWeight.Bold)
            Text(
                "${f.count} frames · $rate · checksum ok $okPct% · ${f.scramble?.name ?: "?"} · ${ago(now, f.lastAt)}",
                style = MaterialTheme.typography.bodySmall,
            )
            ByteGrid(f)
            ApacheFields.forFrame(f.id).forEach { spec ->
                val raw = Codec.be(f.lastClear, spec.index, spec.length)
                val value = raw?.let { ApacheFields.format(spec, it) } ?: "—"
                KeyValue("${spec.label}  [${spec.position}]", if (spec.unit.isEmpty()) value else "$value ${spec.unit}")
            }
        }
    }
}

/**
 * The decoded bytes of the latest frame, 1-based like the protocol doc.
 * Red: changed since the previous frame. Amber: has changed at some point.
 * Bytes that never change are probably unused on this bike.
 */
@Composable
private fun ByteGrid(f: FrameStat) {
    val bytes = f.lastClear
    Column(Modifier.padding(vertical = 8.dp)) {
        bytes.indices.chunked(10).forEach { row ->
            Row {
                row.forEach { i ->
                    val bg = when {
                        f.changed.getOrElse(i) { false } -> Color(0xFFE53935)
                        f.everChanged.getOrElse(i) { false } -> Color(0x66FFB300)
                        else -> Color.Transparent
                    }
                    Column(
                        Modifier.width(32.dp).background(bg),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text("${i + 1}", fontSize = 9.sp, style = MaterialTheme.typography.labelSmall)
                        Text(
                            "%02X".format(Codec.u8(bytes[i])),
                            fontFamily = FontFamily.Monospace,
                            fontSize = 13.sp,
                        )
                    }
                }
            }
        }
        Text(
            "wire: " + Codec.hex(f.lastWire),
            fontFamily = FontFamily.Monospace,
            fontSize = 10.sp,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}
