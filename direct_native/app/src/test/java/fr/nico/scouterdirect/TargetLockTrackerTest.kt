package fr.nico.scouterdirect

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TargetLockTrackerTest {
    @Test
    fun acquisitionStartsAtDetectionTime() {
        val tracker = TargetLockTracker(2000L)
        val snap = tracker.update(searchToken = 1L, found = true, nowMs = 5000L)
        assertTrue(snap.locked)
        assertTrue(snap.acquiredNow)
        assertEquals(5000L, snap.lockStartedMs)
    }

    @Test
    fun briefDropoutKeepsLock() {
        val tracker = TargetLockTracker(2000L)
        tracker.update(1L, true, 1000L)
        val snap = tracker.update(1L, false, 2500L)
        assertTrue(snap.locked)
        assertFalse(snap.acquiredNow)
    }

    @Test
    fun realLossReleasesLock() {
        val tracker = TargetLockTracker(2000L)
        tracker.update(1L, true, 1000L)
        val snap = tracker.update(1L, false, 3101L)
        assertFalse(snap.locked)
    }

    @Test
    fun changingSearchResetsPreviousLock() {
        val tracker = TargetLockTracker(2000L)
        tracker.update(1L, true, 1000L)
        val newSearch = tracker.update(2L, false, 1200L)
        assertFalse(newSearch.locked)
        val acquired = tracker.update(2L, true, 1300L)
        assertTrue(acquired.locked)
        assertTrue(acquired.acquiredNow)
        assertEquals(1300L, acquired.lockStartedMs)
    }
}
