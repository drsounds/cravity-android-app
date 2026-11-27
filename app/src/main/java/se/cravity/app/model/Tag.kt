package se.cravity.app.model

/**
 * Tags that can be attached to an intake entry. Each tag contributes foodmap points,
 * a stomach "load" (how much it fills you, 1.0 = an ordinary meal) and a stress factor.
 *
 * Mood tags (stress, anxiety) carry little load but slow digestion and make the gut more sensitive.
 */
enum class Tag(
    val key: String,
    val label: String,
    val points: Foodmap,
    val load: Double,
    val stress: Double = 0.0,
) {
    BREAKFAST("breakfast", "Breakfast", Foodmap(1.0, 2.0, 1.0), load = 0.8),
    MEAL("meal", "Meal", Foodmap(0.5, 2.0, 2.0), load = 1.0),
    SNACK("snack", "Snack", Foodmap(1.0, 1.0, 0.5), load = 0.35),
    SWEET("sweet", "Sweet", Foodmap(3.0, 0.5, 0.5), load = 0.3),
    FAST_FOOD("fast_food", "Fast food", Foodmap(1.0, 2.0, 3.0), load = 1.2),
    BINGE("binge", "Binge", Foodmap(2.0, 2.0, 2.0), load = 1.8),
    CELEBRATION("celebration", "Celebration", Foodmap(2.0, 1.0, 2.0), load = 1.3),
    STRESS("stress", "Stress", Foodmap(1.0, 0.0, 1.0), load = 0.0, stress = 1.0),
    ANXIETY("anxiety", "Anxiety", Foodmap(0.5, 0.0, 0.5), load = 0.0, stress = 1.0),
    SUPPLEMENT("supplement", "Supplement", Foodmap.ZERO, load = 0.05);

    companion object {
        fun fromKey(key: String): Tag? = entries.firstOrNull { it.key == key.trim() }
    }
}

enum class IntakeKind { MEAL, SUPPLEMENT }

/** A logged (or planned, if [timeMillis] is in the future) intake. */
data class Intake(
    val id: String,
    val timeMillis: Long,
    val kind: IntakeKind,
    val tags: Set<Tag>,
    val note: String = "",
) {
    /** Sum of the tags' foodmap points. */
    val foodmap: Foodmap get() = tags.fold(Foodmap.ZERO) { acc, t -> acc + t.points }

    /**
     * Stomach load. The biggest tag dominates and the others add a little on top,
     * so "breakfast + sweet" is a bit more than breakfast but not two meals.
     * An entry with only mood tags counts as a small snack.
     */
    val load: Double
        get() {
            val loads = tags.map { it.load }.filter { it > 0.0 }
            if (loads.isEmpty()) return if (kind == IntakeKind.SUPPLEMENT) 0.05 else 0.35
            val max = loads.max()
            return max + 0.2 * (loads.sum() - max)
        }

    val stress: Double get() = tags.maxOfOrNull { it.stress } ?: 0.0
}
