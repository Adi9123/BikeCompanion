package dev.rtrcompanion.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.rtrcompanion.data.AppState
import dev.rtrcompanion.data.LinkPhase
import dev.rtrcompanion.data.Reading
import dev.rtrcompanion.protocol.ApacheFields
import dev.rtrcompanion.protocol.NavInstruction
import dev.rtrcompanion.ride.RecPhase
import dev.rtrcompanion.ride.RideRecorder
import java.util.Locale
import kotlin.math.cos
import kotlin.math.sin

private const val STALE_MS = 5_000L
private const val MAX_RPM = 11_000f
private const val REDLINE_RPM = 9_500f

private fun valueOf(r: Map<String, Reading>, key: String): String {
    val reading = r[key] ?: return "—"
    val spec = ApacheFields.spec(key) ?: return reading.raw.toString()
    return ApacheFields.format(spec, reading.raw)
}

@Composable
fun RideScreen(onOpenBike: () -> Unit) {
    val link by AppState.link.collectAsState()
    val readings by AppState.readings.collectAsState()
    val nav by AppState.nav.collectAsState()
    val rec by RideRecorder.state.collectAsState()
    val now = rememberNow()
    fun stale(key: String) = readings[key]?.let { now - it.at > STALE_MS } ?: true

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            StatusPill(link, Modifier.clickable { onOpenBike() })
            Spacer(Modifier.weight(1f))
            RecordingPill(rec)
        }

        if (link.phase == LinkPhase.IDLE && readings.isEmpty()) {
            Spacer(Modifier.height(16.dp))
            Panel(Modifier.fillMaxWidth(), color = Rtr.RedDeep) {
                Text("Not connected", fontWeight = FontWeight.Bold)
                Text(
                    "Switch the ignition on and connect to your bike.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Rtr.Muted,
                )
                Spacer(Modifier.height(10.dp))
                Button(onClick = onOpenBike) { Text("Connect") }
            }
        }

        Spacer(Modifier.height(8.dp))
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Speedometer(
                speed = readings["speed"]?.raw?.toFloat(),
                rpm = readings["rpm"]?.raw?.toFloat(),
                gear = valueOf(readings, "gear"),
                stale = stale("speed"),
            )
        }

        nav?.let {
            Spacer(Modifier.height(12.dp))
            NavCard(it)
        }

        if (rec.phase != RecPhase.IDLE) {
            Spacer(Modifier.height(12.dp))
            Panel(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Dot(if (rec.phase == RecPhase.RECORDING) Rtr.Red else Rtr.Amber, 10)
                    Spacer(Modifier.width(10.dp))
                    Text(
                        if (rec.phase == RecPhase.RECORDING) "Recording this ride" else "Ride paused · ${rec.reason}",
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f),
                    )
                }
                Spacer(Modifier.height(8.dp))
                Row {
                    MiniStat("Distance", "${km(rec.distanceKm)} km", Modifier.weight(1f))
                    MiniStat("Time", duration(((now - (rec.startedAt ?: now)) / 1000)), Modifier.weight(1f))
                    MiniStat("Top", "${rec.maxSpeed} km/h", Modifier.weight(1f))
                }
            }
        }

        SectionTitle("Trip")
        TileRow { m ->
            StatTile("Trip F", valueOf(readings, "tripF"), "km", m, stale = stale("tripF"))
            StatTile("Odometer", valueOf(readings, "odometer"), "km", m, stale = stale("odometer"))
        }
        Spacer(Modifier.height(10.dp))
        TileRow { m ->
            StatTile("Trip A", valueOf(readings, "tripA"), "km", m, stale = stale("tripA"))
            StatTile("Trip B", valueOf(readings, "tripB"), "km", m, stale = stale("tripB"))
        }
        Spacer(Modifier.height(10.dp))
        TileRow { m ->
            StatTile("Mileage A", valueOf(readings, "avgMileageA"), "km/l", m, Rtr.Green, stale("avgMileageA"))
            StatTile("Avg speed", valueOf(readings, "avgSpeed"), "km/h", m, stale = stale("avgSpeed"))
        }

        SectionTitle("Engine")
        TileRow { m ->
            StatTile("Engine temp", valueOf(readings, "engineTemp"), "°C", m, Rtr.Amber, stale("engineTemp"))
            StatTile("Battery", valueOf(readings, "battery"), "V", m, Rtr.Blue, stale("battery"))
        }
        Spacer(Modifier.height(10.dp))
        TileRow { m ->
            StatTile("Intake air", valueOf(readings, "intakeTemp"), "°C", m, stale = stale("intakeTemp"))
            StatTile("Top speed", valueOf(readings, "topSpeed"), "km/h", m, stale = stale("topSpeed"))
        }
        SectionTitle("Fuel")
        val fuel = rememberFuelStatus(rememberRefuels())
        Panel(Modifier.fillMaxWidth(), color = if (fuel.onReserve) Rtr.RedDeep else Rtr.Panel) {
            Row(verticalAlignment = Alignment.Bottom) {
                Column(Modifier.weight(1f)) {
                    Text("FUEL LEFT", style = MaterialTheme.typography.labelSmall, color = Rtr.Muted)
                    Text(
                        fuel.litresLeft?.let { (if (fuel.measured) "" else "≈ ") + litres(it) + " l" } ?: "—",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Column(Modifier.weight(1f)) {
                    Text("RANGE", style = MaterialTheme.typography.labelSmall, color = Rtr.Muted)
                    Text(fuel.rangeKm?.let { "≈ ${it.toInt()} km" } ?: "—", fontSize = 24.sp, fontWeight = FontWeight.Bold)
                }
                if (fuel.onReserve) Pill("Reserve", Rtr.Red)
            }
            Spacer(Modifier.height(10.dp))
            FuelBars(readings["fuel"]?.raw?.toInt(), dev.rtrcompanion.data.Prefs.fuelBars)
        }
        Spacer(Modifier.height(10.dp))
        Panel(Modifier.fillMaxWidth()) {
            Bar("Throttle", readings["throttle"]?.raw?.let { it / 200f }, valueOf(readings, "throttle") + " %", Rtr.Teal)
        }

        Spacer(Modifier.height(16.dp))
        Text(
            "— means the bike has not sent this value; faded tiles are more than 5 s old. " +
                "Range is worked out by the app from fuel left and your mileage; the bike does not send it.",
            style = MaterialTheme.typography.bodySmall,
            color = Rtr.Muted,
        )
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun MiniStat(label: String, value: String, modifier: Modifier) {
    Column(modifier) {
        Text(label.uppercase(Locale.US), style = MaterialTheme.typography.labelSmall, color = Rtr.Muted)
        Text(value, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun Bar(label: String, fraction: Float?, value: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label.uppercase(Locale.US), style = MaterialTheme.typography.labelSmall, color = Rtr.Muted, modifier = Modifier.weight(1f))
        Text(if (fraction == null) "—" else value, style = MaterialTheme.typography.bodySmall)
    }
    Spacer(Modifier.height(6.dp))
    Box(
        Modifier
            .fillMaxWidth()
            .height(10.dp)
            .clip(RoundedCornerShape(5.dp))
            .background(Rtr.Panel2),
    ) {
        val f by animateFloatAsState((fraction ?: 0f).coerceIn(0f, 1f), tween(300), label = label)
        Box(
            Modifier
                .fillMaxWidth(f)
                .height(10.dp)
                .clip(RoundedCornerShape(5.dp))
                .background(Brush.horizontalGradient(listOf(color.copy(alpha = 0.6f), color))),
        )
    }
}

/**
 * Round gauge: the arc fills with engine speed (teal, amber near the top,
 * red past the redline); the centre shows road speed and the gear.
 */
@Composable
fun Speedometer(speed: Float?, rpm: Float?, gear: String, stale: Boolean) {
    val animRpm by animateFloatAsState(rpm ?: 0f, tween(350), label = "rpm")
    val animSpeed by animateFloatAsState(speed ?: 0f, tween(350), label = "speed")
    Box(Modifier.size(290.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = 16.dp.toPx()
            val inset = stroke / 2 + 10.dp.toPx()
            val arcSize = Size(size.width - inset * 2, size.height - inset * 2)
            val topLeft = Offset(inset, inset)
            val startAngle = 135f
            val sweep = 270f
            drawArc(Rtr.Panel2, startAngle, sweep, false, topLeft, arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
            // redline zone
            val redFrom = REDLINE_RPM / MAX_RPM
            drawArc(
                Rtr.Red.copy(alpha = 0.25f), startAngle + sweep * redFrom, sweep * (1 - redFrom), false,
                topLeft, arcSize, style = Stroke(stroke, cap = StrokeCap.Butt),
            )
            val frac = (animRpm / MAX_RPM).coerceIn(0f, 1f)
            if (frac > 0f) {
                val color = when {
                    animRpm >= REDLINE_RPM -> Rtr.Red
                    frac > 0.65f -> Rtr.Amber
                    else -> Rtr.Teal
                }
                drawArc(color, startAngle, sweep * frac, false, topLeft, arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
            }
            // ticks every 1000 rpm
            val r = arcSize.width / 2
            val c = Offset(size.width / 2, size.height / 2)
            for (i in 0..11) {
                val a = Math.toRadians((startAngle + sweep * i / 11f).toDouble())
                val outer = r - stroke * 0.9f
                val inner = outer - if (i % 2 == 0) 10.dp.toPx() else 5.dp.toPx()
                drawLine(
                    if (i * 1000f >= REDLINE_RPM) Rtr.Red else Rtr.Muted,
                    Offset(c.x + (cos(a) * inner).toFloat(), c.y + (sin(a) * inner).toFloat()),
                    Offset(c.x + (cos(a) * outer).toFloat(), c.y + (sin(a) * outer).toFloat()),
                    strokeWidth = 2.dp.toPx(),
                )
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                if (speed == null) "—" else animSpeed.toInt().toString(),
                fontSize = 76.sp,
                fontWeight = FontWeight.Bold,
                color = if (stale) Rtr.Muted else Rtr.Text,
            )
            Text("km/h", color = Rtr.Muted)
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(Rtr.Panel2)
                        .padding(horizontal = 14.dp, vertical = 4.dp),
                ) {
                    Text(gear, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Rtr.Amber)
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(
                if (rpm == null) "— rpm" else "${animRpm.toInt()} rpm",
                style = MaterialTheme.typography.bodySmall,
                color = Rtr.Muted,
            )
        }
    }
}

@Composable
private fun NavCard(n: NavInstruction) {
    Panel(Modifier.fillMaxWidth(), color = Rtr.RedDeep) {
        Text("${n.source.uppercase(Locale.US)} · ${n.maneuver.label.uppercase(Locale.US)}", style = MaterialTheme.typography.labelSmall, color = Rtr.Muted)
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(n.distanceToTurnM?.let { formatDistance(it) } ?: "—", fontSize = 32.sp, fontWeight = FontWeight.Bold)
            if (n.text.isNotBlank()) {
                Text(n.text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(bottom = 6.dp))
            }
        }
        val left = listOfNotNull(n.remainingM?.let { formatDistance(it) }, n.remainingMin?.let { "$it min" }).joinToString(" · ")
        if (left.isNotEmpty()) Text("Remaining $left", style = MaterialTheme.typography.bodySmall, color = Rtr.Muted)
    }
}

fun formatDistance(m: Int): String =
    if (m >= 1000) String.format(Locale.US, "%.1f km", m / 1000.0) else "$m m"
