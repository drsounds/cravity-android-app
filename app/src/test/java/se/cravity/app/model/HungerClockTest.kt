package se.cravity.app.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneOffset

class HungerClockTest {
    private val hour = 3_600_000L
    private val t0 = 1_700_000_000_000L - (1_700_000_000_000L % hour) // aligned to an hour

    private fun meal(at: Long, vararg tags: Tag = arrayOf(Tag.MEAL)) =
        Intake("m$at", at, IntakeKind.MEAL, tags.toSet())

    @Test fun noFoodMeansRedAllAround() {
        val zones = HungerClock.zones(emptyList(), t0, 12 * hour)
        assertEquals(listOf(HungerLevel.RED), zones.map { it.level })
    }

    @Test fun afterAMealZonesGoGreenOrangeRed() {
        val zones = HungerClock.zones(listOf(meal(t0)), t0, 12 * hour)
        assertEquals(listOf(HungerLevel.GREEN, HungerLevel.ORANGE, HungerLevel.RED), zones.map { it.level })
        // zones are contiguous and cover the whole span
        assertEquals(t0, zones.first().startMillis)
        assertEquals(t0 + 12 * hour, zones.last().endMillis)
        zones.zipWithNext().forEach { (a, b) -> assertEquals(a.endMillis, b.startMillis) }
        // satisfied for a couple of hours, hungry within ~5 h
        val orange = zones[1].startMillis - t0
        val red = zones[2].startMillis - t0
        assertTrue(orange in 2 * hour..4 * hour)
        assertTrue(red in 3 * hour..6 * hour)
    }

    @Test fun loggingAMealResetsTheClock() {
        val first = meal(t0)
        val now = t0 + 5 * hour
        assertEquals(HungerLevel.RED, HungerClock.levelAt(listOf(first), now))
        val second = meal(now)
        assertEquals(HungerLevel.GREEN, HungerClock.levelAt(listOf(first, second), now))
    }

    @Test fun plannedMealTurnsZoneGreenAtItsTime() {
        val planned = meal(t0 + 2 * hour)
        val zones = HungerClock.zones(listOf(planned), t0, 12 * hour)
        assertEquals(HungerLevel.RED, zones.first().level)
        val green = zones.first { it.level == HungerLevel.GREEN }
        assertEquals(t0 + 2 * hour, green.startMillis)
    }

    @Test fun biggerMealsKeepYouSatisfiedLonger() {
        val normal = HungerClock.nextTimeOf(HungerLevel.RED, listOf(meal(t0)), t0, 24 * hour)
        val binge = HungerClock.nextTimeOf(HungerLevel.RED, listOf(meal(t0, Tag.BINGE)), t0, 24 * hour)
        assertNotNull(normal)
        assertNotNull(binge)
        assertTrue(binge!! > normal!!)
    }

    @Test fun clockFaceAnglesFor12And24HourModes() {
        val utc = ZoneOffset.UTC
        val threePm = java.time.LocalDate.of(2026, 1, 1).atTime(15, 0).toInstant(utc).toEpochMilli()
        val f12 = ClockFace.build(emptyList(), threePm, ClockMode.H12, utc)
        val f24 = ClockFace.build(emptyList(), threePm, ClockMode.H24, utc)
        assertEquals(90f, f12.angleOf(threePm), 1e-3f)
        assertEquals(225f, f24.angleOf(threePm), 1e-3f)
        assertEquals(12 * hour, f12.spanMillis)
        assertEquals(24 * hour, f24.spanMillis)
    }

    @Test fun clockFaceShowsPlannedMealsAndProcessedTime() {
        val eaten = meal(t0 - hour)
        val planned = meal(t0 + 3 * hour)
        val face = ClockFace.build(listOf(eaten, planned), t0, ClockMode.H12, ZoneOffset.UTC)
        assertEquals(listOf(t0 + 3 * hour), face.mealTimes)
        assertEquals(Digestion.processedAtMillis(eaten), face.processedAtMillis)
    }
}
