package se.cravity.app.model

import kotlin.math.sqrt

/**
 * The three-dimensional "foodmap" vector.
 *
 * - [sweetness]: sugars and sweet treats; fast to absorb, gives quick energy and a later dip.
 * - [grain]: starches and grains (bread, pasta, rice, cereals); also a proxy for fermentable fibre.
 * - [fat]: fat and richness (fried food, cream, cheese, meat). Chosen as the third dimension because
 *   fat is the single biggest dietary factor that slows how fast the stomach empties, so it
 *   connects the log directly to the digestion estimate.
 *
 * Values are unitless "points", not grams or calories.
 */
data class Foodmap(
    val sweetness: Double = 0.0,
    val grain: Double = 0.0,
    val fat: Double = 0.0,
) {
    operator fun plus(o: Foodmap) = Foodmap(sweetness + o.sweetness, grain + o.grain, fat + o.fat)
    operator fun times(k: Double) = Foodmap(sweetness * k, grain * k, fat * k)
    val magnitude: Double get() = sqrt(sweetness * sweetness + grain * grain + fat * fat)

    companion object {
        val ZERO = Foodmap()
    }
}
