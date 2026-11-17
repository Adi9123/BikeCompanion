package dev.bikecompanion.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.bikecompanion.data.LinkPhase
import dev.bikecompanion.data.LinkStatus
import dev.bikecompanion.ride.RecPhase
import dev.bikecompanion.ride.RecState
import kotlinx.coroutines.delay
import java.util.Locale

/** Spacing and shape tokens. See docs/design/DESIGN_SYSTEM.md#space-shape-depth. */
object Space {
    val gutter = 20.dp
    val card = 20.dp
    val betweenCards = 12.dp
    val section = 28.dp
}

object Shapes {
    val chip = RoundedCornerShape(12.dp)
    val control = RoundedCornerShape(16.dp)
    val card = RoundedCornerShape(24.dp)
    val hero = RoundedCornerShape(32.dp)
}

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

/** The title at the top of a screen, with an optional action on the right. */
@Composable
fun ScreenTitle(text: String, subtitle: String? = null, trailing: (@Composable RowScope.() -> Unit)? = null) {
    Row(
        Modifier.fillMaxWidth().padding(top = 20.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(text, style = MaterialTheme.typography.headlineLarge, color = Tone.Text)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = Tone.Muted)
            }
        }
        trailing?.invoke(this)
    }
}

/** Small uppercase overline that starts a group. */
@Composable
fun SectionTitle(text: String) {
    Text(
        text.uppercase(Locale.US),
        style = MaterialTheme.typography.labelSmall,
        color = Tone.Muted,
        modifier = Modifier.padding(top = Space.section, bottom = 10.dp, start = 4.dp),
    )
}

/**
 * The card. Hairline outline in both themes; a soft lift in light mode, where
 * white cards sit on paper. With [onClick] it squashes slightly when pressed.
 */
@Composable
fun Panel(
    modifier: Modifier = Modifier,
    color: Color = Tone.Panel,
    padding: Dp = Space.card,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val lift = if (!Tone.isDark && color == Tone.Panel) {
        Modifier.shadow(6.dp, Shapes.card, ambientColor = Color(0x14000000), spotColor = Color(0x1A000000))
    } else {
        Modifier
    }
    Column(
        modifier
            .then(if (onClick != null) Modifier.pressable(onClick = onClick) else Modifier)
            .then(lift)
            .clip(Shapes.card)
            .background(color)
            .border(BorderStroke(1.dp, Tone.Line.copy(alpha = if (Tone.isDark) 0.9f else 0.6f)), Shapes.card)
            .padding(padding),
        content = content,
    )
}

/** Label, rolling number, unit. Fades when the value is stale. */
@Composable
fun StatTile(
    label: String,
    value: String,
    unit: String = "",
    modifier: Modifier = Modifier,
    accent: Color = Tone.Text,
    stale: Boolean = false,
) {
    val a by animateFloatAsState(if (stale) 0.45f else 1f, glide(Motion.Base), label = "stale")
    Panel(modifier.alpha(a), padding = 18.dp) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = Tone.Muted, maxLines = 1)
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            RollingNumber(value, TypeX.number, accent)
            if (unit.isNotEmpty() && value != "—") {
                Text(
                    " $unit",
                    style = MaterialTheme.typography.labelMedium,
                    color = Tone.Muted,
                    modifier = Modifier.padding(bottom = 4.dp),
                )
            }
        }
    }
}

/** Two tiles side by side. */
@Composable
fun TileRow(content: @Composable (Modifier) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(Space.betweenCards), modifier = Modifier.fillMaxWidth()) {
        content(Modifier.weight(1f))
    }
}

@Composable
fun Dot(color: Color, size: Int = 8) {
    Box(Modifier.size(size.dp).clip(CircleShape).background(color))
}

/** Status capsule with a dot that breathes while [live]. */
@Composable
fun Pill(text: String, dot: Color, modifier: Modifier = Modifier, live: Boolean = false) {
    val bg by animateColorAsState(dot.copy(alpha = if (Tone.isDark) 0.14f else 0.12f), glide(Motion.Quick), label = "pill")
    Row(
        modifier
            .clip(RoundedCornerShape(50))
            .background(bg)
            .padding(start = 6.dp, end = 14.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LiveDot(dot, live, 7.dp)
        Text(
            text,
            style = MaterialTheme.typography.labelMedium,
            color = Tone.Text,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

fun linkColor(phase: LinkPhase): Color = when (phase) {
    LinkPhase.LIVE -> Tone.AccentText
    LinkPhase.IDLE -> Tone.Muted
    LinkPhase.FAILED -> Tone.Red
    else -> Tone.Amber
}

@Composable
fun StatusPill(s: LinkStatus, modifier: Modifier = Modifier) {
    val text = if (s.phase == LinkPhase.LIVE) (s.deviceName ?: "Connected") else s.phase.label
    Pill(text, linkColor(s.phase), modifier, live = s.phase == LinkPhase.LIVE)
}

@Composable
fun RecordingPill(r: RecState, modifier: Modifier = Modifier) {
    when (r.phase) {
        RecPhase.RECORDING -> Pill("REC  ${km(r.distanceKm)} km", Tone.Red, modifier, live = true)
        RecPhase.PAUSED -> Pill("Paused", Tone.Amber, modifier)
        RecPhase.IDLE -> Unit
    }
}

/** The one filled button style: the screen's main action. */
@Composable
fun PrimaryButton(text: String, modifier: Modifier = Modifier, enabled: Boolean = true, onClick: () -> Unit) {
    Box(
        modifier
            .defaultMinSize(minHeight = 56.dp)
            .alpha(if (enabled) 1f else 0.4f)
            .pressable(enabled = enabled, onClick = onClick)
            .clip(Shapes.control)
            .background(Tone.Accent)
            .padding(PaddingValues(horizontal = 24.dp, vertical = 16.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge, color = Tone.OnAccent)
    }
}

/** Tonal button for everything that is not the main action. */
@Composable
fun SecondaryButton(text: String, modifier: Modifier = Modifier, enabled: Boolean = true, onClick: () -> Unit) {
    Box(
        modifier
            .defaultMinSize(minHeight = 48.dp)
            .alpha(if (enabled) 1f else 0.4f)
            .pressable(enabled = enabled, onClick = onClick)
            .clip(Shapes.control)
            .background(Tone.Panel2)
            .padding(PaddingValues(horizontal = 20.dp, vertical = 13.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge, color = Tone.Text)
    }
}

/** Full-width connection banner with the detail line. */
@Composable
fun StatusBanner(s: LinkStatus) {
    Panel(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            LiveDot(linkColor(s.phase), s.phase == LinkPhase.LIVE, 9.dp)
            Spacer(Modifier.width(6.dp))
            Text(s.phase.label, style = MaterialTheme.typography.titleMedium)
            s.deviceName?.let { Text("  ·  $it", color = Tone.Muted, style = MaterialTheme.typography.bodyMedium) }
        }
        if (s.detail.isNotBlank()) {
            Text(s.detail, style = MaterialTheme.typography.bodySmall, color = Tone.Muted, modifier = Modifier.padding(top = 4.dp))
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

fun km(v: Double): String = String.format(Locale.US, "%.1f", v)

fun duration(seconds: Long): String {
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60
    return if (h > 0) String.format(Locale.US, "%dh %02dm", h, m) else String.format(Locale.US, "%dm %02ds", m, s)
}
