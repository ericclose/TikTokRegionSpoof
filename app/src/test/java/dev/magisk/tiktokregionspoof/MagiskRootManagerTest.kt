package dev.magisk.tiktokregionspoof

import dev.magisk.tiktokregionspoof.root.MagiskRootManager
import org.junit.Assert.assertEquals
import org.junit.Test

class MagiskRootManagerTest {

    @Test
    fun formatSlotValue_handlesSingleAndDualSim() {
        assertEquals("us", MagiskRootManager.formatSlotValue("us", 1))
        assertEquals("us,us", MagiskRootManager.formatSlotValue("us", 2))
    }

    @Test
    fun shQuote_escapesSingleQuotesSafely() {
        assertEquals("'T-Mobile'", MagiskRootManager.shQuote("T-Mobile"))
        assertEquals("'AT&T'\\''s Net'", MagiskRootManager.shQuote("AT&T's Net"))
    }
}
