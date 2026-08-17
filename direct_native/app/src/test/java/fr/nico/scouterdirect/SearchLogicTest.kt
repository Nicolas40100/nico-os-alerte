package fr.nico.scouterdirect

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchLogicTest {
    @Test
    fun telecommandeMatchesRemoteLabels() {
        val spec = SearchLogic.buildSpec(1L, "télécommande")
        assertNotNull(spec)
        assertTrue(SearchLogic.matches("remote control", spec!!))
        assertTrue(SearchLogic.matches("remote controller", spec))
    }

    @Test
    fun descriptiveShoesPhraseStillFindsShoes() {
        val spec = SearchLogic.buildSpec(2L, "chaussures blanches")!!
        assertTrue(SearchLogic.matches("shoe", spec))
        assertTrue(SearchLogic.matches("sneakers", spec))
    }

    @Test
    fun tableEnVerrePrioritizesTableWord() {
        val spec = SearchLogic.buildSpec(3L, "table en verre")!!
        assertTrue(SearchLogic.matches("table", spec))
        assertFalse(SearchLogic.matches("drinking glass", spec))
    }

    @Test
    fun commonBrocanteCameraPhraseWorks() {
        val spec = SearchLogic.buildSpec(4L, "appareil photo")!!
        assertTrue(SearchLogic.matches("camera", spec))
        assertTrue(SearchLogic.matches("digital camera", spec))
    }

    @Test
    fun englishInputStillWorks() {
        val spec = SearchLogic.buildSpec(5L, "remote control")!!
        assertTrue(SearchLogic.matches("remote control", spec))
    }
}
