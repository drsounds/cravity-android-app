package se.cravity.app.model

import java.time.Instant
import java.time.ZoneId

enum class ClockMode(val hours: Int) { H12(12), H24(24) }

/** Everything the clock face needs to draw, computed from the log. */
data class ClockFace(
    val nowMillis: Long,
    val mode: ClockMode,
    val zones: List<HungerZone>,
    val mealTimes: List<Long>,
    val processedAtMillis: Long?,
    val zone: ZoneId,
) {
    /** Dial angle in degrees, clockwise from 12 (or 0/24) o'clock at the top. */
    fun angleOf(millis: Long): Float {
        val t = Instant.ofEpochMilli(millis).atZone(zone)
        val hours = t.hour + t.minute / 60.0 + t.second / 3600.0
        return ((hours % mode.hours) / mode.hours * 360.0).toFloat()
    }

    val spanMillis: Long get() = mode.hours * 3_600_000L

    companion object {
        /**
         * Builds the face: hunger zones from now and one full turn of the dial ahead,
         * markers for meals inside that window, and when the latest eaten meal is processed.
         */
        fun build(intakes: List<Intake>, nowMillis: Long, mode: ClockMode, zone: ZoneId = ZoneId.systemDefault()): ClockFace {
            val span = mode.hours * 3_600_000L
            val zones = HungerClock.zones(intakes, nowMillis, span)
            val meals = intakes.filter { it.kind == IntakeKind.MEAL }
            val markers = meals.map { it.timeMillis }.filter { it in nowMillis until nowMillis + span }
            val lastEaten = meals.filter { it.timeMillis <= nowMillis }.maxByOrNull { it.timeMillis }
            val processed = lastEaten?.let { Digestion.processedAtMillis(it) }?.takeIf { it > nowMillis }
            return ClockFace(nowMillis, mode, zones, markers, processed, zone)
        }
    }
}
