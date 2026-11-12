package dev.rtrcompanion.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import dev.rtrcompanion.ride.RecPhase
import dev.rtrcompanion.ride.RideRecorder
import dev.rtrcompanion.ride.RideSample
import dev.rtrcompanion.ride.RideStore
import dev.rtrcompanion.ride.RideSummary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private fun dateLabel(t: Long): String = SimpleDateFormat("EEE d MMM · h:mm a", Locale.getDefault()).format(Date(t))

@Composable
fun RidesScreen(openId: String?, onOpen: (String?) -> Unit) {
    if (openId != null) RideReport(openId, onBack = { onOpen(null) }) else RideList(onOpen)
}

@Composable
private fun RideList(onOpen: (String) -> Unit) {
    val ctx = LocalContext.current
    val store = remember { RideStore(ctx.applicationContext) }
    val saved by RideRecorder.lastSaved.collectAsState()
    val rec by RideRecorder.state.collectAsState()
    val rides by produceState<List<RideSummary>?>(null, saved) {
        value = withContext(Dispatchers.IO) { store.list() }
    }
    val list = rides

    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text("Rides", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(top = 16.dp))
            if (list != null && list.isNotEmpty()) {
                val total = list.sumOf { it.distanceKm }
                Text(
                    "${list.size} rides · ${km(total)} km recorded",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Rtr.Muted,
                )
            }
        }
        if (rec.phase != RecPhase.IDLE) {
            item {
                Panel(Modifier.fillMaxWidth(), color = Rtr.RedDeep) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Dot(if (rec.phase == RecPhase.RECORDING) Rtr.Red else Rtr.Amber, 10)
                        Spacer(Modifier.width(10.dp))
                        Text(
                            if (rec.phase == RecPhase.RECORDING) "Recording now" else "Paused · ${rec.reason}",
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    Text("${km(rec.distanceKm)} km so far · top ${rec.maxSpeed} km/h", color = Rtr.Muted)
                }
            }
        }
        when {
            list == null -> item { Text("Loading…", color = Rtr.Muted) }
            list.isEmpty() -> item {
                Panel(Modifier.fillMaxWidth()) {
                    Text("No rides yet", fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Connect to the bike and ride. Rides are recorded automatically in the background and " +
                            "saved when you switch the key off. Allow location to see your route on the map.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Rtr.Muted,
                    )
                }
            }
            else -> items(list, key = { it.id }) { r -> RideCard(r) { onOpen(r.id) } }
        }
        item { Spacer(Modifier.height(16.dp)) }
    }
}

@Composable
private fun RideCard(r: RideSummary, onClick: () -> Unit) {
    Panel(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).clickable(onClick = onClick)) {
        Text(dateLabel(r.start), style = MaterialTheme.typography.labelLarge, color = Rtr.Muted)
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(km(r.distanceKm), fontSize = 34.sp, fontWeight = FontWeight.Bold)
            Text(" km", color = Rtr.Muted, modifier = Modifier.padding(bottom = 6.dp))
            Spacer(Modifier.weight(1f))
            if (r.hasRoute) Pill("Route", Rtr.Teal)
        }
        Spacer(Modifier.height(8.dp))
        Row {
            Mini("Time", duration(r.durationS), Modifier.weight(1f))
            Mini("Avg", "${r.avgSpeed.toInt()} km/h", Modifier.weight(1f))
            Mini("Top", "${r.maxSpeed} km/h", Modifier.weight(1f))
            Mini("Fuel", r.fuelL?.let { String.format(Locale.US, "%.2f l", it) } ?: "—", Modifier.weight(1f))
        }
    }
}

@Composable
private fun Mini(label: String, value: String, modifier: Modifier) {
    Column(modifier) {
        Text(label.uppercase(Locale.US), style = MaterialTheme.typography.labelSmall, color = Rtr.Muted)
        Text(value, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun RideReport(id: String, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val store = remember { RideStore(ctx.applicationContext) }
    val scope = rememberCoroutineScope()
    var confirmDelete by remember { mutableStateOf(false) }
    val data by produceState<Pair<RideSummary, List<RideSample>>?>(null, id) {
        value = withContext(Dispatchers.IO) { store.load(id) }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete this ride?") },
            text = { Text("The ride and its route are removed from the phone.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    scope.launch {
                        withContext(Dispatchers.IO) { store.delete(id) }
                        RideRecorder.lastSaved.value = null
                        onBack()
                    }
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }

    Column(Modifier.fillMaxSize()) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
            Text("Ride report", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            IconButton(onClick = {
                scope.launch {
                    val files = withContext(Dispatchers.IO) { store.exportFiles(id) }
                    if (files.isEmpty()) return@launch
                    val uris = ArrayList<Uri>(files.map { FileProvider.getUriForFile(ctx, ctx.packageName + ".files", it) })
                    val send = Intent(Intent.ACTION_SEND_MULTIPLE)
                        .setType("*/*")
                        .putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    ctx.startActivity(Intent.createChooser(send, "Share ride (CSV + GPX)"))
                }
            }) { Icon(Icons.Filled.Share, contentDescription = "Share") }
            IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Filled.Delete, contentDescription = "Delete") }
        }

        val d = data
        if (d == null) {
            Text("Loading…", color = Rtr.Muted, modifier = Modifier.padding(16.dp))
            return@Column
        }
        val (s, samples) = d
        val t0 = samples.firstOrNull()?.t ?: 0L
        fun series(f: (RideSample) -> Float?): List<ChartPoint> =
            samples.mapNotNull { smp -> f(smp)?.let { ChartPoint((smp.t - t0) / 1000f, it) } }

        LazyColumn(
            Modifier.fillMaxSize().padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text(dateLabel(s.start), color = Rtr.Muted)
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(km(s.distanceKm), fontSize = 52.sp, fontWeight = FontWeight.Bold)
                    Text(" km", color = Rtr.Muted, modifier = Modifier.padding(bottom = 10.dp))
                }
                Text("${duration(s.durationS)} total · ${duration(s.movingS)} moving", color = Rtr.Muted)
            }
            item {
                TileRow { m ->
                    StatTile("Avg speed", "${s.avgSpeed.toInt()}", "km/h", m, Rtr.Teal)
                    StatTile("Top speed", "${s.maxSpeed}", "km/h", m, Rtr.Red)
                }
                Spacer(Modifier.height(10.dp))
                TileRow { m ->
                    StatTile("Fuel used (est.)", s.fuelL?.let { String.format(Locale.US, "%.2f", it) } ?: "—", "l", m, Rtr.Green)
                    StatTile("Mileage", s.mileageKmpl?.toString() ?: "—", "km/l", m, Rtr.Green)
                }
                Spacer(Modifier.height(10.dp))
                TileRow { m ->
                    StatTile("Avg RPM", if (s.avgRpm > 0) "${s.avgRpm}" else "—", "", m)
                    StatTile("Max RPM", if (s.maxRpm > 0) "${s.maxRpm}" else "—", "", m)
                }
                Spacer(Modifier.height(10.dp))
                TileRow { m ->
                    StatTile("Max engine temp", s.maxEngineTemp?.toString() ?: "—", "°C", m, Rtr.Amber)
                    StatTile(
                        "Battery",
                        if (s.minBattery != null && s.maxBattery != null) {
                            String.format(Locale.US, "%.1f–%.1f", s.minBattery, s.maxBattery)
                        } else {
                            "—"
                        },
                        "V", m, Rtr.Blue,
                    )
                }
                Spacer(Modifier.height(10.dp))
                TileRow { m ->
                    StatTile("Idle time", duration(s.idleS), "", m)
                    StatTile("Moving", duration(s.movingS), "", m)
                }
            }
            item {
                if (s.hasRoute) {
                    Panel(Modifier.fillMaxWidth()) {
                        Text("Route", fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(10.dp))
                        Box(Modifier.fillMaxWidth().height(300.dp).clip(RoundedCornerShape(14.dp))) {
                            RouteMap(samples, Modifier.fillMaxSize())
                        }
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Legend("< 20", Rtr.Teal)
                            Legend("< 40", Rtr.Green)
                            Legend("< 60", Rtr.Amber)
                            Legend("60+ km/h", Rtr.Red)
                        }
                    }
                } else {
                    Panel(Modifier.fillMaxWidth()) {
                        Text("No route", fontWeight = FontWeight.SemiBold)
                        Text(
                            "GPS was off or location permission was not granted during this ride.",
                            style = MaterialTheme.typography.bodySmall,
                            color = Rtr.Muted,
                        )
                    }
                }
            }
            item { LineChart("Speed", "km/h", series { it.speed?.toFloat() }, Rtr.Teal) }
            item { LineChart("Engine speed", "rpm", series { it.rpm?.toFloat() }, Rtr.Red) }
            item { LineChart("Throttle", "%", series { it.throttle?.toFloat() }, Rtr.Violet) }
            item { LineChart("Engine temperature", "°C", series { it.engineTemp?.toFloat() }, Rtr.Amber, zeroBased = false) }
            item {
                LineChart(
                    "Battery", "V", series { it.battery }, Rtr.Blue, zeroBased = false,
                    formatY = { String.format(Locale.US, "%.1f", it) },
                )
            }
            item { GearBars(s.gearSeconds) }
            item {
                Text(
                    "Fuel is estimated from distance and the cluster's average mileage. " +
                        "Distance comes from the odometer, or GPS when the odometer was not available.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Rtr.Muted,
                )
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun Legend(text: String, color: androidx.compose.ui.graphics.Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Dot(color)
        Spacer(Modifier.width(4.dp))
        Text(text, style = MaterialTheme.typography.labelSmall, color = Rtr.Muted)
    }
}

/** Pops up when the key goes off mid-ride, and when a ride has been saved. */
@Composable
fun RideDialogs(onViewRide: (String) -> Unit) {
    val ctx = LocalContext.current
    val store = remember { RideStore(ctx.applicationContext) }
    val scope = rememberCoroutineScope()
    val rec by RideRecorder.state.collectAsState()
    val saved by RideRecorder.lastSaved.collectAsState()
    var dismissedPause by remember { mutableStateOf<Long?>(null) }
    // Start from the ride already saved, so only rides saved while the app is open are announced.
    var shownSaved by remember { mutableStateOf(RideRecorder.lastSaved.value?.id) }
    val now = rememberNow()

    if (rec.phase == RecPhase.PAUSED && rec.pausedAt != dismissedPause) {
        val left = (dev.rtrcompanion.data.Prefs.autoEndMinutes * 60L - (now - (rec.pausedAt ?: now)) / 1000).coerceAtLeast(0)
        AlertDialog(
            onDismissRequest = { dismissedPause = rec.pausedAt },
            title = { Text("Ride ended?") },
            text = {
                Text(
                    "${rec.reason}. ${km(rec.distanceKm)} km recorded.\n\n" +
                        "Saving automatically in ${left / 60}:${String.format(Locale.US, "%02d", left % 60)} " +
                        "unless the bike is switched back on.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch(Dispatchers.IO) { RideRecorder.finish(store, "user") }
                }) { Text("Save ride") }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = { RideRecorder.discard() }) { Text("Discard") }
                    TextButton(onClick = { dismissedPause = rec.pausedAt }) { Text("Keep riding") }
                }
            },
        )
    }

    val s = saved
    if (s != null && s.id != shownSaved) {
        AlertDialog(
            onDismissRequest = { shownSaved = s.id },
            title = { Text("Ride saved") },
            text = { Text("${km(s.distanceKm)} km in ${duration(s.durationS)} · avg ${s.avgSpeed.toInt()} km/h · top ${s.maxSpeed} km/h") },
            confirmButton = {
                TextButton(onClick = {
                    shownSaved = s.id
                    onViewRide(s.id)
                }) { Text("View report") }
            },
            dismissButton = { TextButton(onClick = { shownSaved = s.id }) { Text("Close") } },
        )
    }
}
