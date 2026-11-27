package se.cravity.app.model

import kotlin.math.exp

/**
 * A rough three-axis outlook for how recent intake might feel in the gut. Each axis is 0..1.
 *
 * - [sugarSwing]: blood-sugar rise and the energy dip that may follow. Driven by sweetness,
 *   damped by fat. Peaks within the first hour or two.
 * - [fermentation]: gas and bloating as starches and sugars reach the gut. Driven by grain and
 *   sweetness. Builds slowly and peaks around 3 to 6 hours.
 * - [heaviness]: fullness, sluggishness and reflux risk. Follows what is still in the stomach,
 *   amplified by fat.
 *
 * Stress and anxiety make the gut more sensitive and scale fermentation and heaviness up.
 *
 * ROUGH ESTIMATE ONLY, NOT MEDICAL ADVICE.
 */
data class GutOutlook(val sugarSwing: Double, val fermentation: Double, val heaviness: Double)

data class GutForecastPoint(val hoursAhead: Int, val outlook: GutOutlook)

object GutModel {

    /**
     * Linear map from foodmap (sweetness, grain, fat) to raw gut pressure (sugarSwing, fermentation, heaviness).
     * Rows are output axes, columns are foodmap dimensions.
     */
    val MATRIX = arrayOf(
        doubleArrayOf(1.0, 0.35, -0.3), // sugar swing
        doubleArrayOf(0.4, 0.8, 0.1),   // fermentation
        doubleArrayOf(0.1, 0.3, 0.9),   // heaviness
    )

    /** Time profile (0..1) of each axis, [hours] after eating. */
    fun sugarProfile(hours: Double): Double = if (hours < 0) 0.0 else bump(hours, peak = 1.0, width = 1.2)
    fun fermentationProfile(hours: Double): Double = if (hours < 0) 0.0 else bump(hours, peak = 4.5, width = 2.5)

    private fun bump(x: Double, peak: Double, width: Double): Double {
        val d = (x - peak) / width
        return exp(-d * d)
    }

    /** Saturating squash so many points approach but never exceed 1. */
    fun squash(x: Double): Double = if (x <= 0) 0.0 else 1.0 - exp(-x / 4.0)

    fun outlookAt(intakes: List<Intake>, atMillis: Long): GutOutlook {
        var sugar = 0.0
        var ferm = 0.0
        var heavy = 0.0
        for (i in intakes) {
            val hours = (atMillis - i.timeMillis) / 3_600_000.0
            if (hours < 0 || hours > 24) continue
            val f = i.foodmap
            val v = doubleArrayOf(f.sweetness, f.grain, f.fat)
            fun row(r: Int) = (0..2).sumOf { MATRIX[r][it] * v[it] }.coerceAtLeast(0.0)
            val sensitivity = 1.0 + 0.5 * i.stress
            sugar += row(0) * sugarProfile(hours)
            ferm += row(1) * fermentationProfile(hours) * sensitivity
            val retained = Digestion.retainedFraction(i, hours * 60)
            heavy += (row(2) + 2.0 * i.load) * retained * sensitivity
        }
        return GutOutlook(squash(sugar), squash(ferm), squash(heavy))
    }

    /** Hourly outlook for the next [hours] hours, starting now (hour 0). */
    fun forecast(intakes: List<Intake>, nowMillis: Long, hours: Int = 8): List<GutForecastPoint> =
        (0..hours).map { h -> GutForecastPoint(h, outlookAt(intakes, nowMillis + h * 3_600_000L)) }

    /**
     * Current foodmap: points from the last [windowHours] hours, fading linearly with age.
     */
    fun recentFoodmap(intakes: List<Intake>, nowMillis: Long, windowHours: Double = 24.0): Foodmap =
        intakes.fold(Foodmap.ZERO) { acc, i ->
            val age = (nowMillis - i.timeMillis) / 3_600_000.0
            if (age < 0 || age > windowHours) acc else acc + i.foodmap * (1.0 - age / windowHours)
        }
}
