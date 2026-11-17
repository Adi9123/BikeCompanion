package dev.bikecompanion.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.bikecompanion.data.AppState
import dev.bikecompanion.data.Prefs
import dev.bikecompanion.garage.BatteryHealth
import dev.bikecompanion.garage.BatteryRecord
import dev.bikecompanion.garage.Due
import dev.bikecompanion.garage.GarageStore
import dev.bikecompanion.garage.Health
import dev.bikecompanion.garage.MaintItem
import dev.bikecompanion.garage.Maintenance
import dev.bikecompanion.garage.ServiceEntry
import dev.bikecompanion.ride.RideStore
import dev.bikecompanion.ride.RideSummary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@Composable
fun GarageScreen() {
    var sub by rememberSaveable { mutableIntStateOf(0) }
    Column(Modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = sub, containerColor = Rtr.Bg, contentColor = Rtr.Text) {
            listOf("Fuel", "Service", "Battery", "Stats").forEachIndexed { i, t ->
                Tab(selected = sub == i, onClick = { sub = i }, text = { Text(t) })
            }
        }
        when (sub) {
            0 -> FuelScreen()
            1 -> ServiceScreen()
            2 -> BatteryScreen()
            else -> StatsScreen()
        }
    }
}

/** Current odometer: live from the bike, or the last one seen. */
@Composable
private fun currentOdo(): Double? {
    val readings by AppState.readings.collectAsState()
    return readings["odometer"]?.raw?.let { it / 10.0 } ?: Prefs.lastOdoKm.takeIf { it > 0 }
}

private fun day(t: Long) = SimpleDateFormat("d MMM yyyy", Locale.getDefault()).format(Date(t))

// ---------------------------------------------------------------- service

@Composable
private fun ServiceScreen() {
    val ctx = LocalContext.current
    val store = remember { GarageStore(ctx.applicationContext) }
    val version by GarageStore.version.collectAsState()
    val items by produceState(emptyList<MaintItem>(), version) { value = withContext(Dispatchers.IO) { store.items() } }
    val log by produceState(emptyList<ServiceEntry>(), version) { value = withContext(Dispatchers.IO) { store.log() } }
    val odo = currentOdo()
    val readings by AppState.readings.collectAsState()
    val now = System.currentTimeMillis()
    var editing by remember { mutableStateOf<MaintItem?>(null) }
    var adding by remember { mutableStateOf(false) }

    editing?.let { item ->
        DoneDialog(item, odo, onDismiss = { editing = null }) { updated, cost ->
            store.saveItems(items.map { if (it.id == updated.id) updated else it })
            store.addLog(ServiceEntry(System.currentTimeMillis(), updated.lastOdo, updated.name, cost))
            editing = null
        }
    }
    if (adding) AddServiceDialog(odo, onDismiss = { adding = false }) { store.addLog(it); adding = false }

    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("ODOMETER", style = MaterialTheme.typography.labelSmall, color = Rtr.Muted)
                    Text(odo?.let { "${km(it)} km" } ?: "—", fontSize = 26.sp, fontWeight = FontWeight.Bold)
                }
                Button(onClick = { adding = true }) { Text("Log service") }
            }
            readings["service"]?.raw?.let {
                Text(
                    String.format(Locale.US, "Cluster service indicator: 0x%02X", it),
                    style = MaterialTheme.typography.bodySmall,
                    color = Rtr.Muted,
                )
            }
        }
        item { SectionTitle("Reminders") }
        items(items, key = { it.id }) { item ->
            val due = Maintenance.due(item, odo, now)
            val color = when (due.status) {
                Due.DUE -> Rtr.Red
                Due.SOON -> Rtr.Amber
                Due.OK -> Rtr.Green
                Due.UNKNOWN -> Rtr.Muted
            }
            Panel(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Dot(color, 10)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(item.name, fontWeight = FontWeight.SemiBold)
                        val parts = listOfNotNull(
                            due.kmLeft?.let { if (it >= 0) "in ${it.toInt()} km" else "${(-it).toInt()} km overdue" },
                            due.daysLeft?.let { if (it >= 0) "in $it days" else "${-it} days overdue" },
                        )
                        Text(
                            when {
                                due.status == Due.UNKNOWN -> "Tap Done to start tracking" + listOfNotNull(
                                    item.intervalKm?.let { " · every $it km" },
                                    item.intervalDays?.let { " · every $it days" },
                                ).joinToString("")
                                else -> parts.joinToString(" · ")
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = if (due.status == Due.DUE) Rtr.Red else Rtr.Muted,
                        )
                    }
                    OutlinedButton(onClick = { editing = item }) { Text("Done") }
                }
            }
        }
        if (log.isNotEmpty()) {
            item { SectionTitle("Service history") }
            items(log, key = { it.t }) { e ->
                Panel(Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(e.title, fontWeight = FontWeight.SemiBold)
                            Text(
                                listOfNotNull(day(e.t), e.odo?.let { "${km(it)} km" }, e.cost?.let { rupees(it) }).joinToString(" · "),
                                style = MaterialTheme.typography.bodySmall,
                                color = Rtr.Muted,
                            )
                        }
                        IconButton(onClick = { store.deleteLog(e.t) }) { Icon(Icons.Filled.Delete, contentDescription = "Delete", tint = Rtr.Muted) }
                    }
                }
            }
            item {
                val total = log.sumOf { it.cost ?: 0.0 }
                Text("Total spent on service: ${rupees(total)}", color = Rtr.Muted, modifier = Modifier.padding(bottom = 24.dp))
            }
        }
    }
}

@Composable
private fun DoneDialog(item: MaintItem, odo: Double?, onDismiss: () -> Unit, onSave: (MaintItem, Double?) -> Unit) {
    var odoText by remember { mutableStateOf(odo?.let { km(it) } ?: "") }
    var cost by remember { mutableStateOf("") }
    var everyKm by remember { mutableStateOf(item.intervalKm?.toString() ?: "") }
    var everyDays by remember { mutableStateOf(item.intervalDays?.toString() ?: "") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("${item.name} done") },
        text = {
            Column {
                NumberField("Odometer (km)", odoText) { odoText = it }
                NumberField("Cost (₹, optional)", cost) { cost = it }
                NumberField("Repeat every (km)", everyKm) { everyKm = it }
                NumberField("Repeat every (days)", everyDays) { everyDays = it }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onSave(
                    item.copy(
                        lastOdo = odoText.toDoubleOrNull() ?: odo,
                        lastDate = System.currentTimeMillis(),
                        intervalKm = everyKm.toIntOrNull(),
                        intervalDays = everyDays.toIntOrNull(),
                    ),
                    cost.toDoubleOrNull(),
                )
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun AddServiceDialog(odo: Double?, onDismiss: () -> Unit, onSave: (ServiceEntry) -> Unit) {
    var title by remember { mutableStateOf("") }
    var odoText by remember { mutableStateOf(odo?.let { km(it) } ?: "") }
    var cost by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Log service or repair") },
        text = {
            Column {
                OutlinedTextField(value = title, onValueChange = { title = it.take(40) }, label = { Text("What was done") }, singleLine = true)
                NumberField("Odometer (km)", odoText) { odoText = it }
                NumberField("Cost (₹)", cost) { cost = it }
            }
        },
        confirmButton = {
            TextButton(enabled = title.isNotBlank(), onClick = {
                onSave(ServiceEntry(System.currentTimeMillis(), odoText.toDoubleOrNull(), title.trim(), cost.toDoubleOrNull()))
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun NumberField(label: String, value: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = { v -> onChange(v.filter { it.isDigit() || it == '.' }.take(9)) },
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = Modifier.padding(top = 6.dp),
    )
}

// ---------------------------------------------------------------- battery

@Composable
private fun BatteryScreen() {
    val ctx = LocalContext.current
    val store = remember { GarageStore(ctx.applicationContext) }
    val version by GarageStore.version.collectAsState()
    val records by produceState(emptyList<BatteryRecord>(), version) { value = withContext(Dispatchers.IO) { store.battery() } }
    val readings by AppState.readings.collectAsState()
    val verdict = BatteryHealth.assess(records)
    fun color(h: Health) = when (h) {
        Health.GOOD -> Rtr.Green
        Health.FAIR -> Rtr.Amber
        Health.WEAK -> Rtr.Red
        Health.UNKNOWN -> Rtr.Muted
    }

    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Spacer(Modifier.height(8.dp))
            Panel(Modifier.fillMaxWidth()) {
                Text("BATTERY NOW", style = MaterialTheme.typography.labelSmall, color = Rtr.Muted)
                Text(
                    readings["battery"]?.raw?.let { String.format(Locale.US, "%.1f V", it / 10.0) } ?: "—",
                    fontSize = 40.sp,
                    fontWeight = FontWeight.Bold,
                    color = Rtr.Blue,
                )
                Text(
                    "About 12.4–12.8 V with the engine off, 13.5–14.8 V while running means all is well.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Rtr.Muted,
                )
            }
        }
        item {
            TileRow { m ->
                StatTile("Battery", verdict.battery.label, "", m, color(verdict.battery))
                StatTile("Charging", verdict.charging.label, "", m, color(verdict.charging))
            }
        }
        item {
            Panel(Modifier.fillMaxWidth()) {
                Text("What the app sees", fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(6.dp))
                if (verdict.notes.isEmpty()) {
                    Text(
                        "Nothing yet. Each time you switch on and start with the app connected, it records the resting " +
                            "voltage, the dip while starting and the charging voltage.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Rtr.Muted,
                    )
                } else {
                    verdict.notes.forEach { Text("• $it", style = MaterialTheme.typography.bodySmall) }
                }
            }
        }
        val sorted = records.sortedBy { it.t }
        if (sorted.size >= 2) {
            item {
                LineChart(
                    "Resting voltage", "V",
                    sorted.mapIndexedNotNull { i, r -> r.resting?.let { ChartPoint(i.toFloat(), it) } },
                    Rtr.Blue, zeroBased = false, formatY = { String.format(Locale.US, "%.1f", it) },
                )
            }
            item {
                LineChart(
                    "Charging voltage", "V",
                    sorted.mapIndexedNotNull { i, r -> r.charging?.let { ChartPoint(i.toFloat(), it) } },
                    Rtr.Teal, zeroBased = false, formatY = { String.format(Locale.US, "%.1f", it) },
                )
            }
        }
        item {
            Text(
                "${records.size} key-ons recorded. Graphs are across key-ons, oldest on the left.",
                style = MaterialTheme.typography.bodySmall,
                color = Rtr.Muted,
                modifier = Modifier.padding(bottom = 24.dp),
            )
        }
    }
}

// ---------------------------------------------------------------- stats

@Composable
private fun StatsScreen() {
    val ctx = LocalContext.current
    val rideStore = remember { RideStore(ctx.applicationContext) }
    val rides by produceState(emptyList<RideSummary>()) { value = withContext(Dispatchers.IO) { rideStore.list() } }
    val refuels = rememberRefuels()
    val weekStart = Calendar.getInstance().apply {
        set(Calendar.DAY_OF_WEEK, firstDayOfWeek); set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0)
    }.timeInMillis
    val monthStart = Calendar.getInstance().apply {
        set(Calendar.DAY_OF_MONTH, 1); set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0)
    }.timeInMillis

    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { Spacer(Modifier.height(8.dp)); PeriodPanel("This week", weekStart, rides, refuels) }
        item { PeriodPanel("This month", monthStart, rides, refuels) }
        item { PeriodPanel("All time", 0L, rides, refuels) }
        item {
            // km per day, last 14 days
            val dayMs = 86_400_000L
            val today = Calendar.getInstance().apply { set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0) }.timeInMillis
            val days = (13 downTo 0).map { d ->
                val from = today - d * dayMs
                rides.filter { it.start >= from && it.start < from + dayMs }.sumOf { it.distanceKm }.toFloat()
            }
            Panel(Modifier.fillMaxWidth()) {
                Text("Last 14 days", fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(10.dp))
                val max = (days.maxOrNull() ?: 0f).coerceAtLeast(1f)
                Canvas(Modifier.fillMaxWidth().height(110.dp)) {
                    val w = size.width / days.size
                    days.forEachIndexed { i, v ->
                        val h = v / max * size.height
                        drawRoundRect(
                            color = if (i == days.size - 1) Rtr.Red else Rtr.Teal,
                            topLeft = Offset(i * w + w * 0.15f, size.height - h),
                            size = Size(w * 0.7f, h.coerceAtLeast(2f)),
                            cornerRadius = CornerRadius(6f, 6f),
                        )
                    }
                }
                Text("Today is on the right, in red.", style = MaterialTheme.typography.labelSmall, color = Rtr.Muted)
            }
        }
        if (rides.isNotEmpty()) {
            item {
                SectionTitle("Records")
                TileRow { m ->
                    StatTile("Top speed", "${rides.maxOf { it.maxSpeed }}", "km/h", m, Rtr.Red)
                    StatTile("Longest ride", km(rides.maxOf { it.distanceKm }), "km", m)
                }
                Spacer(Modifier.height(10.dp))
                TileRow { m ->
                    StatTile("Smoothest", "${rides.maxOf { it.smoothness }}", "/100", m, Rtr.Green)
                    StatTile("Since", day(rides.minOf { it.start }), "", m)
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun PeriodPanel(title: String, from: Long, rides: List<RideSummary>, refuels: List<dev.bikecompanion.fuel.Refuel>) {
    val rs = rides.filter { it.start >= from }
    val dist = rs.sumOf { it.distanceKm }
    val time = rs.sumOf { it.movingS }
    val spent = refuels.filter { it.t >= from }.sumOf { it.price ?: 0.0 }
    Panel(Modifier.fillMaxWidth()) {
        Text(title.uppercase(Locale.US), style = MaterialTheme.typography.labelSmall, color = Rtr.Muted)
        Row(verticalAlignment = Alignment.Bottom) {
            Text(km(dist), fontSize = 32.sp, fontWeight = FontWeight.Bold)
            Text(" km", color = Rtr.Muted, modifier = Modifier.padding(bottom = 5.dp))
        }
        Row {
            Column(Modifier.weight(1f)) {
                Text("RIDES", style = MaterialTheme.typography.labelSmall, color = Rtr.Muted)
                Text("${rs.size}", fontWeight = FontWeight.SemiBold)
            }
            Column(Modifier.weight(1f)) {
                Text("RIDING", style = MaterialTheme.typography.labelSmall, color = Rtr.Muted)
                Text(duration(time), fontWeight = FontWeight.SemiBold)
            }
            Column(Modifier.weight(1f)) {
                Text("FUEL", style = MaterialTheme.typography.labelSmall, color = Rtr.Muted)
                Text(rupees(spent), fontWeight = FontWeight.SemiBold)
            }
        }
    }
}
