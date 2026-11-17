package dev.bikecompanion.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/** The five destinations, each with a hand-drawn icon. */
enum class Dest(val label: String) { RIDE("Ride"), MAP("Map"), RIDES("Rides"), GARAGE("Garage"), SETTINGS("Settings") }

/**
 * Floating tab bar. The selected-tab capsule moves like liquid: the edge in
 * the direction of travel leads on a stiff spring and the trailing edge
 * follows on a softer one, so it stretches and then settles (squash and
 * stretch). The newly selected icon pops.
 */
@Composable
fun TabBar(selected: Int, onSelect: (Int) -> Unit) {
    val n = Dest.entries.size
    val left = remember { Animatable(selected.toFloat()) }
    val right = remember { Animatable(selected + 1f) }
    var from by remember { mutableIntStateOf(selected) }

    LaunchedEffect(selected) {
        val movingRight = selected > from
        from = selected
        if (Motion.reduced) {
            left.snapTo(selected.toFloat())
            right.snapTo(selected + 1f)
            return@LaunchedEffect
        }
        val lead = spring<Float>(dampingRatio = 0.8f, stiffness = 900f)
        val trail = spring<Float>(dampingRatio = 0.75f, stiffness = 260f)
        launch { left.animateTo(selected.toFloat(), if (movingRight) trail else lead) }
        launch { right.animateTo(selected + 1f, if (movingRight) lead else trail) }
    }

    Box(
        Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        BoxWithConstraints(
            Modifier
                .fillMaxWidth()
                .height(68.dp)
                .shadow(if (Tone.isDark) 0.dp else 14.dp, RoundedCornerShape(26.dp), ambientColor = Color(0x22000000), spotColor = Color(0x26000000))
                .clip(RoundedCornerShape(26.dp))
                .background(Tone.Panel)
                .border(1.dp, Tone.Line.copy(alpha = 0.7f), RoundedCornerShape(26.dp))
                .padding(6.dp),
        ) {
            val cell = maxWidth / n
            // the liquid capsule
            Box(
                Modifier
                    .offset(x = cell * left.value)
                    .width(cell * (right.value - left.value))
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(20.dp))
                    .background(Tone.Accent),
            )
            Row(Modifier.fillMaxSize()) {
                Dest.entries.forEachIndexed { i, d ->
                    TabCell(d, i == selected, Modifier.weight(1f)) { onSelect(i) }
                }
            }
        }
    }
}

@Composable
private fun TabCell(d: Dest, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val pop = remember { Animatable(1f) }
    LaunchedEffect(selected) {
        if (selected && !Motion.reduced) {
            pop.animateTo(0.86f, tween(70))
            pop.animateTo(1f, Springs.pop())
        }
    }
    val tint = if (selected) Tone.OnAccent else Tone.Muted
    Column(
        modifier
            .fillMaxHeight()
            .semantics {
                role = Role.Tab
                this.selected = selected
                contentDescription = d.label
            }
            .pressable(scale = 0.92f, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
    ) {
        Canvas(
            Modifier
                .size(24.dp)
                .graphicsLayer {
                    scaleX = pop.value
                    scaleY = pop.value
                },
        ) { drawTabIcon(d, tint, if (selected) Tone.Accent else Tone.Panel) }
        Spacer(Modifier.height(3.dp))
        Text(d.label, style = MaterialTheme.typography.labelSmall, color = tint, maxLines = 1)
    }
}

/** Icons drawn on a 24-unit grid with a 2-unit rounded stroke. */
private fun DrawScope.drawTabIcon(d: Dest, c: Color, bg: Color) {
    val u = size.width / 24f
    fun p(x: Float, y: Float) = Offset(x * u, y * u)
    val stroke = Stroke(width = 2f * u, cap = StrokeCap.Round, join = StrokeJoin.Round)
    when (d) {
        Dest.RIDE -> {
            // gauge arc, needle, hub
            drawArc(c, 150f, 240f, false, p(3f, 4f), Size(18f * u, 18f * u), style = stroke)
            drawLine(c, p(12f, 13f), p(16.5f, 8.5f), strokeWidth = 2f * u, cap = StrokeCap.Round)
            drawCircle(c, 1.8f * u, p(12f, 13f))
        }
        Dest.MAP -> {
            // a route between two places
            val path = Path().apply {
                moveTo(6f * u, 18f * u)
                cubicTo(6f * u, 11f * u, 18f * u, 13f * u, 18f * u, 6f * u)
            }
            drawPath(path, c, style = Stroke(2f * u, cap = StrokeCap.Round, pathEffect = null))
            drawCircle(c, 2.6f * u, p(6f, 19f))
            drawCircle(c, 2.6f * u, p(18f, 5f), style = stroke)
        }
        Dest.RIDES -> {
            // a flag on a pole
            drawLine(c, p(6f, 3.5f), p(6f, 21f), strokeWidth = 2f * u, cap = StrokeCap.Round)
            val flag = Path().apply {
                moveTo(6f * u, 4.5f * u)
                cubicTo(10f * u, 2.5f * u, 13f * u, 6.5f * u, 18.5f * u, 4.5f * u)
                lineTo(18.5f * u, 12.5f * u)
                cubicTo(13f * u, 14.5f * u, 10f * u, 10.5f * u, 6f * u, 12.5f * u)
            }
            drawPath(flag, c, style = stroke)
        }
        Dest.GARAGE -> {
            // roof, walls, door
            val house = Path().apply {
                moveTo(3f * u, 11f * u)
                lineTo(12f * u, 4f * u)
                lineTo(21f * u, 11f * u)
                moveTo(5.5f * u, 9.5f * u)
                lineTo(5.5f * u, 20f * u)
                lineTo(18.5f * u, 20f * u)
                lineTo(18.5f * u, 9.5f * u)
                moveTo(9.5f * u, 20f * u)
                lineTo(9.5f * u, 14f * u)
                lineTo(14.5f * u, 14f * u)
                lineTo(14.5f * u, 20f * u)
            }
            drawPath(house, c, style = stroke)
        }
        Dest.SETTINGS -> {
            // two sliders
            drawLine(c, p(4f, 8f), p(20f, 8f), strokeWidth = 2f * u, cap = StrokeCap.Round)
            drawLine(c, p(4f, 16f), p(20f, 16f), strokeWidth = 2f * u, cap = StrokeCap.Round)
            drawCircle(bg, 3.2f * u, p(15f, 8f))
            drawCircle(c, 3.2f * u, p(15f, 8f), style = stroke)
            drawCircle(bg, 3.2f * u, p(9f, 16f))
            drawCircle(c, 3.2f * u, p(9f, 16f), style = stroke)
        }
    }
}
