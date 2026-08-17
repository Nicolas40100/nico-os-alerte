package fr.nico.scouterdirect

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TargetConfirmationTrackerTest {
    private val box = CandidateBox(10f, 10f, 110f, 110f)

    @Test
    fun oneWeakObservationDoesNotConfirm() {
        val tracker = TargetConfirmationTracker(confirmHits = 2, maxGapMs = 900L, minIou = 0.05f)
        assertFalse(tracker.update(1L, box, 1000L).confirmed)
    }

    @Test
    fun twoNearbyObservationsConfirm() {
        val tracker = TargetConfirmationTracker(confirmHits = 2, maxGapMs = 900L, minIou = 0.05f)
        assertFalse(tracker.update(1L, box, 1000L).confirmed)
        val shifted = CandidateBox(20f, 15f, 120f, 115f)
        assertTrue(tracker.update(1L, shifted, 1400L).confirmed)
    }

    @Test
    fun spatialJumpResetsConfirmation() {
        val tracker = TargetConfirmationTracker(confirmHits = 2, maxGapMs = 900L, minIou = 0.05f)
        assertFalse(tracker.update(1L, box, 1000L).confirmed)
        val far = CandidateBox(300f, 300f, 380f, 380f)
        assertFalse(tracker.update(1L, far, 1300L).confirmed)
    }

    @Test
    fun longGapResetsConfirmation() {
        val tracker = TargetConfirmationTracker(confirmHits = 2, maxGapMs = 900L, minIou = 0.05f)
        assertFalse(tracker.update(1L, box, 1000L).confirmed)
        assertFalse(tracker.update(1L, box, 2500L).confirmed)
    }

    @Test
    fun newSearchTokenResetsConfirmation() {
        val tracker = TargetConfirmationTracker(confirmHits = 2, maxGapMs = 900L, minIou = 0.05f)
        assertFalse(tracker.update(1L, box, 1000L).confirmed)
        assertFalse(tracker.update(2L, box, 1200L).confirmed)
    }
}
