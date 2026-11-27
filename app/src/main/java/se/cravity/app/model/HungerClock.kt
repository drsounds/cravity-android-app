package se.cravity.app.model

enum class HungerLevel { GREEN, ORANGE, RED }

/** A contiguous span of time with the same hunger level. */
data class HungerZone(val startMillis: Long, val endMillis: Long, val level: HungerLevel)

/**
 * Turns stomach content over time into hunger zones:
 *
 * - GREEN: satisfied, plenty still in the stomach.
 * - ORANGE: hunger building, stomach getting low.
 * - RED: hunger imminent, stomach essentially empty.
 *
 * Logging a meal adds content, which pushes the zones forward: the clock "resets".
 */
object HungerClock {
    const val GREEN_ABOVE = 0.35
    const val ORANGE_ABOVE = 0.08
    private const val STEP_MILLIS = 5 * 60_000L

    fun levelFor(content: Double): HungerLevel = when {
        content > GREEN_ABOVE -> HungerLevel.GREEN
        content > ORANGE_ABOVE -> HungerLevel.ORANGE
        else -> HungerLevel.RED
    }

    fun levelAt(intakes: List<Intake>, atMillis: Long): HungerLevel =
        levelFor(Digestion.stomachContent(intakes, atMillis))

    /**
     * Zones covering [fromMillis, fromMillis + spanMillis), sampled every 5 minutes.
     * Planned (future) intakes are included, so a booked meal resets the clock at its time.
     */
    fun zones(intakes: List<Intake>, fromMillis: Long, spanMillis: Long): List<HungerZone> {
        val end = fromMillis + spanMillis
        val result = mutableListOf<HungerZone>()
        var zoneStart = fromMillis
        var current = levelAt(intakes, fromMillis)
        var t = fromMillis + STEP_MILLIS
        while (t < end) {
            val level = levelAt(intakes, t)
            if (level != current) {
                result += HungerZone(zoneStart, t, current)
                zoneStart = t
                current = level
            }
            t += STEP_MILLIS
        }
        result += HungerZone(zoneStart, end, current)
        return result
    }

    /** First time at or after [fromMillis] (within [horizonMillis]) when the level becomes [level], or null. */
    fun nextTimeOf(level: HungerLevel, intakes: List<Intake>, fromMillis: Long, horizonMillis: Long): Long? =
        zones(intakes, fromMillis, horizonMillis).firstOrNull { it.level == level }?.startMillis
}
