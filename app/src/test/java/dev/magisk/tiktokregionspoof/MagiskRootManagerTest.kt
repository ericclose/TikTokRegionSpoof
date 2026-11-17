package dev.magisk.tiktokregionspoof

import dev.magisk.tiktokregionspoof.root.MagiskRootManager
import dev.magisk.tiktokregionspoof.root.ModuleStatus
import dev.magisk.tiktokregionspoof.root.RootImplementation
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

    @Test
    fun parseEnvironmentProbe_detectsKernelSuCorrectly() {
        val probe = ShellResult(
            exitCode = 0,
            stdout = """
                0
                KERNELSU
                v3.0.0
                yes
                ACTIVE
                gb,gb
                2
                dsds
            """.trimIndent(),
            stderr = ""
        )
        val env = MagiskRootManager.parseEnvironmentProbe(probe, emptyList())
        assertTrue(env.hasRoot)
        assertEquals(RootImplementation.KERNELSU, env.rootImplementation)
        assertEquals("v3.0.0", env.magiskVersion)
        assertTrue(env.hasResetprop)
        assertEquals(ModuleStatus.ACTIVE, env.moduleStatus)
        assertEquals(2, env.simSlotCount)
    }

    @Test
    fun parseEnvironmentProbe_detectsMagiskCorrectly() {
        val probe = ShellResult(
            exitCode = 0,
            stdout = """
                0
                MAGISK
                28.1:MAGISK
                yes
                NOT_INSTALLED
                us
                1
                
            """.trimIndent(),
            stderr = ""
        )
        val env = MagiskRootManager.parseEnvironmentProbe(probe, emptyList())
        assertTrue(env.hasRoot)
        assertEquals(RootImplementation.MAGISK, env.rootImplementation)
        assertEquals("28.1:MAGISK", env.magiskVersion)
        assertTrue(env.hasResetprop)
        assertEquals(ModuleStatus.NOT_INSTALLED, env.moduleStatus)
        assertEquals(1, env.simSlotCount)
    }

    @Test
    fun parseEnvironmentProbe_handlesDeniedRoot() {
        val probe = ShellResult(-1, "", "Cannot run program \"su\"")
        val env = MagiskRootManager.parseEnvironmentProbe(probe, emptyList())
        assertFalse(env.hasRoot)
        assertEquals(RootImplementation.NONE, env.rootImplementation)
        assertEquals("Unavailable", env.magiskVersion)
    }
}
