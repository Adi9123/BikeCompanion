package dev.bikecompanion.ui

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
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
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
import dev.bikecompanion.fuel.FuelMath
import dev.bikecompanion.garage.RideModes
import androidx.compose.foundation.background
import dev.bikecompanion.ride.RecPhase
import dev.bikecompanion.ride.RideRecorder
import dev.bikecompanion.ride.RideSample
import dev.bikecompanion.ride.RideStore
import dev.bikecompanion.ride.RideSummary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private fun dateLabel(t: Long): String = SimpleDateFormat("EEE d MMM · h:mm a", Locale.getDefault()).format(Date(t))

@Composable
fun RidesScreen(openId: String?, onOpen: (String?) -> Unit) {
    if (openId != null) {
        RideReport(openId, onBack = { onOpen(null) })
        return
    }
    RideList(onOpen)
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
        Modifier.fillMaxSize().padding(horizontal = Space.gutter),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text("Rides", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(top = 16.dp))
            if (list != null && list.isNotEmpty()) {
                val total = list.sumOf { it.distanceKm }
                Text(
                    "${list.size} rides · ${km(total)} km recorded",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Tone.Muted,
                )
            }
        }
        if (rec.phase != RecPhase.IDLE) {
            item {
                Panel(Modifier.fillMaxWidth(), color = Tone.RedDeep) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Dot(if (rec.phase == RecPhase.RECORDING) Tone.Red else Tone.Amber, 10)
                        Spacer(Modifier.width(10.dp))
                        Text(
                            if (rec.phase == RecPhase.RECORDING) "Recording now" else "Paused · ${rec.reason}",
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    Text("${km(rec.distanceKm)} km so far · top ${rec.maxSpeed} km/h", color = Tone.Muted)
                }
            }
        }
        when {
            list == null -> item { Text("Loading…", color = Tone.Muted) }
            list.isEmpty() -> item {
                Panel(Modifier.fillMaxWidth()) {
                    Text("No rides yet", fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Connect to the bike and ride. Rides are recorded automatically in the background and " +
                            "saved when you switch the key off. Allow location to see your route on the map.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Tone.Muted,
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
        Text(dateLabel(r.start), style = MaterialTheme.typography.labelLarge, color = Tone.Muted)
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(km(r.distanceKm), fontSize = 34.sp, fontWeight = FontWeight.Bold)
            Text(" km", color = Tone.Muted, modifier = Modifier.padding(bottom = 6.dp))
            Spacer(Modifier.weight(1f))
            if (r.hasRoute) Pill("Route", Tone.Teal)
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
        Text(label.uppercase(Locale.US), style = MaterialTheme.typography.labelSmall, color = Tone.Muted)
        Text(value, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun RideReport(id: String, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val store = remember { RideStore(ctx.applicationContext) }
    val scope = rememberCoroutineScope()
    var confirmDelete by remember { mutableStateOf(false) }
    val refuels = rememberRefuels()
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
            Text("Loading…", color = Tone.Muted, modifier = Modifier.padding(16.dp))
            return@Column
        }
        val (s, samples) = d
        val t0 = samples.firstOrNull()?.t ?: 0L
        fun series(f: (RideSample) -> Float?): List<ChartPoint> =
            samples.mapNotNull { smp -> f(smp)?.let { ChartPoint((smp.t - t0) / 1000f, it) } }

        LazyColumn(
            Modifier.fillMaxSize().padding(horizontal = Space.gutter),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text(dateLabel(s.start), color = Tone.Muted)
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(km(s.distanceKm), fontSize = 52.sp, fontWeight = FontWeight.Bold)
                    Text(" km", color = Tone.Muted, modifier = Modifier.padding(bottom = 10.dp))
                }
                Text("${duration(s.durationS)} total · ${duration(s.movingS)} moving", color = Tone.Muted)
            }
            item {
                TileRow { m ->
                    StatTile("Avg speed", "${s.avgSpeed.toInt()}", "km/h", m, Tone.Teal)
                    StatTile("Top speed", "${s.maxSpeed}", "km/h", m, Tone.Red)
                }
                Spacer(Modifier.height(10.dp))
                val lpc = FuelMath.calibration(refuels)
                val measured = if (lpc != null && s.injCounts > 0) s.injCounts * lpc else null
                val fuel = measured ?: s.fuelL
                val perLitre = FuelMath.pricePerLitre(refuels)
                TileRow { m ->
                    StatTile(
                        if (measured != null) "Fuel used" else "Fuel used (est.)",
                        fuel?.let { String.format(Locale.US, "%.2f", it) } ?: "—", "l", m, Tone.Green,
                    )
                    StatTile(
                        "Mileage",
                        if (measured != null && measured > 0.0) String.format(Locale.US, "%.1f", s.distanceKm / measured) else (s.mileageKmpl?.toString() ?: "—"),
                        "km/l", m, Tone.Green,
                    )
                }
                Spacer(Modifier.height(10.dp))
                TileRow { m ->
                    StatTile("Fuel cost", if (fuel != null && perLitre != null) rupees(fuel * perLitre) else "—", "", m, Tone.Amber)
                    StatTile(
                        "Cost per km",
                        if (fuel != null && perLitre != null && s.distanceKm > 0) "₹" + String.format(Locale.US, "%.2f", fuel * perLitre / s.distanceKm) else "—",
                        "", m,
                    )
                }
                Spacer(Modifier.height(10.dp))
                TileRow { m ->
                    StatTile("Avg RPM", if (s.avgRpm > 0) "${s.avgRpm}" else "—", "", m)
                    StatTile("Max RPM", if (s.maxRpm > 0) "${s.maxRpm}" else "—", "", m)
                }
                Spacer(Modifier.height(10.dp))
                TileRow { m ->
                    StatTile("Max engine temp", s.maxEngineTemp?.toString() ?: "—", "°C", m, Tone.Amber)
                    StatTile(
                        "Battery",
                        if (s.minBattery != null && s.maxBattery != null) {
                            String.format(Locale.US, "%.1f–%.1f", s.minBattery, s.maxBattery)
                        } else {
                            "—"
                        },
                        "V", m, Tone.Blue,
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
                            Legend("< 20", Tone.Teal)
                            Legend("< 40", Tone.Green)
                            Legend("< 60", Tone.Amber)
                            Legend("60+ km/h", Tone.Red)
                        }
                    }
                } else {
                    Panel(Modifier.fillMaxWidth()) {
                        Text("No route", fontWeight = FontWeight.SemiBold)
                        Text(
                            "GPS was off or location permission was not granted during this ride.",
                            style = MaterialTheme.typography.bodySmall,
                            color = Tone.Muted,
                        )
                    }
                }
            }
            item { RidingStyle(s) }
            item { LineChart("Speed", "km/h", series { it.speed?.toFloat() }, Tone.Teal) }
            item { LineChart("Engine speed", "rpm", series { it.rpm?.toFloat() }, Tone.Red) }
            item { LineChart("Throttle", "%", series { it.throttle?.toFloat() }, Tone.Violet) }
            item { LineChart("Engine temperature", "°C", series { it.engineTemp?.toFloat() }, Tone.Amber, zeroBased = false) }
            item {
                LineChart(
                    "Battery", "V", series { it.battery }, Tone.Blue, zeroBased = false,
                    formatY = { String.format(Locale.US, "%.1f", it) },
                )
            }
            item { GearBars(s.gearSeconds) }
            if (s.modeSeconds.isNotEmpty()) item { ModeBars(s.modeSeconds) }
            item {
                Text(
                    "Fuel is measured from the bike's injection counter once it is calibrated (see Rides › Fuel); " +
                        "until then it is estimated from distance and the cluster's average mileage. " +
                        "Distance comes from the odometer, or GPS when the odometer was not available.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Tone.Muted,
                )
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun ModeBars(modeSeconds: Map<Int, Long>) {
    val names = RideModes.decodeNames(dev.bikecompanion.data.Prefs.modeNames)
    val total = modeSeconds.values.sum().coerceAtLeast(1L)
    Panel(Modifier.fillMaxWidth()) {
        Text("Time in each ride mode", fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(10.dp))
        modeSeconds.entries.sortedByDescending { it.value }.forEach { (mode, secs) ->
            val name = RideModes.name(mode, names)
            val color = when (name) {
                "Sport" -> Tone.Red
                "Rain" -> Tone.Blue
                else -> Tone.Teal
            }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
                Text(name, modifier = Modifier.width(64.dp), fontWeight = FontWeight.SemiBold)
                Box(Modifier.weight(1f).height(12.dp).clip(RoundedCornerShape(6.dp)).background(Tone.Panel2)) {
                    Box(
                        Modifier.fillMaxWidth(secs.toFloat() / total).height(12.dp)
                            .clip(RoundedCornerShape(6.dp)).background(color),
                    )
                }
                Text(duration(secs), style = MaterialTheme.typography.bodySmall, color = Tone.Muted, modifier = Modifier.padding(start = 8.dp))
            }
        }
    }
}

/** How the ride was ridden: smoothness score and the events behind it. */
@Composable
private fun RidingStyle(s: RideSummary) {
    val color = when {
        s.smoothness >= 80 -> Tone.Green
        s.smoothness >= 60 -> Tone.Amber
        else -> Tone.Red
    }
    Panel(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Riding style", fontWeight = FontWeight.SemiBold)
                Text(
                    when {
                        s.smoothness >= 80 -> "Smooth and easy on the bike"
                        s.smoothness >= 60 -> "Mostly smooth"
                        else -> "Aggressive: harsh throttle, hard braking or high revs"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = Tone.Muted,
                )
            }
            Text("${s.smoothness}", fontSize = 36.sp, fontWeight = FontWeight.Bold, color = color)
            Text("/100", color = Tone.Muted, modifier = Modifier.padding(start = 2.dp))
        }
        Spacer(Modifier.height(10.dp))
        Row {
            Mini("Harsh accel", "${s.harshAccel}", Modifier.weight(1f))
            Mini("Hard brake", "${s.hardBrake}", Modifier.weight(1f))
            Mini("Over 8k rpm", duration(s.overRevS), Modifier.weight(1f))
        }
        Spacer(Modifier.height(8.dp))
        Row {
            Mini("Avg throttle", "${s.avgThrottle}%", Modifier.weight(1f))
            Mini("Warm-up", s.warmupS?.let { duration(it) } ?: "—", Modifier.weight(1f))
            Spacer(Modifier.weight(1f))
        }
    }
}

@Composable
private fun Legend(text: String, color: androidx.compose.ui.graphics.Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Dot(color)
        Spacer(Modifier.width(4.dp))
        Text(text, style = MaterialTheme.typography.labelSmall, color = Tone.Muted)
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
        val left = (dev.bikecompanion.data.Prefs.autoEndMinutes * 60L - (now - (rec.pausedAt ?: now)) / 1000).coerceAtLeast(0)
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
