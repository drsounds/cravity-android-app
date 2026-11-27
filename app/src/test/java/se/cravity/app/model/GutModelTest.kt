package se.cravity.app.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GutModelTest {
    private val hour = 3_600_000L
    private val t0 = 1_700_000_000_000L
    private fun intake(vararg tags: Tag, at: Long = t0) = Intake("i$at", at, IntakeKind.MEAL, tags.toSet())

    @Test fun emptyLogGivesCalmOutlook() {
        val o = GutModel.outlookAt(emptyList(), t0)
        assertEquals(GutOutlook(0.0, 0.0, 0.0), o)
    }

    @Test fun outlookStaysWithinZeroAndOne() {
        val all = (0 until 10).map { intake(*Tag.entries.toTypedArray(), at = t0 - it * hour) }
        GutModel.forecast(all, t0, 12).forEach {
            listOf(it.outlook.sugarSwing, it.outlook.fermentation, it.outlook.heaviness).forEach { v ->
                assertTrue(v in 0.0..1.0)
            }
        }
    }

    @Test fun sweetsPeakEarlyAndFermentationLater() {
        val list = listOf(intake(Tag.SWEET, Tag.BREAKFAST))
        val f = GutModel.forecast(list, t0, 8)
        val sugarPeak = f.maxBy { it.outlook.sugarSwing }.hoursAhead
        val fermPeak = f.maxBy { it.outlook.fermentation }.hoursAhead
        assertTrue(sugarPeak <= 2)
        assertTrue(fermPeak in 3..6)
    }

    @Test fun fastFoodFeelsHeavierThanASweet() {
        val at = t0 + hour
        val heavy = GutModel.outlookAt(listOf(intake(Tag.FAST_FOOD)), at).heaviness
        val light = GutModel.outlookAt(listOf(intake(Tag.SWEET)), at).heaviness
        assertTrue(heavy > light)
    }

    @Test fun stressAmplifiesBloating() {
        val at = t0 + 4 * hour
        val calm = GutModel.outlookAt(listOf(intake(Tag.MEAL)), at).fermentation
        val stressed = GutModel.outlookAt(listOf(intake(Tag.MEAL, Tag.STRESS)), at).fermentation
        assertTrue(stressed > calm)
    }

    @Test fun recentFoodmapFadesWithAgeAndIgnoresFuture() {
        val now = intake(Tag.SWEET, at = t0)
        val old = intake(Tag.SWEET, at = t0 - 12 * hour)
        val ancient = intake(Tag.SWEET, at = t0 - 30 * hour)
        val future = intake(Tag.SWEET, at = t0 + hour)
        val fm = GutModel.recentFoodmap(listOf(now, old, ancient, future), t0)
        assertEquals(3.0 + 1.5, fm.sweetness, 1e-9)
    }
}
