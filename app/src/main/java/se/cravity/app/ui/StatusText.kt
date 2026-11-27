package se.cravity.app.ui

import se.cravity.app.model.Digestion
import se.cravity.app.model.HungerClock
import se.cravity.app.model.HungerLevel
import se.cravity.app.model.Intake
import se.cravity.app.model.IntakeKind
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Short human-readable summaries shared by the app and the widget. */
object StatusText {
    private val fmt = DateTimeFormatter.ofPattern("HH:mm")
    private const val HORIZON = 24 * 3_600_000L

    fun time(millis: Long, zone: ZoneId = ZoneId.systemDefault()): String =
        fmt.format(Instant.ofEpochMilli(millis).atZone(zone))

    fun headline(intakes: List<Intake>, now: Long): String {
        val eaten = intakes.filter { it.kind == IntakeKind.MEAL && it.timeMillis <= now }
        if (eaten.isEmpty()) return "Log a meal to start the clock"
        return when (HungerClock.levelAt(intakes, now)) {
            HungerLevel.GREEN -> HungerClock.nextTimeOf(HungerLevel.ORANGE, intakes, now, HORIZON)
                ?.let { "Satisfied until ~${time(it)}" } ?: "Satisfied"
            HungerLevel.ORANGE -> HungerClock.nextTimeOf(HungerLevel.RED, intakes, now, HORIZON)
                ?.let { "Hunger building, eat by ~${time(it)}" } ?: "Hunger building"
            HungerLevel.RED -> "Hungry: time to eat"
        }
    }

    fun digestion(intakes: List<Intake>, now: Long): String? {
        val last = intakes.filter { it.kind == IntakeKind.MEAL && it.timeMillis <= now }.maxByOrNull { it.timeMillis }
            ?: return null
        val at = Digestion.processedAtMillis(last)
        return if (at > now) "Last meal processed by the stomach around ${time(at)}"
        else "Last meal processed by the stomach (around ${time(at)})"
    }
}
