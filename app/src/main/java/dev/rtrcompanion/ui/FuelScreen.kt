package dev.rtrcompanion.ui

import androidx.compose.foundation.background
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.rtrcompanion.data.AppState
import dev.rtrcompanion.data.Prefs
import dev.rtrcompanion.fuel.FuelMath
import dev.rtrcompanion.fuel.FuelMeter
import dev.rtrcompanion.fuel.FuelStatus
import dev.rtrcompanion.fuel.FuelStore
import dev.rtrcompanion.fuel.Refuel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** The refuel list, reloaded whenever it changes. */
@Composable
fun rememberRefuels(): List<Refuel> {
    val ctx = LocalContext.current
    val store = remember { FuelStore(ctx.applicationContext) }
    val version by FuelStore.version.collectAsState()
    val list by produceState(emptyList<Refuel>(), version) {
        value = withContext(Dispatchers.IO) { store.list() }
    }
    return list
}

/** Live fuel status from the refuel history and the bike's latest values. */
@Composable
fun rememberFuelStatus(refuels: List<Refuel>): FuelStatus {
    val readings by AppState.readings.collectAsState()
    return FuelMath.status(
        refuels = refuels,
        tankLitres = Prefs.tankLitres,
        reserveLitres = Prefs.reserveLitres,
        totalBars = Prefs.fuelBars,
        bars = readings["fuel"]?.raw?.toInt()?.takeIf { it in 0..Prefs.fuelBars },
        injNow = Prefs.injTotal,
        clusterMileage = readings["avgMileageA"]?.raw?.toInt(),
    )
}

fun litres(v: Double): String = String.format(Locale.US, "%.1f", v)
fun rupees(v: Double): String = "₹" + String.format(Locale.US, "%,.0f", v)

@Composable
fun FuelScreen() {
    val ctx = LocalContext.current
    val store = remember { FuelStore(ctx.applicationContext) }
    val refuels = rememberRefuels()
    val status = rememberFuelStatus(refuels)
    val readings by AppState.readings.collectAsState()
    var adding by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<Refuel?>(null) }

    val tanks = FuelMath.tanks(refuels)
    val calibration = FuelMath.calibration(refuels)
    val monthStart = Calendar.getInstance().apply {
        set(Calendar.DAY_OF_MONTH, 1); set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0)
    }.timeInMillis
    val spentMonth = refuels.filter { it.t >= monthStart }.sumOf { it.price ?: 0.0 }

    if (adding) AddRefuelDialog(onDismiss = { adding = false })
    deleting?.let { r ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Delete this refuel?") },
            text = { Text("${litres(r.litres)} l on ${dateOf(r.t)}") },
            confirmButton = { TextButton(onClick = { store.delete(r.t); deleting = null }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancel") } },
        )
    }

    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Spacer(Modifier.height(4.dp))
            Panel(Modifier.fillMaxWidth(), color = if (status.onReserve) Rtr.RedDeep else Rtr.Panel) {
                Text("FUEL LEFT", style = MaterialTheme.typography.labelSmall, color = Rtr.Muted)
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        status.litresLeft?.let { (if (status.measured) "" else "≈ ") + litres(it) } ?: "—",
                        fontSize = 44.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(" / ${litres(Prefs.tankLitres)} l", color = Rtr.Muted, modifier = Modifier.padding(bottom = 8.dp))
                    Spacer(Modifier.weight(1f))
                    if (status.onReserve) Pill("Reserve", Rtr.Red)
                }
                Spacer(Modifier.height(8.dp))
                FuelBars(readings["fuel"]?.raw?.toInt(), Prefs.fuelBars)
                Spacer(Modifier.height(12.dp))
                Row {
                    Column(Modifier.weight(1f)) {
                        Text("RANGE", style = MaterialTheme.typography.labelSmall, color = Rtr.Muted)
                        Text(status.rangeKm?.let { "≈ ${it.toInt()} km" } ?: "—", fontWeight = FontWeight.Bold, fontSize = 20.sp)
                    }
                    Column(Modifier.weight(1f)) {
                        Text("SOURCE", style = MaterialTheme.typography.labelSmall, color = Rtr.Muted)
                        Text(
                            if (status.measured) "Fuel counter" else "Gauge bars",
                            fontWeight = FontWeight.SemiBold,
                            color = if (status.measured) Rtr.Green else Rtr.Amber,
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                Button(onClick = { adding = true }, modifier = Modifier.fillMaxWidth()) { Text("Add refuel") }
            }
        }
        item {
            TileRow { m ->
                StatTile("Real mileage", FuelMath.averageMileage(tanks)?.let { String.format(Locale.US, "%.1f", it) } ?: "—", "km/l", m, Rtr.Green)
                StatTile("Last tank", tanks.lastOrNull()?.let { String.format(Locale.US, "%.1f", it.kmpl) } ?: "—", "km/l", m)
            }
            Spacer(Modifier.height(10.dp))
            TileRow { m ->
                StatTile("Cost per km", FuelMath.averageCostPerKm(tanks)?.let { "₹" + String.format(Locale.US, "%.2f", it) } ?: "—", "", m, Rtr.Amber)
                StatTile("This month", rupees(spentMonth), "", m)
            }
            Spacer(Modifier.height(10.dp))
            TileRow { m ->
                StatTile("Fuel bought", litres(refuels.sumOf { it.litres }), "l", m)
                StatTile("Fill-ups", refuels.size.toString(), "", m)
            }
        }
        item {
            Panel(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Dot(if (calibration != null) Rtr.Green else Rtr.Amber, 10)
                    Spacer(Modifier.width(10.dp))
                    Text(
                        if (calibration != null) "Fuel counter calibrated" else "Fuel counter not calibrated yet",
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    if (calibration != null) {
                        "Fuel left and fuel used per ride are measured. One counter step ≈ " +
                            String.format(Locale.US, "%.2f", calibration * 1_000_000) + " µl."
                    } else {
                        "Fill the tank completely and add it here. Ride with the app connected, then fill completely " +
                            "again and add it. From those two fills the app learns the bike's fuel counter and starts " +
                            "measuring fuel per ride. Until then, fuel left is estimated from the gauge."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = Rtr.Muted,
                )
            }
        }
        if (refuels.isNotEmpty()) {
            item { SectionTitle("History") }
            items(refuels.sortedByDescending { it.t }, key = { it.t }) { r ->
                val tank = tanks.firstOrNull { it.end.t == r.t }
                Panel(Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(dateOf(r.t), style = MaterialTheme.typography.labelLarge, color = Rtr.Muted)
                            Row(verticalAlignment = Alignment.Bottom) {
                                Text(litres(r.litres), fontSize = 24.sp, fontWeight = FontWeight.Bold)
                                Text(" l", color = Rtr.Muted, modifier = Modifier.padding(bottom = 3.dp))
                                Spacer(Modifier.width(12.dp))
                                r.price?.let { Text(rupees(it), fontWeight = FontWeight.SemiBold) }
                                if (r.price != null && r.litres > 0) {
                                    Text("  ${rupees(r.price / r.litres)}/l", style = MaterialTheme.typography.bodySmall, color = Rtr.Muted)
                                }
                            }
                            Text(
                                listOfNotNull(
                                    if (r.full) "Full tank" else "Partial",
                                    r.odoKm?.let { "${km(it)} km" },
                                    tank?.let { String.format(Locale.US, "%.1f km/l over %.0f km", it.kmpl, it.km) },
                                ).joinToString(" · "),
                                style = MaterialTheme.typography.bodySmall,
                                color = Rtr.Muted,
                            )
                        }
                        IconButton(onClick = { deleting = r }) {
                            Icon(Icons.Filled.Delete, contentDescription = "Delete", tint = Rtr.Muted)
                        }
                    }
                }
            }
        }
        item {
            Text(
                "Tank ${litres(Prefs.tankLitres)} l including ${litres(Prefs.reserveLitres)} l reserve, ${Prefs.fuelBars}-bar gauge. " +
                    "Real mileage is worked out from full tank to full tank.",
                style = MaterialTheme.typography.bodySmall,
                color = Rtr.Muted,
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}

private fun dateOf(t: Long): String = SimpleDateFormat("d MMM yyyy · h:mm a", Locale.getDefault()).format(Date(t))

/** The cluster's gauge as segments; the last one is the reserve. */
@Composable
fun FuelBars(bars: Int?, total: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
        for (i in 1..total) {
            val on = bars != null && i <= bars
            val color = when {
                !on -> Rtr.Panel2
                (bars ?: 0) <= 1 -> Rtr.Red
                (bars ?: 0) <= 2 -> Rtr.Amber
                else -> Rtr.Green
            }
            Box(
                Modifier
                    .weight(1f)
                    .height(14.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(color),
            )
        }
    }
}

/** Litres, amount, odometer (filled from the bike), full tank or not. */
@Composable
fun AddRefuelDialog(onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val store = remember { FuelStore(ctx.applicationContext) }
    val readings by AppState.readings.collectAsState()
    var litresText by remember { mutableStateOf("") }
    var priceText by remember { mutableStateOf("") }
    var odoText by remember { mutableStateOf(readings["odometer"]?.raw?.let { km(it / 10.0) } ?: "") }
    var full by remember { mutableStateOf(true) }
    val litresValue = litresText.replace(',', '.').toDoubleOrNull()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add refuel") },
        text = {
            Column {
                OutlinedTextField(
                    value = litresText,
                    onValueChange = { litresText = it.filter { c -> c.isDigit() || c == '.' || c == ',' }.take(6) },
                    label = { Text("Litres") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = priceText,
                    onValueChange = { priceText = it.filter { c -> c.isDigit() || c == '.' }.take(7) },
                    label = { Text("Amount paid (₹, optional)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = odoText,
                    onValueChange = { odoText = it.filter { c -> c.isDigit() || c == '.' }.take(9) },
                    label = { Text("Odometer (km)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = full, onCheckedChange = { full = it })
                    Text("Filled to full")
                }
                Text(
                    "Mark full only if the tank was filled to the top; mileage is measured between full fills.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Rtr.Muted,
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = litresValue != null && litresValue > 0.0 && litresValue <= Prefs.tankLitres + 1,
                onClick = {
                    store.add(
                        Refuel(
                            t = System.currentTimeMillis(),
                            odoKm = odoText.toDoubleOrNull(),
                            litres = litresValue ?: 0.0,
                            price = priceText.toDoubleOrNull(),
                            full = full,
                            injTotal = Prefs.injTotal,
                            trackedKm = Prefs.trackedKm,
                        ),
                    )
                    onDismiss()
                },
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** Asks after a refuel is spotted (fuel bars up two or more since the last ride). */
@Composable
fun FuelDialogs() {
    val prompt by FuelMeter.refuelPrompt.collectAsState()
    var adding by remember { mutableStateOf(false) }
    val p = prompt
    if (p != null && !adding) {
        AlertDialog(
            onDismissRequest = { FuelMeter.refuelPrompt.value = null },
            title = { Text("Refuelled?") },
            text = { Text("The fuel gauge went from ${p.first} to ${p.second} bars since your last ride. Add the refuel to keep mileage and cost accurate.") },
            confirmButton = { TextButton(onClick = { adding = true }) { Text("Add refuel") } },
            dismissButton = { TextButton(onClick = { FuelMeter.refuelPrompt.value = null }) { Text("No") } },
        )
    }
    if (adding) {
        AddRefuelDialog(onDismiss = {
            adding = false
            FuelMeter.refuelPrompt.value = null
        })
    }
}
