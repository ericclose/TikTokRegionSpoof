package dev.magisk.tiktokregionspoof

import dev.magisk.tiktokregionspoof.root.MagiskRootManager
import dev.magisk.tiktokregionspoof.root.ShellResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MagiskRootManagerTest {

    @Test
    fun formatSlotValue_handlesSingleAndDualSim() {
        assertEquals("us", MagiskRootManager.formatSlotValue("us", 1))
        assertEquals("us,us", MagiskRootManager.formatSlotValue("us", 2))
    }

    @Test
    fun shQuote_escapesSingleQuotesAndMetacharactersSafely() {
        assertEquals("'T-Mobile'", MagiskRootManager.shQuote("T-Mobile"))
        assertEquals("'AT&T'\\''s Net'", MagiskRootManager.shQuote("AT&T's Net"))
        assertEquals("'a; rm -rf /'", MagiskRootManager.shQuote("a; rm -rf /"))
    }

    @Test
    fun shellResult_isSuccessOnlyOnZeroExitCode() {
        assertTrue(ShellResult(0, "ok", "").isSuccess)
        assertFalse(ShellResult(1, "", "denied").isSuccess)
        assertFalse(ShellResult(-1, "", "error").isSuccess)
    }
}
