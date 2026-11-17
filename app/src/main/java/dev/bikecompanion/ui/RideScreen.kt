package dev.bikecompanion.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.bikecompanion.data.AppState
import dev.bikecompanion.data.LinkPhase
import dev.bikecompanion.data.Prefs
import dev.bikecompanion.data.Reading
import dev.bikecompanion.garage.RideModes
import dev.bikecompanion.garage.Switch
import dev.bikecompanion.monitor.Alert
import dev.bikecompanion.monitor.BikeMonitor
import dev.bikecompanion.monitor.Severity
import dev.bikecompanion.protocol.ApacheFields
import dev.bikecompanion.protocol.NavInstruction
import dev.bikecompanion.ride.RecPhase
import dev.bikecompanion.ride.RideRecorder
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

/**
 * The live dashboard (stories S2, S3, S4, S6). Glance-first: speed is the
 * hero, then gear and revs; directions and warnings appear only when there is
 * something to say; the rest sits below for when the bike is stopped.
 */
@Composable
fun RideScreen(onOpenBike: () -> Unit) {
    val link by AppState.link.collectAsState()
    val readings by AppState.readings.collectAsState()
    val nav by AppState.nav.collectAsState()
    val rec by RideRecorder.state.collectAsState()
    val alerts by BikeMonitor.alerts.collectAsState()
    val switches by BikeMonitor.switches.collectAsState()
    val now = rememberNow()
    fun stale(key: String) = readings[key]?.let { now - it.at > STALE_MS } ?: true
    val live = link.phase == LinkPhase.LIVE

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Space.gutter),
    ) {
        // ------------------------------------------------------------ header
        Row(Modifier.fillMaxWidth().padding(top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    greeting(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Tone.Muted,
                )
                Text(
                    Prefs.riderName.trim().ifBlank { "Rider" }.lowercase(Locale.getDefault())
                        .replaceFirstChar { it.titlecase(Locale.getDefault()) },
                    style = MaterialTheme.typography.headlineLarge,
                    color = Tone.Text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            RecordingPill(rec)
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            StatusPill(link, Modifier.pressable(onClick = onOpenBike))
            readings["mode"]?.takeIf { now - it.at < STALE_MS }?.let { r ->
                val name = RideModes.name(RideModes.mode(r.raw), RideModes.decodeNames(Prefs.modeNames))
                Pill(
                    name + (RideModes.topSpeed(name)?.let { " · $it" } ?: ""),
                    modeColor(name),
                )
            }
        }

        // ------------------------------------------------------------ alerts
        alerts.forEachIndexed { i, a ->
            Spacer(Modifier.height(12.dp))
            Box(Modifier.reveal(i)) { AlertBanner(a) }
        }

        // ------------------------------------------------------------ hero
        if (!live && readings.isEmpty()) {
            Spacer(Modifier.height(16.dp))
            WaitingHero(link.phase, onOpenBike)
        } else {
            Spacer(Modifier.height(8.dp))
            Gauge(
                speed = readings["speed"]?.raw?.toFloat(),
                rpm = readings["rpm"]?.raw?.toFloat(),
                gear = valueOf(readings, "gear"),
                stale = stale("speed"),
                live = live,
            )
        }

        // ------------------------------------------------------------ directions
        AnimatedVisibility(
            visible = nav != null,
            enter = expandVertically(glide(Motion.Scene)) + fadeIn(glide(Motion.Scene)),
            exit = shrinkVertically(tween(Motion.Base, easing = Ease.Launch)) + fadeOut(tween(Motion.Quick)),
        ) {
            nav?.let {
                Column {
                    Spacer(Modifier.height(12.dp))
                    NavCard(it)
                }
            }
        }

        // ------------------------------------------------------------ lamps
        if (switches.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                SwitchChip("◀", switches[Switch.LEFT], Tone.Green, Modifier.weight(1f))
                SwitchChip("High", switches[Switch.HIGH_BEAM], Tone.Blue, Modifier.weight(1f))
                SwitchChip("Stand", switches[Switch.SIDE_STAND], Tone.Red, Modifier.weight(1f))
                SwitchChip("▶", switches[Switch.RIGHT], Tone.Green, Modifier.weight(1f))
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                SwitchChip("Clutch", switches[Switch.CLUTCH], Tone.Amber, Modifier.weight(1f))
                SwitchChip("Brake", switches[Switch.BRAKE], Tone.Red, Modifier.weight(1f))
                SwitchChip("Info", switches[Switch.CALL_BUTTON], Tone.Violet, Modifier.weight(1f))
            }
        }

        // ------------------------------------------------------------ this ride
        if (rec.phase != RecPhase.IDLE) {
            Spacer(Modifier.height(12.dp))
            Panel(Modifier.fillMaxWidth().reveal(0)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    LiveDot(if (rec.phase == RecPhase.RECORDING) Tone.Red else Tone.Amber, rec.phase == RecPhase.RECORDING, 8.dp)
                    Spacer(Modifier.width(6.dp))
                    Text(
                        if (rec.phase == RecPhase.RECORDING) "Recording this ride" else "Paused · ${rec.reason}",
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
                Spacer(Modifier.height(14.dp))
                Row {
                    MiniStat("Distance", km(rec.distanceKm), "km", Modifier.weight(1f))
                    MiniStat("Time", duration((now - (rec.startedAt ?: now)) / 1000), "", Modifier.weight(1f))
                    MiniStat("Top", rec.maxSpeed.toString(), "km/h", Modifier.weight(1f))
                }
            }
        }

        // ------------------------------------------------------------ fuel
        SectionTitle("Fuel")
        val fuel = rememberFuelStatus(rememberRefuels())
        Panel(Modifier.fillMaxWidth(), color = if (fuel.onReserve) Tone.RedDeep else Tone.Panel) {
            Row(verticalAlignment = Alignment.Bottom) {
                Column(Modifier.weight(1f)) {
                    Text("Range", style = MaterialTheme.typography.labelMedium, color = Tone.Muted)
                    Row(verticalAlignment = Alignment.Bottom) {
                        RollingNumber(fuel.rangeKm?.toInt()?.toString() ?: "—", MaterialTheme.typography.displayLarge, if (fuel.onReserve) Tone.Red else Tone.Text)
                        if (fuel.rangeKm != null) {
                            Text(" km", style = MaterialTheme.typography.titleMedium, color = Tone.Muted, modifier = Modifier.padding(bottom = 10.dp))
                        }
                    }
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("Fuel left", style = MaterialTheme.typography.labelMedium, color = Tone.Muted)
                    Text(
                        fuel.litresLeft?.let { (if (fuel.measured) "" else "about ") + litres(it) + " L" } ?: "—",
                        style = TypeX.numberSmall,
                        color = Tone.Text,
                    )
                    if (fuel.onReserve) {
                        Spacer(Modifier.height(6.dp))
                        Pill("Reserve", Tone.Red, live = true)
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
            FuelBars(readings["fuel"]?.raw?.toInt(), Prefs.fuelBars)
        }

        // ------------------------------------------------------------ trip
        SectionTitle("Trip")
        TileRow { m ->
            StatTile("Trip A", valueOf(readings, "tripA"), "km", m, stale = stale("tripA"))
            StatTile("Trip B", valueOf(readings, "tripB"), "km", m, stale = stale("tripB"))
        }
        Spacer(Modifier.height(Space.betweenCards))
        TileRow { m ->
            StatTile("Mileage", valueOf(readings, "avgMileageA"), "km/l", m, Tone.AccentText, stale("avgMileageA"))
            StatTile("Avg speed", valueOf(readings, "avgSpeed"), "km/h", m, stale = stale("avgSpeed"))
        }
        Spacer(Modifier.height(Space.betweenCards))
        TileRow { m ->
            StatTile("Odometer", valueOf(readings, "odometer"), "km", m, stale = stale("odometer"))
            StatTile("On reserve", valueOf(readings, "tripF"), "km", m, stale = stale("tripF"))
        }

        // ------------------------------------------------------------ engine
        SectionTitle("Engine")
        TileRow { m ->
            StatTile("Engine", valueOf(readings, "engineTemp"), "°C", m, Tone.Amber, stale("engineTemp"))
            StatTile("Battery", valueOf(readings, "battery"), "V", m, Tone.Blue, stale("battery"))
        }
        Spacer(Modifier.height(Space.betweenCards))
        TileRow { m ->
            StatTile("Intake air", valueOf(readings, "intakeTemp"), "°C", m, stale = stale("intakeTemp"))
            StatTile("Top speed", valueOf(readings, "topSpeed"), "km/h", m, stale = stale("topSpeed"))
        }
        Spacer(Modifier.height(Space.betweenCards))
        Panel(Modifier.fillMaxWidth()) {
            Bar("Throttle", readings["throttle"]?.raw?.let { it / 200f }, valueOf(readings, "throttle") + " %", Tone.Accent)
        }

        Spacer(Modifier.height(20.dp))
        Text(
            "— means the bike has not sent this value; faded values are more than 5 seconds old. " +
                "Range is worked out from fuel left and your mileage; the bike does not send it.",
            style = MaterialTheme.typography.bodySmall,
            color = Tone.Muted,
        )
        Spacer(Modifier.height(24.dp))
    }
}

private fun greeting(): String {
    val h = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
    return when (h) {
        in 5..11 -> "Good morning"
        in 12..16 -> "Good afternoon"
        in 17..21 -> "Good evening"
        else -> "Riding late"
    }
}

private fun modeColor(name: String): Color = when (name) {
    "Sport" -> Tone.Red
    "Rain" -> Tone.Blue
    else -> Tone.AccentText
}

/** Not connected yet: the radar says the app is looking, the button says what to do. */
@Composable
private fun WaitingHero(phase: LinkPhase, onOpenBike: () -> Unit) {
    val hasBike = Prefs.lastAddress != null
    Panel(Modifier.fillMaxWidth(), padding = 24.dp) {
        Box(Modifier.fillMaxWidth().height(180.dp), contentAlignment = Alignment.Center) {
            Radar(if (phase == LinkPhase.FAILED) Tone.Red else Tone.AccentText, Modifier.size(180.dp))
        }
        Spacer(Modifier.height(8.dp))
        Text(
            when {
                !hasBike -> "Let's find your bike"
                phase == LinkPhase.IDLE -> "Not connected"
                else -> "Looking for your bike"
            },
            style = MaterialTheme.typography.headlineSmall,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            if (hasBike) {
                "Switch the ignition on. The app connects by itself and stays connected while you ride."
            } else {
                "Switch the ignition on, then pick your bike from the list. It takes about a minute."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = Tone.Muted,
        )
        if (!hasBike || phase == LinkPhase.IDLE || phase == LinkPhase.FAILED) {
            Spacer(Modifier.height(18.dp))
            PrimaryButton(if (hasBike) "Connect now" else "Find my bike", Modifier.fillMaxWidth(), onClick = onOpenBike)
        }
    }
}

/**
 * Alerts drop in from above on a fork spring. Danger is exaggerated on
 * purpose: a stronger drop and a slow glow, so it cannot be missed.
 */
@Composable
fun AlertBanner(a: Alert) {
    val color = when (a.severity) {
        Severity.DANGER -> Tone.Red
        Severity.WARN -> Tone.Amber
        Severity.INFO -> Tone.Blue
    }
    val drop = remember { Animatable(if (Motion.reduced) 0f else 1f) }
    LaunchedEffect(Unit) { drop.animateTo(0f, Springs.fork()) }
    val glow = if (a.severity == Severity.DANGER && !Motion.reduced) {
        val t = rememberInfiniteTransition(label = "danger")
        t.animateFloat(0.12f, 0.26f, infiniteRepeatable(tween(1600, easing = Ease.Shift), RepeatMode.Reverse), label = "glow").value
    } else {
        0.14f
    }
    val lift = if (a.severity == Severity.DANGER) 28f else 14f
    Panel(
        Modifier
            .fillMaxWidth()
            .graphicsLayer {
                translationY = -lift * drop.value * density
                alpha = 1f - drop.value
            },
        color = color.copy(alpha = glow),
        padding = 16.dp,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            LiveDot(color, a.severity != Severity.INFO, 8.dp)
            Spacer(Modifier.width(6.dp))
            Column {
                Text(a.title, style = MaterialTheme.typography.titleMedium, color = color)
                Text(a.detail, style = MaterialTheme.typography.bodySmall, color = Tone.Text)
            }
        }
    }
}

/** One lamp or switch: lit in its colour when on, quiet when off, dashed out when unknown. */
@Composable
private fun SwitchChip(label: String, on: Boolean?, color: Color, modifier: Modifier) {
    val lit by animateFloatAsState(if (on == true) 1f else 0f, glide(Motion.Quick), label = label)
    Box(
        modifier
            .clip(Shapes.chip)
            .background(Tone.Panel2)
            .background(color.copy(alpha = 0.22f * lit))
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = when (on) {
                true -> if (Tone.isDark) color else Tone.Text
                false -> Tone.Muted
                null -> Tone.Muted.copy(alpha = 0.4f)
            },
        )
    }
}

@Composable
private fun MiniStat(label: String, value: String, unit: String, modifier: Modifier) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = Tone.Muted)
        Row(verticalAlignment = Alignment.Bottom) {
            RollingNumber(value, TypeX.numberSmall, Tone.Text)
            if (unit.isNotEmpty()) Text(" $unit", style = MaterialTheme.typography.bodySmall, color = Tone.Muted, modifier = Modifier.padding(bottom = 2.dp))
        }
    }
}

@Composable
private fun Bar(label: String, fraction: Float?, value: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = Tone.Muted, modifier = Modifier.weight(1f))
        Text(if (fraction == null) "—" else value, style = TypeX.numberSmall, color = Tone.Text)
    }
    Spacer(Modifier.height(10.dp))
    Box(
        Modifier
            .fillMaxWidth()
            .height(10.dp)
            .clip(RoundedCornerShape(5.dp))
            .background(Tone.Panel2),
    ) {
        val f by animateFloatAsState((fraction ?: 0f).coerceIn(0f, 1f), Springs.needle(), label = label)
        Box(
            Modifier
                .fillMaxWidth(f)
                .height(10.dp)
                .clip(RoundedCornerShape(5.dp))
                .background(color),
        )
    }
}

/**
 * The hero gauge: road speed in the centre, engine speed as a sweep around it.
 *
 * On connection it performs the **ignition sweep**, like the bike's own
 * cluster at key-on: a short dip (anticipation), a sweep to the limit, a beat,
 * and a fall back to the real value. Afterwards the arc follows the engine on
 * a needle spring (follow-through).
 */
@Composable
fun Gauge(speed: Float?, rpm: Float?, gear: String, stale: Boolean, live: Boolean) {
    val sweep = remember { Animatable(0f) }
    var sweeping by remember { mutableStateOf(false) }
    LaunchedEffect(live) {
        if (live && !Motion.reduced) {
            sweeping = true
            sweep.snapTo(0f)
            sweep.animateTo(-0.025f, tween(80, easing = Ease.Shift))
            sweep.animateTo(1f, tween(560, easing = Ease.Shift))
            kotlinx.coroutines.delay(140)
            sweep.animateTo(((rpm ?: 0f) / MAX_RPM).coerceIn(0f, 1f), tween(620, easing = Ease.Glide))
            sweeping = false
        }
    }
    val follow by animateFloatAsState(((rpm ?: 0f) / MAX_RPM).coerceIn(0f, 1f), Springs.needle(), label = "rpm")
    val shownSpeed by animateFloatAsState(speed ?: 0f, Springs.needle(), label = "speed")
    val frac = if (sweeping) sweep.value else follow

    Box(Modifier.fillMaxWidth().aspectRatio(1.05f), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize().padding(6.dp)) {
            val stroke = 18.dp.toPx()
            val d = size.minDimension - stroke
            val topLeft = Offset((size.width - d) / 2, (size.height - d) / 2)
            val arc = Size(d, d)
            val start = 135f
            val total = 270f
            // track
            drawArc(Tone.Panel2, start, total, false, topLeft, arc, style = Stroke(stroke, cap = StrokeCap.Round))
            // rev-limit zone
            val redFrom = REDLINE_RPM / MAX_RPM
            drawArc(Tone.Red.copy(alpha = 0.22f), start + total * redFrom, total * (1 - redFrom), false, topLeft, arc, style = Stroke(stroke, cap = StrokeCap.Butt))
            // the sweep: gets hotter towards the limit (the gradient encodes data)
            val f = frac.coerceIn(0f, 1f)
            if (f > 0.002f) {
                val c = when {
                    f * MAX_RPM >= REDLINE_RPM -> Tone.Red
                    f > 0.68f -> Tone.Amber
                    else -> Tone.Accent
                }
                drawArc(c, start, total * f, false, topLeft, arc, style = Stroke(stroke, cap = StrokeCap.Round))
                // a bright tip, like a needle
                val a = Math.toRadians((start + total * f).toDouble())
                val r = d / 2
                val cx = size.width / 2 + (cos(a) * r).toFloat()
                val cy = size.height / 2 + (sin(a) * r).toFloat()
                drawCircle(Brush.radialGradient(listOf(c.copy(alpha = 0.55f), Color.Transparent), Offset(cx, cy), stroke * 1.6f), stroke * 1.6f, Offset(cx, cy))
                drawCircle(Tone.Bg, stroke * 0.22f, Offset(cx, cy))
            }
            // ticks every 1000 rpm, inside the track
            val r = d / 2 - stroke * 1.15f
            val cx = size.width / 2
            val cy = size.height / 2
            for (i in 0..11) {
                val a = Math.toRadians((start + total * i / 11f).toDouble())
                val inner = r - if (i % 2 == 0) 10.dp.toPx() else 5.dp.toPx()
                drawLine(
                    if (i * 1000f >= REDLINE_RPM) Tone.Red else Tone.Muted.copy(alpha = 0.6f),
                    Offset(cx + (cos(a) * inner).toFloat(), cy + (sin(a) * inner).toFloat()),
                    Offset(cx + (cos(a) * r).toFloat(), cy + (sin(a) * r).toFloat()),
                    strokeWidth = 2.dp.toPx(),
                    cap = StrokeCap.Round,
                )
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                if (speed == null) "—" else shownSpeed.toInt().toString(),
                style = TypeX.hero,
                color = if (stale) Tone.Muted else Tone.Text,
            )
            Text("km/h", style = MaterialTheme.typography.labelMedium, color = Tone.Muted)
            Spacer(Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                GearBadge(gear)
                Text(
                    if (rpm == null) "— rpm" else String.format(Locale.US, "%,d rpm", (frac * MAX_RPM).toInt().coerceAtLeast(0)),
                    style = MaterialTheme.typography.labelLarge,
                    color = Tone.Muted,
                )
            }
        }
    }
}

/** The gear letter pops (squash and stretch) whenever it changes. */
@Composable
private fun GearBadge(gear: String) {
    val pop = remember { Animatable(1f) }
    LaunchedEffect(gear) {
        if (!Motion.reduced) {
            pop.snapTo(0.82f)
            pop.animateTo(1f, Springs.pop())
        }
    }
    Box(
        Modifier
            .graphicsLayer {
                scaleX = pop.value * (2f - pop.value).coerceAtLeast(1f)
                scaleY = pop.value
            }
            .clip(Shapes.chip)
            .background(Tone.Accent)
            .padding(horizontal = 14.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(gear, style = TypeX.numberSmall, color = Tone.OnAccent)
    }
}

/** Directions: the same arrow the cluster gets, the distance as the hero. */
@Composable
private fun NavCard(n: NavInstruction) {
    Panel(Modifier.fillMaxWidth(), color = if (Tone.isDark) Tone.Panel2 else Tone.Panel) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(76.dp).clip(Shapes.control).background(Tone.Accent),
                contentAlignment = Alignment.Center,
            ) {
                TurnArrow(n.maneuver, Tone.OnAccent, 52.dp)
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                RollingNumber(n.distanceToTurnM?.let { formatDistance(it) } ?: "—", MaterialTheme.typography.displayMedium, Tone.Text)
                Text(
                    n.text.ifBlank { n.maneuver.label },
                    style = MaterialTheme.typography.bodyLarge,
                    color = Tone.Text,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        val left = listOfNotNull(n.remainingM?.let { formatDistance(it) }, n.remainingMin?.let { "$it min" }).joinToString(" · ")
        if (left.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            Text("${n.source} · $left to go", style = MaterialTheme.typography.bodySmall, color = Tone.Muted)
        }
    }
}

fun formatDistance(m: Int): String =
    if (m >= 1000) String.format(Locale.US, "%.1f km", m / 1000.0) else "$m m"
