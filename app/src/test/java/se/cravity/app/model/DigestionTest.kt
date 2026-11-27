package se.cravity.app.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DigestionTest {
    private val t0 = 1_700_000_000_000L
    private fun intake(vararg tags: Tag, at: Long = t0, kind: IntakeKind = IntakeKind.MEAL) =
        Intake("x", at, kind, tags.toSet())

    @Test fun retainedFractionIsOneAtStartAndHalfAtHalfTime() {
        val meal = intake(Tag.MEAL)
        assertEquals(1.0, Digestion.retainedFraction(meal, 0.0), 1e-9)
        val half = Digestion.halfEmptyingMinutes(meal)
        assertEquals(0.5, Digestion.retainedFraction(meal, half), 1e-9)
    }

    @Test fun notEatenYetMeansNothingInStomach() {
        assertEquals(0.0, Digestion.retainedFraction(intake(Tag.MEAL), -10.0), 0.0)
    }

    @Test fun retainedFractionDecreasesMonotonically() {
        val meal = intake(Tag.MEAL)
        var prev = 1.0
        for (m in 1..600 step 5) {
            val f = Digestion.retainedFraction(meal, m.toDouble())
            assertTrue(f <= prev)
            prev = f
        }
    }

    @Test fun ordinaryMealIsProcessedInRoughlyThreeToFiveHours() {
        val minutes = Digestion.minutesUntilFraction(intake(Tag.MEAL), Digestion.PROCESSED_FRACTION)
        assertTrue("was $minutes", minutes in 180.0..300.0)
        assertEquals(t0 + (minutes * 60_000).toLong(), Digestion.processedAtMillis(intake(Tag.MEAL)))
    }

    @Test fun minutesUntilFractionInvertsRetainedFraction() {
        val meal = intake(Tag.FAST_FOOD, Tag.STRESS)
        val t = Digestion.minutesUntilFraction(meal, 0.25)
        assertEquals(0.25, Digestion.retainedFraction(meal, t), 1e-9)
    }

    @Test fun fattyAndBigMealsTakeLongerThanSweets() {
        val sweet = Digestion.halfEmptyingMinutes(intake(Tag.SWEET))
        val meal = Digestion.halfEmptyingMinutes(intake(Tag.MEAL))
        val fast = Digestion.halfEmptyingMinutes(intake(Tag.FAST_FOOD))
        val binge = Digestion.halfEmptyingMinutes(intake(Tag.BINGE))
        assertTrue(sweet < meal)
        assertTrue(meal < fast)
        assertTrue(meal < binge)
    }

    @Test fun stressSlowsDigestion() {
        assertTrue(
            Digestion.halfEmptyingMinutes(intake(Tag.MEAL, Tag.STRESS)) >
                Digestion.halfEmptyingMinutes(intake(Tag.MEAL)),
        )
    }

    @Test fun halfTimeIsClamped() {
        val huge = intake(*Tag.entries.toTypedArray())
        assertTrue(Digestion.halfEmptyingMinutes(huge) <= Digestion.MAX_HALF_MINUTES)
        val supplement = intake(Tag.SUPPLEMENT, kind = IntakeKind.SUPPLEMENT)
        assertTrue(Digestion.halfEmptyingMinutes(supplement) >= Digestion.MIN_HALF_MINUTES)
    }

    @Test fun loadTakesBiggestTagPlusSmallExtra() {
        assertEquals(1.0, intake(Tag.MEAL).load, 1e-9)
        assertEquals(0.8 + 0.2 * 0.3, intake(Tag.BREAKFAST, Tag.SWEET).load, 1e-9)
        // Only mood tags still counts as a small snack
        assertEquals(0.35, intake(Tag.STRESS).load, 1e-9)
    }

    @Test fun foodmapSumsTagPoints() {
        val fm = intake(Tag.BREAKFAST, Tag.SWEET).foodmap
        assertEquals(Foodmap(4.0, 2.5, 1.5), fm)
    }
}
