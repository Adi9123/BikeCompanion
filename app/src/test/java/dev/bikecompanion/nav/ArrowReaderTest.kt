package dev.bikecompanion.nav

import dev.bikecompanion.nav.ArrowReader.P
import dev.bikecompanion.protocol.Maneuver
import dev.bikecompanion.protocol.NavParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ArrowReaderTest {

    private fun shift(path: List<P>, dx: Float, dy: Float) = path.map { P(it.x + dx, it.y + dy) }

    @Test
    fun everyArrowReadsAsItself() {
        assertEquals(Maneuver.STRAIGHT, ArrowReader.classify(ArrowReader.draw(ArrowReader.STRAIGHT_SHAPE)))
        for ((m, path) in ArrowReader.LEFT_SHAPES) {
            assertEquals(m, ArrowReader.classify(ArrowReader.draw(path)))
        }
    }

    @Test
    fun rightTurnsAreMirrors() {
        val left = ArrowReader.LEFT_SHAPES.getValue(Maneuver.LEFT)
        assertEquals(Maneuver.RIGHT, ArrowReader.classify(ArrowReader.draw(ArrowReader.mirror(left))))
        val u = ArrowReader.LEFT_SHAPES.getValue(Maneuver.UTURN_LEFT)
        assertEquals(Maneuver.UTURN_RIGHT, ArrowReader.classify(ArrowReader.draw(ArrowReader.mirror(u))))
    }

    /** A differently drawn arrow (thicker, shifted) must still point the right way. */
    @Test
    fun toleratesADifferentDrawingStyle() {
        val left = shift(ArrowReader.LEFT_SHAPES.getValue(Maneuver.LEFT), 0.03f, 0.02f)
        assertEquals(Maneuver.LEFT, ArrowReader.classify(ArrowReader.draw(left, width = 0.19f, headWidth = 0.36f)))
        val right = shift(ArrowReader.mirror(ArrowReader.LEFT_SHAPES.getValue(Maneuver.LEFT)), -0.03f, 0.02f)
        assertEquals(Maneuver.RIGHT, ArrowReader.classify(ArrowReader.draw(right, width = 0.12f)))
        val up = shift(ArrowReader.STRAIGHT_SHAPE, 0.02f, 0f)
        assertEquals(Maneuver.STRAIGHT, ArrowReader.classify(ArrowReader.draw(up, width = 0.2f)))
    }

    @Test
    fun roundaboutExits() {
        val ring = P(0.5f, 0.55f) to 0.17f
        val entry = listOf(P(0.5f, 0.98f), P(0.5f, 0.74f))
        fun icon(exit: List<P>): BooleanArray {
            val a = ArrowReader.draw(exit, width = 0.12f, headLength = 0.08f, headWidth = 0.2f, ring = ring)
            val b = ArrowReader.draw(entry, width = 0.12f, headLength = 0.0001f, headWidth = 0.0001f)
            return BooleanArray(a.size) { a[it] || b[it] }
        }
        assertEquals(Maneuver.ROUNDABOUT_LEFT, ArrowReader.classify(icon(listOf(P(0.33f, 0.55f), P(0.12f, 0.55f)))))
        assertEquals(Maneuver.ROUNDABOUT_RIGHT, ArrowReader.classify(icon(listOf(P(0.67f, 0.55f), P(0.88f, 0.55f)))))
        assertEquals(Maneuver.ROUNDABOUT_STRAIGHT, ArrowReader.classify(icon(listOf(P(0.5f, 0.38f), P(0.5f, 0.12f)))))
    }

    @Test
    fun emptyIconIsNotGuessed() {
        assertNull(ArrowReader.classify(BooleanArray(ArrowReader.N * ArrowReader.N)))
    }

    @Test
    fun maskFromTransparentAndOpaqueIcons() {
        val size = 64
        // white arrow on transparent background
        val shape = ArrowReader.draw(ArrowReader.LEFT_SHAPES.getValue(Maneuver.LEFT))
        fun pixels(ink: Int, bg: Int) = IntArray(size * size) { i ->
            val gx = (i % size) * ArrowReader.N / size
            val gy = (i / size) * ArrowReader.N / size
            if (shape[gy * ArrowReader.N + gx]) ink else bg
        }
        val transparent = ArrowReader.mask(size, size, pixels(0xFFFFFFFF.toInt(), 0x00000000))
        assertEquals(Maneuver.LEFT, ArrowReader.classify(transparent))
        val opaque = ArrowReader.mask(size, size, pixels(0xFFFFFFFF.toInt(), 0xFF1A73E8.toInt()))
        assertEquals(Maneuver.LEFT, ArrowReader.classify(opaque))
        assertEquals(ArrowReader.signature(transparent), ArrowReader.signature(opaque))
    }

    @Test
    fun signatureDiffersBetweenArrows() {
        val l = ArrowReader.draw(ArrowReader.LEFT_SHAPES.getValue(Maneuver.LEFT))
        val r = ArrowReader.draw(ArrowReader.mirror(ArrowReader.LEFT_SHAPES.getValue(Maneuver.LEFT)))
        assertNotEquals(ArrowReader.signature(l), ArrowReader.signature(r))
        assertEquals(12, ArrowReader.signature(l).length)
    }

    @Test
    fun textWithoutADirectionIsNotGuessed() {
        assertNull(NavParser.maneuverOf("toward Example Road"))
        assertNull(NavParser.maneuverOf("Example Bank Road"))
        // a street name that happens to contain a direction word
        assertNull(NavParser.maneuverOf("toward Leftbank Road"))
        assertEquals(Maneuver.LEFT, NavParser.maneuverOf("Left onto Example Road"))
        assertEquals(Maneuver.KEEP_LEFT, NavParser.maneuverOf("Take the exit on the left"))
    }
}
