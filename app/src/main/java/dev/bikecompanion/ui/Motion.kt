package dev.bikecompanion.ui

import android.content.Context
import android.provider.Settings
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/**
 * The motion vocabulary. Hand-built on Compose's own animation primitives,
 * with no animation libraries. Durations, curves and springs are named for
 * what a motorcycle does; docs/design/DESIGN_SYSTEM.md#motion explains each.
 */
object Motion {
    const val Tap = 90
    const val Quick = 160
    const val Base = 260
    const val Scene = 380
    const val Ignition = 1400

    /** True when the phone's animation scale is 0: instant transitions, no loops, no sweep. */
    var reduced by mutableStateOf(false)
        private set

    fun refresh(ctx: Context) {
        reduced = try {
            Settings.Global.getFloat(ctx.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
        } catch (e: Exception) {
            false
        }
    }
}

object Ease {
    /** Entrances: arrive fast, settle softly. */
    val Glide = CubicBezierEasing(0.16f, 1f, 0.3f, 1f)

    /** Exits: leave with intent. */
    val Launch = CubicBezierEasing(0.7f, 0f, 0.84f, 0f)

    /** Travel from A to B on screen. */
    val Shift = CubicBezierEasing(0.65f, 0f, 0.35f, 1f)
}

object Springs {
    /** Position and scale: a slight overshoot that settles like front forks. */
    fun <T> fork(): AnimationSpec<T> = spring(dampingRatio = 0.72f, stiffness = 420f)

    /** Gauges: real-needle inertia, settles without wobbling while readable. */
    fun <T> needle(): AnimationSpec<T> = spring(dampingRatio = 0.55f, stiffness = 140f)

    /** Press release: quick, barely-there bounce. */
    fun <T> snap(): AnimationSpec<T> = spring(dampingRatio = 0.85f, stiffness = 900f)

    /** Playful pop for icons (only on selection). */
    fun <T> pop(): AnimationSpec<T> = spring(dampingRatio = 0.42f, stiffness = 600f)
}

fun <T> glide(duration: Int = Motion.Base, delay: Int = 0): FiniteAnimationSpec<T> =
    if (Motion.reduced) tween(0) else tween(duration, delay, Ease.Glide)

// -------------------------------------------------------------- modifiers

/**
 * Squash-and-stretch press feedback: squashes to [scale] while held, springs
 * back with a tiny overshoot on release, and gives a light tick of haptics.
 */
fun Modifier.pressable(
    enabled: Boolean = true,
    scale: Float = 0.97f,
    onClick: () -> Unit,
): Modifier = composed {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val haptics = LocalHapticFeedback.current
    val s by animateFloatAsState(
        targetValue = if (pressed && !Motion.reduced) scale else 1f,
        animationSpec = if (pressed) tween(Motion.Tap, easing = FastOutSlowInEasing) else Springs.snap(),
        label = "press",
    )
    this
        .graphicsLayer {
            scaleX = s
            scaleY = s
        }
        .clickable(interactionSource = source, indication = null, enabled = enabled) {
            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            onClick()
        }
}

/**
 * Staggered entrance: fades in and rises 16 dp, each item a little after the
 * one before (overlapping action). Plays once, when the item first appears.
 */
fun Modifier.reveal(index: Int = 0): Modifier = composed {
    if (Motion.reduced) return@composed this
    val p = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay(45L * index.coerceIn(0, 8))
        p.animateTo(1f, tween(Motion.Scene, easing = Ease.Glide))
    }
    graphicsLayer {
        alpha = p.value
        translationY = (1f - p.value) * 16.dp.toPx()
    }
}

// ------------------------------------------------------------- components

/**
 * Digits that roll like an odometer when the value changes: up when it grows,
 * down when it shrinks, the rightmost digit first and the others following
 * a beat later. Characters that do not change stay still.
 */
@Composable
fun RollingNumber(text: String, style: TextStyle, color: Color, modifier: Modifier = Modifier) {
    var previous by remember { mutableStateOf(text) }
    val up = (text.filter { it.isDigit() || it == '.' }.toDoubleOrNull() ?: 0.0) >=
        (previous.filter { it.isDigit() || it == '.' }.toDoubleOrNull() ?: 0.0)
    LaunchedEffect(text) { previous = text }
    Row(modifier, verticalAlignment = Alignment.Bottom) {
        val chars = text.toList()
        chars.forEachIndexed { i, ch ->
            val fromRight = chars.size - 1 - i
            // Keyed from the right, so 9 -> 10 rolls the units digit instead of shifting everything.
            key(fromRight) {
            AnimatedContent(
                targetState = ch,
                transitionSpec = {
                    if (Motion.reduced) {
                        fadeIn(tween(0)) togetherWith fadeOut(tween(0))
                    } else {
                        val dir = if (up) 1 else -1
                        val delay = fromRight * 40
                        (slideInVertically(tween(Motion.Base, delay, Ease.Glide)) { h -> dir * h } +
                            fadeIn(tween(Motion.Quick, delay))) togetherWith
                            (slideOutVertically(tween(Motion.Base, delay, Ease.Glide)) { h -> -dir * h } +
                                fadeOut(tween(Motion.Quick, delay))) using SizeTransform(clip = true)
                    }
                },
                label = "digit",
            ) { c -> Text(c.toString(), style = style, color = color) }
            }
        }
    }
}

/** A dot that breathes a ring outward while something is live. */
@Composable
fun LiveDot(color: Color, live: Boolean, size: Dp = 8.dp) {
    Box(Modifier.size(size * 2.6f), contentAlignment = Alignment.Center) {
        if (live && !Motion.reduced) {
            val t = rememberInfiniteTransition(label = "breath")
            val p by t.animateFloat(
                0f, 1f,
                infiniteRepeatable(tween(1800, easing = Ease.Glide), RepeatMode.Restart),
                label = "ring",
            )
            Canvas(Modifier.size(size * 2.6f)) {
                val r = this.size.minDimension / 2
                drawCircle(color.copy(alpha = 0.45f * (1f - p)), radius = r * (0.38f + 0.62f * p))
            }
        }
        Canvas(Modifier.size(size)) { drawCircle(color) }
    }
}

/** Rings expanding outward, one after another: "looking for the bike". */
@Composable
fun Radar(color: Color, modifier: Modifier = Modifier) {
    if (Motion.reduced) {
        Canvas(modifier) { drawCircle(color.copy(alpha = 0.3f), radius = size.minDimension / 4, style = Stroke(2.dp.toPx())) }
        return
    }
    val t = rememberInfiniteTransition(label = "radar")
    val p by t.animateFloat(0f, 1f, infiniteRepeatable(tween(2400, easing = LinearEasing)), label = "sweep")
    Canvas(modifier) {
        val max = size.minDimension / 2
        for (k in 0 until 3) {
            val f = (p + k / 3f) % 1f
            val eased = Ease.Glide.transform(f)
            drawCircle(
                color.copy(alpha = 0.5f * (1f - f)),
                radius = max * (0.15f + 0.85f * eased),
                center = Offset(size.width / 2, size.height / 2),
                style = Stroke(2.dp.toPx()),
            )
        }
        drawCircle(color, radius = 5.dp.toPx())
    }
}
