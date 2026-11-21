package dev.bobble.widget

/**
 * One entry in the body catalog. Pure data (no Android types) so layout tests can use it.
 *
 * @param aspect body image width / height
 * @param neckPivotX neck pivot as a fraction of the body image width
 * @param neckPivotY neck pivot as a fraction of the body image height
 */
data class BodySpec(
    val id: String,
    val name: String,
    val aspect: Float,
    val neckPivotX: Float = FigureLayout.NECK_PIVOT_X,
    val neckPivotY: Float = FigureLayout.NECK_PIVOT_Y,
)

/** Premade bodies the user picks from, like an avatar selector. All are drawn in code. */
object BodyCatalog {
    const val STANDING_ASPECT = 0.62f
    const val SEATED_ASPECT = 0.9f

    val ALL: List<BodySpec> = listOf(
        BodySpec("cardigan", "Cardigan", STANDING_ASPECT),
        BodySpec("tee", "T-shirt", STANDING_ASPECT),
        BodySpec("suit", "Suit", STANDING_ASPECT),
        BodySpec("dress", "Dress", STANDING_ASPECT),
        BodySpec("hoodie", "Hoodie", STANDING_ASPECT),
        BodySpec("hoops", "Basketball", STANDING_ASPECT),
        BodySpec("skirt", "Skirt", STANDING_ASPECT),
        BodySpec("football", "Football", SEATED_ASPECT),
    )

    val DEFAULT: BodySpec = ALL.first()

    fun byId(id: String?): BodySpec = ALL.firstOrNull { it.id == id } ?: DEFAULT
}
