package se.cravity.app.model

import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * A rough gastric-emptying estimate using the power-exponential model often used in
 * gastric emptying studies: retained fraction f(t) = 2^-((t / tHalf)^beta).
 *
 * The half-emptying time grows with the size of the meal (sub-linearly), with fat
 * and with stress, and shrinks a bit for sugary, liquid-ish intake. These are
 * coarse heuristics for a personal tracker, not a physiological model.
 */
object Digestion {
    const val BETA = 1.6
    /** Half-emptying time of an ordinary mixed meal (load 1.0), in minutes. */
    const val BASE_HALF_MINUTES = 90.0
    /** Fraction left in the stomach at which we call a meal "processed". */
    const val PROCESSED_FRACTION = 0.1
    const val MIN_HALF_MINUTES = 15.0
    const val MAX_HALF_MINUTES = 300.0

    fun halfEmptyingMinutes(intake: Intake): Double {
        val load = intake.load
        if (load <= 0.0) return MIN_HALF_MINUTES
        val fm = intake.foodmap
        val total = fm.sweetness + fm.grain + fm.fat
        val fatShare = if (total > 0) fm.fat / total else 0.0
        val sweetShare = if (total > 0) fm.sweetness / total else 0.0
        val sizeFactor = sqrt(load)
        val fatFactor = 1.0 + 0.6 * fatShare
        val sweetFactor = 1.0 - 0.25 * sweetShare
        val stressFactor = 1.0 + 0.2 * intake.stress
        return (BASE_HALF_MINUTES * sizeFactor * fatFactor * sweetFactor * stressFactor)
            .coerceIn(MIN_HALF_MINUTES, MAX_HALF_MINUTES)
    }

    /** Fraction of the intake still in the stomach [minutesAfter] minutes after eating. */
    fun retainedFraction(intake: Intake, minutesAfter: Double): Double {
        if (minutesAfter < 0) return 0.0 // not eaten yet
        val tHalf = halfEmptyingMinutes(intake)
        return 2.0.pow(-((minutesAfter / tHalf).pow(BETA)))
    }

    /** Minutes after eating until only [fraction] remains. */
    fun minutesUntilFraction(intake: Intake, fraction: Double): Double {
        require(fraction in 0.0..1.0 && fraction > 0.0)
        val tHalf = halfEmptyingMinutes(intake)
        // fraction = 2^-((t/tHalf)^beta)  =>  t = tHalf * (log2(1/fraction))^(1/beta)
        val log2 = ln(1.0 / fraction) / ln(2.0)
        return tHalf * log2.pow(1.0 / BETA)
    }

    /** Estimated wall-clock time (epoch millis) at which the intake is processed by the stomach. */
    fun processedAtMillis(intake: Intake): Long =
        intake.timeMillis + (minutesUntilFraction(intake, PROCESSED_FRACTION) * 60_000).toLong()

    /** Total stomach content (sum of loads still retained) at [atMillis]. */
    fun stomachContent(intakes: List<Intake>, atMillis: Long): Double =
        intakes.sumOf { it.load * retainedFraction(it, (atMillis - it.timeMillis) / 60_000.0) }

}
