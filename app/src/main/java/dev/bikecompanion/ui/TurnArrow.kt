package dev.bikecompanion.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.bikecompanion.nav.ArrowReader
import dev.bikecompanion.nav.ArrowReader.P
import dev.bikecompanion.protocol.Maneuver
import kotlin.math.hypot

/**
 * The turn arrow, drawn by hand from the same shapes the arrow reader uses,
 * so what the phone shows is what the cluster was sent. A new turn swings in
 * along an arc from the side it points to (arcs, follow-through).
 */
@Composable
fun TurnArrow(m: Maneuver, color: Color, size: Dp = 64.dp) {
    AnimatedContent(
        targetState = m,
        transitionSpec = { EnterTransition.None togetherWith fadeOut(tween(Motion.Quick, easing = Ease.Launch)) },
        label = "turn",
    ) { target ->
        val swing = remember(target) { Animatable(if (Motion.reduced) 0f else 1f) }
        LaunchedEffect(target) { swing.animateTo(0f, Springs.fork()) }
        Box(
            Modifier
                .size(size)
                .graphicsLayer {
                    // rotate about the bottom centre, like the bike leaning into the turn
                    transformOrigin = TransformOrigin(0.5f, 1f)
                    rotationZ = -target.side * 28f * swing.value
                    alpha = 1f - swing.value * 0.9f
                    val s = 1f - 0.12f * swing.value
                    scaleX = s
                    scaleY = s
                },
        ) {
            Canvas(Modifier.size(size)) { drawManeuver(target, color) }
        }
    }
}

private val MIRRORS = mapOf(
    Maneuver.RIGHT to Maneuver.LEFT,
    Maneuver.SLIGHT_RIGHT to Maneuver.SLIGHT_LEFT,
    Maneuver.SHARP_RIGHT to Maneuver.SHARP_LEFT,
    Maneuver.UTURN_RIGHT to Maneuver.UTURN_LEFT,
)

private fun shapeFor(m: Maneuver): List<P>? {
    val left = ArrowReader.LEFT_SHAPES
    return when (m) {
        Maneuver.LEFT, Maneuver.SLIGHT_LEFT, Maneuver.SHARP_LEFT, Maneuver.UTURN_LEFT -> left[m]
        Maneuver.RIGHT, Maneuver.SLIGHT_RIGHT, Maneuver.SHARP_RIGHT, Maneuver.UTURN_RIGHT ->
            left[MIRRORS.getValue(m)]?.let { ArrowReader.mirror(it) }
        Maneuver.KEEP_LEFT, Maneuver.MERGE_LEFT -> left[Maneuver.SLIGHT_LEFT]
        Maneuver.KEEP_RIGHT, Maneuver.MERGE_RIGHT -> left[Maneuver.SLIGHT_LEFT]?.let { ArrowReader.mirror(it) }
        Maneuver.STRAIGHT, Maneuver.DEPART, Maneuver.MERGE, Maneuver.FERRY, Maneuver.UNKNOWN -> ArrowReader.STRAIGHT_SHAPE
        else -> null
    }
}

private fun DrawScope.drawManeuver(m: Maneuver, c: Color) {
    val w = size.width
    fun o(p: P) = Offset(p.x * w, p.y * w)
    val stroke = Stroke(width = 0.13f * w, cap = StrokeCap.Round, join = StrokeJoin.Round)

    fun arrow(path: List<P>) {
        val line = Path().apply {
            moveTo(path[0].x * w, path[0].y * w)
            for (i in 1 until path.size) lineTo(path[i].x * w, path[i].y * w)
        }
        drawPath(line, c, style = stroke)
        val a = path[path.size - 2]
        val b = path.last()
        val len = hypot((b.x - a.x).toDouble(), (b.y - a.y).toDouble()).toFloat()
        val dx = (b.x - a.x) / len
        val dy = (b.y - a.y) / len
        val head = 0.2f
        val half = 0.19f
        val tip = P(b.x + dx * head, b.y + dy * head)
        val tri = Path().apply {
            moveTo(tip.x * w, tip.y * w)
            lineTo((b.x - dy * half) * w, (b.y + dx * half) * w)
            lineTo((b.x + dy * half) * w, (b.y - dx * half) * w)
            close()
        }
        drawPath(tri, c, style = Stroke(width = 0.05f * w, join = StrokeJoin.Round))
        drawPath(tri, c)
    }

    val shape = shapeFor(m)
    if (shape != null) {
        arrow(shape)
        return
    }
    if (m == Maneuver.ARRIVE) {
        // a chequered-flag post
        drawLine(c, o(P(0.32f, 0.9f)), o(P(0.32f, 0.12f)), strokeWidth = 0.1f * w, cap = StrokeCap.Round)
        val flag = Path().apply {
            moveTo(0.32f * w, 0.14f * w)
            cubicTo(0.5f * w, 0.06f * w, 0.62f * w, 0.24f * w, 0.8f * w, 0.14f * w)
            lineTo(0.8f * w, 0.5f * w)
            cubicTo(0.62f * w, 0.6f * w, 0.5f * w, 0.42f * w, 0.32f * w, 0.5f * w)
            close()
        }
        drawPath(flag, c)
        return
    }
    // roundabouts: the ring, the road in from below, and the exit
    val centre = P(0.5f, 0.56f)
    drawCircle(c, 0.17f * w, o(centre), style = Stroke(0.1f * w))
    drawLine(c, o(P(0.5f, 0.96f)), o(P(0.5f, 0.74f)), strokeWidth = 0.1f * w, cap = StrokeCap.Round)
    val exit = when (m) {
        Maneuver.ROUNDABOUT_LEFT -> listOf(P(0.33f, 0.56f), P(0.16f, 0.56f))
        Maneuver.ROUNDABOUT_SLIGHT_LEFT, Maneuver.ROUNDABOUT_SHARP_LEFT -> listOf(P(0.38f, 0.44f), P(0.24f, 0.3f))
        Maneuver.ROUNDABOUT_RIGHT -> listOf(P(0.67f, 0.56f), P(0.84f, 0.56f))
        Maneuver.ROUNDABOUT_SLIGHT_RIGHT, Maneuver.ROUNDABOUT_SHARP_RIGHT -> listOf(P(0.62f, 0.44f), P(0.76f, 0.3f))
        Maneuver.ROUNDABOUT_UTURN -> listOf(P(0.4f, 0.7f), P(0.36f, 0.86f))
        else -> listOf(P(0.5f, 0.39f), P(0.5f, 0.2f))
    }
    arrow(exit)
}
