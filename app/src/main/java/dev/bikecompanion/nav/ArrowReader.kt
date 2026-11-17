package dev.bikecompanion.nav

import dev.bikecompanion.protocol.Maneuver
import java.security.MessageDigest
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.sqrt

/**
 * Reads the turn arrow that Google Maps draws in its navigation notification.
 *
 * Maps often leaves the direction out of the notification text ("toward
 * Example Road"), so the picture is the only reliable source. The icon is
 * reduced to an [N]x[N] ink mask, then:
 *
 * - a ring (an enclosed hole) means a roundabout, and the exit direction is
 *   read from the ink that sticks out of the ring;
 * - anything else is compared with hand-drawn arrow templates, and the most
 *   similar one wins.
 *
 * Pure Kotlin on plain arrays, so it is unit-tested without a device.
 */
object ArrowReader {
    const val N = 32

    // ----------------------------------------------------------------- input

    /**
     * Ink mask from ARGB pixels of any size. On a transparent background ink
     * is what is opaque; on an opaque background ink is what differs from the
     * corners. Half-transparent strokes (the grey "other branch" Maps draws at
     * a fork) are not ink.
     */
    fun mask(w: Int, h: Int, argb: IntArray): BooleanArray {
        val corners = intArrayOf(argb[0], argb[w - 1], argb[(h - 1) * w], argb[h * w - 1])
        val transparentBg = corners.map { it ushr 24 }.average() < 128
        val bgLum = corners.map { lum(it) }.average()
        val out = BooleanArray(N * N)
        for (gy in 0 until N) {
            val y0 = gy * h / N
            val y1 = max(y0 + 1, (gy + 1) * h / N)
            for (gx in 0 until N) {
                val x0 = gx * w / N
                val x1 = max(x0 + 1, (gx + 1) * w / N)
                var ink = 0
                var total = 0
                for (y in y0 until y1) for (x in x0 until x1) {
                    val p = argb[y * w + x]
                    val a = p ushr 24
                    val isInk = if (transparentBg) a >= 170 else a >= 170 && abs(lum(p) - bgLum) > 60
                    if (isInk) ink++
                    total++
                }
                out[gy * N + gx] = ink * 2 >= total
            }
        }
        return out
    }

    private fun lum(p: Int): Double =
        0.299 * ((p shr 16) and 255) + 0.587 * ((p shr 8) and 255) + 0.114 * (p and 255)

    /** Short stable id for an icon, used to remember what it means. */
    fun signature(mask: BooleanArray): String {
        val bytes = ByteArray((mask.size + 7) / 8)
        for (i in mask.indices) if (mask[i]) bytes[i / 8] = (bytes[i / 8].toInt() or (1 shl (i % 8))).toByte()
        val d = MessageDigest.getInstance("SHA-1").digest(bytes)
        return d.take(6).joinToString("") { String.format("%02x", it.toInt() and 0xFF) }
    }

    // ---------------------------------------------------------- classifying

    fun classify(mask: BooleanArray): Maneuver? {
        val ink = mask.count { it }
        if (ink < 12) return null
        roundabout(mask)?.let { return it }
        val probe = blur(mask)
        var best: Maneuver? = null
        var bestScore = 0.0
        for ((m, t) in templates) {
            val s = cosine(probe, t)
            if (s > bestScore) {
                bestScore = s
                best = m
            }
        }
        return if (bestScore >= MIN_SCORE) best else null
    }

    private const val MIN_SCORE = 0.55

    /** Non-ink cells that cannot reach the border: the inside of a ring. */
    internal fun holes(mask: BooleanArray): BooleanArray {
        val reached = BooleanArray(N * N)
        val stack = ArrayDeque<Int>()
        for (i in 0 until N) {
            for (c in intArrayOf(i, (N - 1) * N + i, i * N, i * N + N - 1)) {
                if (!mask[c] && !reached[c]) {
                    reached[c] = true
                    stack.addLast(c)
                }
            }
        }
        while (stack.isNotEmpty()) {
            val c = stack.removeLast()
            val x = c % N
            val y = c / N
            val next = intArrayOf(
                if (x > 0) c - 1 else -1, if (x < N - 1) c + 1 else -1,
                if (y > 0) c - N else -1, if (y < N - 1) c + N else -1,
            )
            for (n in next) if (n >= 0 && !mask[n] && !reached[n]) {
                reached[n] = true
                stack.addLast(n)
            }
        }
        return BooleanArray(N * N) { !mask[it] && !reached[it] }
    }

    private fun roundabout(mask: BooleanArray): Maneuver? {
        val hole = holes(mask)
        val count = hole.count { it }
        if (count < 3) return null
        var hx = 0.0
        var hy = 0.0
        for (i in hole.indices) if (hole[i]) {
            hx += i % N
            hy += i / N
        }
        hx /= count
        hy /= count
        val r = sqrt(count / Math.PI)
        // Past the ring itself: the ring's stroke is about as thick as the hole is wide.
        val reach = max(r * 2.2 + 1.0, r + 4.0)
        var vx = 0.0
        var vy = 0.0
        var n = 0
        for (i in mask.indices) if (mask[i]) {
            val dx = (i % N) - hx
            val dy = hy - (i / N)          // y up
            if (hypot(dx, dy) < reach) continue
            val a = deg(dx, dy)
            if (a in 245.0..295.0) continue  // the road coming in from below
            vx += dx
            vy += dy
            n++
        }
        if (n == 0) return Maneuver.ROUNDABOUT
        return when (deg(vx, vy)) {
            in 60.0..120.0 -> Maneuver.ROUNDABOUT_STRAIGHT
            in 120.0..165.0 -> Maneuver.ROUNDABOUT_SLIGHT_LEFT
            in 165.0..215.0 -> Maneuver.ROUNDABOUT_LEFT
            in 215.0..245.0 -> Maneuver.ROUNDABOUT_SHARP_LEFT
            in 15.0..60.0 -> Maneuver.ROUNDABOUT_SLIGHT_RIGHT
            in 295.0..325.0 -> Maneuver.ROUNDABOUT_SHARP_RIGHT
            else -> Maneuver.ROUNDABOUT_RIGHT
        }
    }

    /** Angle in degrees, 0 = right, 90 = up, 0..360. */
    private fun deg(x: Double, y: Double): Double {
        val a = Math.toDegrees(atan2(y, x))
        return if (a < 0) a + 360 else a
    }

    private fun blur(mask: BooleanArray): DoubleArray {
        var a = DoubleArray(N * N) { if (mask[it]) 1.0 else 0.0 }
        repeat(2) {
            val b = DoubleArray(N * N)
            for (y in 0 until N) for (x in 0 until N) {
                var s = 0.0
                for (dy in -1..1) for (dx in -1..1) {
                    val xx = (x + dx).coerceIn(0, N - 1)
                    val yy = (y + dy).coerceIn(0, N - 1)
                    s += a[yy * N + xx]
                }
                b[y * N + x] = s / 9.0
            }
            a = b
        }
        return a
    }

    private fun cosine(a: DoubleArray, b: DoubleArray): Double {
        var ab = 0.0
        var aa = 0.0
        var bb = 0.0
        for (i in a.indices) {
            ab += a[i] * b[i]
            aa += a[i] * a[i]
            bb += b[i] * b[i]
        }
        return if (aa == 0.0 || bb == 0.0) 0.0 else ab / sqrt(aa * bb)
    }

    // -------------------------------------------------------------- drawing

    /** A point in the unit square, y pointing down like the icon. */
    data class P(val x: Float, val y: Float)

    /** Arrow shapes, drawn for left turns; right turns are their mirror images. */
    internal val LEFT_SHAPES: Map<Maneuver, List<P>> = mapOf(
        Maneuver.LEFT to listOf(P(0.58f, 0.92f), P(0.58f, 0.55f), P(0.54f, 0.44f), P(0.44f, 0.38f), P(0.32f, 0.38f)),
        Maneuver.SLIGHT_LEFT to listOf(P(0.60f, 0.92f), P(0.60f, 0.60f), P(0.38f, 0.30f)),
        Maneuver.SHARP_LEFT to listOf(P(0.62f, 0.92f), P(0.62f, 0.35f), P(0.56f, 0.25f), P(0.45f, 0.30f), P(0.32f, 0.50f)),
        Maneuver.UTURN_LEFT to listOf(
            P(0.68f, 0.92f), P(0.68f, 0.40f), P(0.62f, 0.26f), P(0.50f, 0.20f),
            P(0.38f, 0.26f), P(0.32f, 0.40f), P(0.32f, 0.52f),
        ),
    )
    internal val STRAIGHT_SHAPE = listOf(P(0.5f, 0.92f), P(0.5f, 0.32f))

    private val MIRROR = mapOf(
        Maneuver.LEFT to Maneuver.RIGHT,
        Maneuver.SLIGHT_LEFT to Maneuver.SLIGHT_RIGHT,
        Maneuver.SHARP_LEFT to Maneuver.SHARP_RIGHT,
        Maneuver.UTURN_LEFT to Maneuver.UTURN_RIGHT,
    )

    fun mirror(path: List<P>): List<P> = path.map { P(1f - it.x, it.y) }

    private val templates: List<Pair<Maneuver, DoubleArray>> by lazy {
        val out = ArrayList<Pair<Maneuver, DoubleArray>>()
        out += Maneuver.STRAIGHT to blur(draw(STRAIGHT_SHAPE))
        for ((m, path) in LEFT_SHAPES) {
            out += m to blur(draw(path))
            out += MIRROR.getValue(m) to blur(draw(mirror(path)))
        }
        out
    }

    /**
     * Draws a stroked path with an arrowhead at its end, optionally with a
     * ring (for roundabout test icons).
     */
    fun draw(
        path: List<P>,
        width: Float = 0.15f,
        headLength: Float = 0.22f,
        headWidth: Float = 0.42f,
        ring: Pair<P, Float>? = null,
    ): BooleanArray {
        val out = BooleanArray(N * N)
        val a = path[path.size - 2]
        val b = path.last()
        val len = hypot((b.x - a.x).toDouble(), (b.y - a.y).toDouble()).toFloat()
        val dx = (b.x - a.x) / len
        val dy = (b.y - a.y) / len
        val tip = P(b.x + dx * headLength, b.y + dy * headLength)
        val l = P(b.x - dy * headWidth / 2, b.y + dx * headWidth / 2)
        val r = P(b.x + dy * headWidth / 2, b.y - dx * headWidth / 2)
        for (gy in 0 until N) for (gx in 0 until N) {
            val p = P((gx + 0.5f) / N, (gy + 0.5f) / N)
            var ink = false
            for (i in 0 until path.size - 1) {
                if (segDist(p, path[i], path[i + 1]) <= width / 2) {
                    ink = true
                    break
                }
            }
            if (!ink) ink = inTriangle(p, tip, l, r)
            if (!ink && ring != null) {
                val d = hypot((p.x - ring.first.x).toDouble(), (p.y - ring.first.y).toDouble())
                ink = abs(d - ring.second) <= width / 2
            }
            out[gy * N + gx] = ink
        }
        return out
    }

    private fun segDist(p: P, a: P, b: P): Float {
        val vx = b.x - a.x
        val vy = b.y - a.y
        val t = (((p.x - a.x) * vx + (p.y - a.y) * vy) / (vx * vx + vy * vy)).coerceIn(0f, 1f)
        return hypot((p.x - (a.x + vx * t)).toDouble(), (p.y - (a.y + vy * t)).toDouble()).toFloat()
    }

    private fun inTriangle(p: P, a: P, b: P, c: P): Boolean {
        fun side(p1: P, p2: P, p3: P) = (p1.x - p3.x) * (p2.y - p3.y) - (p2.x - p3.x) * (p1.y - p3.y)
        val d1 = side(p, a, b)
        val d2 = side(p, b, c)
        val d3 = side(p, c, a)
        val neg = d1 < 0 || d2 < 0 || d3 < 0
        val pos = d1 > 0 || d2 > 0 || d3 > 0
        return !(neg && pos)
    }
}
