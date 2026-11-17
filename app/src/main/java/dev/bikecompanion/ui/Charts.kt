package dev.bikecompanion.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.util.Locale

/** A point on a time chart: seconds from the start of the ride, and a value. */
data class ChartPoint(val x: Float, val y: Float)

/** Keeps a chart responsive: at most [max] points, averaged in buckets. */
fun downsample(points: List<ChartPoint>, max: Int = 240): List<ChartPoint> {
    if (points.size <= max) return points
    val bucket = points.size.toFloat() / max
    return (0 until max).map { i ->
        val from = (i * bucket).toInt()
        val to = ((i + 1) * bucket).toInt().coerceAtMost(points.size).coerceAtLeast(from + 1)
        val slice = points.subList(from, to)
        ChartPoint(slice.map { it.x }.average().toFloat(), slice.map { it.y }.average().toFloat())
    }
}

/**
 * Area line chart with a gradient fill, faint grid lines, and the peak and
 * average in the header. [formatY] formats the header values.
 */
@Composable
fun LineChart(
    title: String,
    unit: String,
    points: List<ChartPoint>,
    color: Color,
    modifier: Modifier = Modifier,
    zeroBased: Boolean = true,
    formatY: (Float) -> String = { String.format(Locale.US, "%.0f", it) },
) {
    Panel(modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Dot(color, 10)
            Spacer(Modifier.width(8.dp))
            Text(title, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            if (points.isNotEmpty()) {
                val avg = points.map { it.y }.average().toFloat()
                val peak = points.maxOf { it.y }
                Text(
                    "avg ${formatY(avg)} · max ${formatY(peak)} $unit",
                    style = MaterialTheme.typography.bodySmall,
                    color = Tone.Muted,
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        if (points.size < 2) {
            Text("Not enough data", style = MaterialTheme.typography.bodySmall, color = Tone.Muted)
            return@Panel
        }
        val data = downsample(points)
        Canvas(Modifier.fillMaxWidth().height(130.dp)) {
            val minX = data.first().x
            val maxX = data.last().x.coerceAtLeast(minX + 1f)
            val lo = if (zeroBased) 0f else data.minOf { it.y }
            val hi = data.maxOf { it.y }.let { if (it - lo < 1f) lo + 1f else it }
            fun px(p: ChartPoint) = Offset(
                (p.x - minX) / (maxX - minX) * size.width,
                size.height - (p.y - lo) / (hi - lo) * size.height,
            )
            for (i in 0..3) {
                val y = size.height * i / 3f
                drawLine(Tone.Line, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
            }
            val line = Path()
            data.forEachIndexed { i, p ->
                val o = px(p)
                if (i == 0) line.moveTo(o.x, o.y) else line.lineTo(o.x, o.y)
            }
            val fill = Path().apply {
                addPath(line)
                lineTo(px(data.last()).x, size.height)
                lineTo(px(data.first()).x, size.height)
                close()
            }
            drawPath(fill, Brush.verticalGradient(listOf(color.copy(alpha = 0.35f), Color.Transparent)))
            drawPath(line, color, style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
        Row(Modifier.fillMaxWidth().padding(top = 6.dp)) {
            Text("0:00", style = MaterialTheme.typography.labelSmall, color = Tone.Muted, modifier = Modifier.weight(1f))
            Text(duration(points.last().x.toLong()), style = MaterialTheme.typography.labelSmall, color = Tone.Muted)
        }
    }
}

/** Tiny line for list items. */
@Composable
fun Sparkline(values: List<Float>, color: Color, modifier: Modifier = Modifier) {
    if (values.size < 2) return
    val pts = downsample(values.mapIndexed { i, v -> ChartPoint(i.toFloat(), v) }, 60)
    Canvas(modifier) {
        val hi = pts.maxOf { it.y }.coerceAtLeast(1f)
        val maxX = pts.last().x.coerceAtLeast(1f)
        val path = Path()
        pts.forEachIndexed { i, p ->
            val x = p.x / maxX * size.width
            val y = size.height - p.y / hi * size.height
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, color, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

/** Horizontal bars: share of moving time in each gear. */
@Composable
fun GearBars(gearSeconds: Map<Int, Long>, modifier: Modifier = Modifier) {
    val total = gearSeconds.values.sum().coerceAtLeast(1L)
    Panel(modifier.fillMaxWidth()) {
        Text("Time in each gear", fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(10.dp))
        if (gearSeconds.isEmpty()) {
            Text("No gear data", style = MaterialTheme.typography.bodySmall, color = Tone.Muted)
            return@Panel
        }
        for (g in 0..6) {
            val secs = gearSeconds[g] ?: 0L
            if (secs == 0L) continue
            val frac = secs.toFloat() / total
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
                Text(if (g == 0) "N" else "$g", fontWeight = FontWeight.Bold, modifier = Modifier.width(24.dp))
                Box(
                    Modifier
                        .weight(1f)
                        .height(12.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Tone.Panel2),
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth(frac)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(6.dp))
                            .background(Brush.horizontalGradient(listOf(Tone.Teal, Tone.Blue))),
                    )
                }
                Text(
                    String.format(Locale.US, "%3.0f%%", frac * 100),
                    style = MaterialTheme.typography.bodySmall,
                    color = Tone.Muted,
                    modifier = Modifier.width(52.dp).padding(start = 8.dp),
                )
            }
        }
    }
}
