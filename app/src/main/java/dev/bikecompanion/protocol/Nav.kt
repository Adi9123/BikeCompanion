package dev.bikecompanion.protocol

import java.util.Locale
import kotlin.math.roundToInt

/**
 * Turn types, each with the arrow code the Apache cluster draws for it.
 *
 * Codes follow the official app's Apache icon table. [side] says which way the
 * turn goes (-1 left, +1 right), which the cluster uses for its "turn now"
 * prompt. Roundabout codes assume clockwise roundabouts (left-hand traffic,
 * as in India). Every code can be checked on the bike with the icon tester.
 */
enum class Maneuver(val code: Int, val label: String, val side: Int = 0) {
    STRAIGHT(87, "Straight"),
    DEPART(33, "Head"),
    ARRIVE(34, "Arrive"),
    LEFT(0, "Turn left", -1),
    SHARP_LEFT(1, "Sharp left", -1),
    SLIGHT_LEFT(2, "Slight left", -1),
    KEEP_LEFT(7, "Keep left", -1),
    MERGE_LEFT(8, "Merge left", -1),
    UTURN_LEFT(9, "U-turn left", -1),
    RIGHT(16, "Turn right", 1),
    SHARP_RIGHT(17, "Sharp right", 1),
    SLIGHT_RIGHT(18, "Slight right", 1),
    KEEP_RIGHT(23, "Keep right", 1),
    MERGE_RIGHT(24, "Merge right", 1),
    UTURN_RIGHT(25, "U-turn", 1),
    MERGE(103, "Merge"),
    FERRY(35, "Ferry"),
    ROUNDABOUT(71, "Roundabout"),
    ROUNDABOUT_STRAIGHT(72, "Roundabout straight"),
    ROUNDABOUT_LEFT(40, "Roundabout left", -1),
    ROUNDABOUT_SLIGHT_LEFT(41, "Roundabout slight left", -1),
    ROUNDABOUT_SHARP_LEFT(39, "Roundabout sharp left", -1),
    ROUNDABOUT_RIGHT(50, "Roundabout right", 1),
    ROUNDABOUT_SLIGHT_RIGHT(49, "Roundabout slight right", 1),
    ROUNDABOUT_SHARP_RIGHT(51, "Roundabout sharp right", 1),
    ROUNDABOUT_UTURN(96, "Roundabout U-turn"),
    UNKNOWN(100, "Continue"),
}

/** One navigation step, independent of which maps app produced it. */
data class NavInstruction(
    val maneuver: Maneuver,
    val distanceToTurnM: Int?,
    val remainingM: Int?,
    val remainingMin: Int?,
    /** Instruction text without the distance, e.g. "Turn left onto Example Road". */
    val text: String,
    val source: String = "Google Maps",
    /** Test only: send this arrow code instead of the maneuver's. */
    val iconOverride: Int? = null,
)

/**
 * Turns the text of a maps navigation notification into a [NavInstruction].
 *
 * Maps apps do not expose the maneuver as data, only as text and a picture, so
 * this reads the English text. The notification usually has one line with the
 * distance to the next turn and the instruction, and one summary line with the
 * time and distance left (it contains "min", "hr" or "ETA").
 */
object NavParser {
    private val distRe = Regex("""(\d+(?:[.,]\d+)?)\s*(km|m|mi|ft|yd)\b""", RegexOption.IGNORE_CASE)
    private val hrRe = Regex("""(\d+)\s*(?:hr|hrs|h|hour|hours)\b""", RegexOption.IGNORE_CASE)
    private val minRe = Regex("""(\d+)\s*(?:min|mins|minute|minutes)\b""", RegexOption.IGNORE_CASE)

    fun meters(value: String, unit: String): Int {
        val v = value.replace(',', '.').toDoubleOrNull() ?: return 0
        val m = when (unit.lowercase(Locale.US)) {
            "km" -> v * 1000.0
            "mi" -> v * 1609.344
            "ft" -> v * 0.3048
            "yd" -> v * 0.9144
            else -> v
        }
        return m.roundToInt()
    }

    private fun isSummary(s: String): Boolean =
        minRe.containsMatchIn(s) || hrRe.containsMatchIn(s) ||
            s.contains("ETA", ignoreCase = true) || s.contains("arrival", ignoreCase = true)

    fun parse(texts: List<String>): NavInstruction? {
        val clean = texts.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        if (clean.isEmpty()) return null
        val summary = clean.firstOrNull { isSummary(it) }
        val others = clean.filter { it != summary }

        var toTurn: Int? = null
        for (s in others) {
            val m = distRe.find(s)
            if (m != null) {
                toTurn = meters(m.groupValues[1], m.groupValues[2])
                break
            }
        }

        val instruction = others.firstOrNull { maneuverOf(it) != null }
            ?: others.firstOrNull { distRe.replace(it, "").isNotBlank() }
            ?: ""
        val maneuver = maneuverOf(instruction) ?: Maneuver.UNKNOWN

        var remM: Int? = null
        var remMin: Int? = null
        if (summary != null) {
            distRe.find(summary)?.let { remM = meters(it.groupValues[1], it.groupValues[2]) }
            val h = hrRe.find(summary)?.groupValues?.get(1)?.toIntOrNull() ?: 0
            val m = minRe.find(summary)?.groupValues?.get(1)?.toIntOrNull()
            if (m != null || h > 0) remMin = h * 60 + (m ?: 0)
        }

        if (toTurn == null && maneuver == Maneuver.UNKNOWN) return null
        val text = distRe.replace(instruction, "")
            .replace("·", " ")
            .replace(Regex("\\s+"), " ")
            .trim(' ', ',', '-', '–')
        return NavInstruction(maneuver, toTurn, remM, remMin, text)
    }

    /**
     * The turn named in an instruction, or null when the text does not name
     * one (Maps often writes only "toward Example Road" and shows the turn as
     * a picture; [dev.bikecompanion.nav.ArrowReader] handles that case).
     * Words are matched whole, so a street called "Left Bank Road" is not a
     * left turn.
     */
    fun maneuverOf(raw: String): Maneuver? {
        val s = raw.lowercase(Locale.US).replace('‑', '-')
        fun has(vararg w: String) = w.any { Regex("""\b""" + Regex.escape(it) + """\b""").containsMatchIn(s) }
        val left = has("left")
        val right = has("right")
        return when {
            s.contains("arrive") || s.contains("arriving") || has("destination") -> Maneuver.ARRIVE
            has("roundabout", "rotary", "traffic circle") -> when {
                has("u-turn", "u turn") -> Maneuver.ROUNDABOUT_UTURN
                has("sharp left") -> Maneuver.ROUNDABOUT_SHARP_LEFT
                has("sharp right") -> Maneuver.ROUNDABOUT_SHARP_RIGHT
                has("slight left") -> Maneuver.ROUNDABOUT_SLIGHT_LEFT
                has("slight right") -> Maneuver.ROUNDABOUT_SLIGHT_RIGHT
                has("1st exit", "first exit") -> Maneuver.ROUNDABOUT_LEFT
                has("2nd exit", "second exit") -> Maneuver.ROUNDABOUT_STRAIGHT
                has("3rd exit", "third exit") -> Maneuver.ROUNDABOUT_RIGHT
                has("4th exit", "fourth exit") -> Maneuver.ROUNDABOUT_UTURN
                has("turn left", "exit left") -> Maneuver.ROUNDABOUT_LEFT
                has("turn right", "exit right") -> Maneuver.ROUNDABOUT_RIGHT
                has("straight") -> Maneuver.ROUNDABOUT_STRAIGHT
                else -> Maneuver.ROUNDABOUT
            }
            has("u-turn", "u turn", "make a u") -> if (left) Maneuver.UTURN_LEFT else Maneuver.UTURN_RIGHT
            has("sharp left") -> Maneuver.SHARP_LEFT
            has("sharp right") -> Maneuver.SHARP_RIGHT
            has("slight left", "bear left") -> Maneuver.SLIGHT_LEFT
            has("slight right", "bear right") -> Maneuver.SLIGHT_RIGHT
            has("keep left", "stay left", "fork left") -> Maneuver.KEEP_LEFT
            has("keep right", "stay right", "fork right") -> Maneuver.KEEP_RIGHT
            has("merge") -> when {
                left -> Maneuver.MERGE_LEFT
                right -> Maneuver.MERGE_RIGHT
                else -> Maneuver.MERGE
            }
            has("ferry") -> Maneuver.FERRY
            has("turn left") -> Maneuver.LEFT
            has("turn right") -> Maneuver.RIGHT
            has("exit", "ramp") && (left || right) -> if (left) Maneuver.KEEP_LEFT else Maneuver.KEEP_RIGHT
            s.startsWith("head") -> Maneuver.DEPART
            has("continue", "straight", "go straight") -> Maneuver.STRAIGHT
            Regex("""^(go |turn )?left\b""").containsMatchIn(s) -> Maneuver.LEFT
            Regex("""^(go |turn )?right\b""").containsMatchIn(s) -> Maneuver.RIGHT
            else -> null
        }
    }
}

/**
 * Builds the two navigation frames for the Apache cluster: the control frame
 * (arrow, distances, prompt) and the instruction text line.
 *
 * The rules mirror what the official app does for this cluster family:
 * distance to the turn is rounded to 50 m, the status byte is 0xFF while
 * navigating, and the prompt byte selects the cluster's own built-in message
 * (1 = turn coming up, 3/4 = turn left/right now, 5 = go straight for a
 * while, 8 = arrived).
 */
object NavFrames {
    const val STATUS_ACTIVE = 0xFF
    const val ARRIVED_CODE = 34

    fun roundTo50(m: Int): Int = ((m + 25) / 50) * 50

    fun build(n: NavInstruction): Pair<ByteArray, ByteArray> {
        val dist = (n.distanceToTurnM ?: 0).coerceAtLeast(0)
        val remaining = n.remainingM
        val override = n.iconOverride
        val arrived = n.maneuver == Maneuver.ARRIVE || (remaining != null && remaining <= 50)
        val picto: Int
        val prefix: Int
        val text: String
        when {
            override != null -> {
                picto = override
                prefix = 1
                text = n.text
            }
            arrived -> {
                picto = ARRIVED_CODE
                prefix = 8
                text = "Arrived"
            }
            dist < 1000 -> {
                picto = n.maneuver.code
                prefix = if (dist < 65) {
                    when (n.maneuver.side) {
                        -1 -> 3
                        1 -> 4
                        else -> 0
                    }
                } else {
                    1
                }
                text = n.text.ifBlank { n.maneuver.label }
            }
            else -> {
                picto = n.maneuver.code
                prefix = 5
                text = n.text.ifBlank { n.maneuver.label }
            }
        }
        val control = Outbound.navControl(
            distanceM = roundTo50(dist),
            remainingMin = n.remainingMin ?: 0,
            remainingM = remaining ?: 0,
            picto = picto,
            strings = if (text.length > Outbound.TEXT_MAX) 2 else 1,
            status = STATUS_ACTIVE,
            alert = 0,
            prefix = prefix,
        )
        return control to Outbound.navText(text)
    }
}
