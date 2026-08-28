package fr.nico.scouterdirect

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FreeScanFilterTest {
    @Test
    fun filtersObservedParasiteLabels() {
        assertFalse(FreeScanFilter.accept("squeeze", 0.90f))
        assertFalse(FreeScanFilter.accept("remove", 0.90f))
        assertFalse(FreeScanFilter.accept("loop", 0.90f))
        assertFalse(FreeScanFilter.accept("interaction", 0.90f))
        assertFalse(FreeScanFilter.accept("floor", 0.90f))
    }

    @Test
    fun freeScanRequiresThirtyPercentConfidence() {
        assertFalse(FreeScanFilter.accept("shoe", 0.299f))
        assertTrue(FreeScanFilter.accept("shoe", 0.30f))
        assertTrue(FreeScanFilter.accept("printer", 0.55f))
    }

    @Test
    fun legitimateObjectClassesStayAvailable() {
        assertTrue(FreeScanFilter.accept("converter", 0.60f))
        assertTrue(FreeScanFilter.accept("mascara", 0.60f))
        assertTrue(FreeScanFilter.accept("cup", 0.60f))
    }
}
