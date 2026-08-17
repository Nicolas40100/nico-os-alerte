package fr.nico.scouterdirect

data class CandidateBox(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
)

data class ConfirmationSnapshot(
    val confirmed: Boolean,
    val hits: Int,
)

class TargetConfirmationTracker(
    private val confirmHits: Int = 2,
    private val maxGapMs: Long = 900L,
    private val minIou: Float = 0.05f,
) {
    private var token: Long? = null
    private var hits = 0
    private var lastSeenMs = 0L
    private var lastBox: CandidateBox? = null

    fun update(searchToken: Long?, box: CandidateBox?, nowMs: Long): ConfirmationSnapshot {
        if (searchToken == null) {
            resetAll()
            return ConfirmationSnapshot(false, 0)
        }

        if (token != searchToken) {
            token = searchToken
            hits = 0
            lastSeenMs = 0L
            lastBox = null
        }

        if (box == null) {
            if (lastSeenMs > 0L && nowMs - lastSeenMs > maxGapMs) {
                hits = 0
                lastBox = null
                lastSeenMs = 0L
            }
            return ConfirmationSnapshot(false, hits)
        }

        val previous = lastBox
        val gapOk = lastSeenMs == 0L || nowMs - lastSeenMs <= maxGapMs
        val spatiallyConsistent = previous == null || iou(previous, box) >= minIou

        hits = if (gapOk && spatiallyConsistent) hits + 1 else 1
        lastBox = box
        lastSeenMs = nowMs

        return ConfirmationSnapshot(hits >= confirmHits, hits)
    }

    private fun resetAll() {
        token = null
        hits = 0
        lastSeenMs = 0L
        lastBox = null
    }

    private fun iou(a: CandidateBox, b: CandidateBox): Float {
        val left = maxOf(a.left, b.left)
        val top = maxOf(a.top, b.top)
        val right = minOf(a.right, b.right)
        val bottom = minOf(a.bottom, b.bottom)
        val iw = (right - left).coerceAtLeast(0f)
        val ih = (bottom - top).coerceAtLeast(0f)
        val intersection = iw * ih
        if (intersection <= 0f) return 0f

        val areaA = (a.right - a.left).coerceAtLeast(0f) * (a.bottom - a.top).coerceAtLeast(0f)
        val areaB = (b.right - b.left).coerceAtLeast(0f) * (b.bottom - b.top).coerceAtLeast(0f)
        val union = areaA + areaB - intersection
        return if (union > 0f) intersection / union else 0f
    }
}
