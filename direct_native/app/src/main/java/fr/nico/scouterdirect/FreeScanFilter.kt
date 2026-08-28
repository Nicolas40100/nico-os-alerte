package fr.nico.scouterdirect

/**
 * Keeps free-scan output useful without touching target search.
 * Target matching still sees every detector candidate down to its own thresholds.
 */
object FreeScanFilter {
    const val MIN_SCORE = 0.30f

    private val blockedExact = setOf(
        "squeeze",
        "remove",
        "loop",
        "crap",
        "interaction",
        "flip",
        "leak",
        "scar",
        "floor",
        "kitchen floor",
        "wall",
        "ceiling",
        "background",
        "shadow",
        "reflection",
    )

    fun accept(detection: Detection): Boolean = accept(detection.label, detection.score)

    fun accept(label: String, score: Float): Boolean {
        if (score < MIN_SCORE) return false
        return SearchLogic.canonical(label) !in blockedExact
    }
}
