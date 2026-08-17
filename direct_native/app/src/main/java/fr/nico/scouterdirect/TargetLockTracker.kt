package fr.nico.scouterdirect

data class LockSnapshot(
    val locked: Boolean,
    val acquiredNow: Boolean,
    val lockStartedMs: Long,
    val lastSeenMs: Long,
)

class TargetLockTracker(private val lostGraceMs: Long = 2000L) {
    private var token: Long? = null
    private var locked = false
    private var lockStartedMs = 0L
    private var lastSeenMs = 0L

    fun update(searchToken: Long?, found: Boolean, nowMs: Long): LockSnapshot {
        if (searchToken == null) {
            resetAll()
            return snapshot(acquiredNow = false)
        }

        if (token != searchToken) {
            token = searchToken
            locked = false
            lockStartedMs = 0L
            lastSeenMs = 0L
        }

        var acquiredNow = false
        if (found) {
            if (!locked) {
                locked = true
                lockStartedMs = nowMs
                acquiredNow = true
            }
            lastSeenMs = nowMs
        } else if (locked && nowMs - lastSeenMs > lostGraceMs) {
            locked = false
            lockStartedMs = 0L
        }

        return snapshot(acquiredNow)
    }

    private fun resetAll() {
        token = null
        locked = false
        lockStartedMs = 0L
        lastSeenMs = 0L
    }

    private fun snapshot(acquiredNow: Boolean) = LockSnapshot(
        locked = locked,
        acquiredNow = acquiredNow,
        lockStartedMs = lockStartedMs,
        lastSeenMs = lastSeenMs,
    )
}
