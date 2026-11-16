package dev.magisk.tiktokregionspoof

import dev.magisk.tiktokregionspoof.model.CarrierProfile
import dev.magisk.tiktokregionspoof.model.RegionDefaults
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CarrierProfileTest {

    @Test
    fun flagEmoji_convertsIsoToRegionalIndicators() {
        val us = CarrierProfile("US", "United States", "310260", "T-Mobile", "T-Mobile US")
        assertEquals("🇺🇸", us.flagEmoji)

        val jp = CarrierProfile("jp", "Japan", "44010", "NTT DOCOMO", "NTT DOCOMO")
        assertEquals("🇯🇵", jp.flagEmoji)
    }

    @Test
    fun flagEmoji_invalidCodeFallsBackToWhiteFlag() {
        val invalid = CarrierProfile("USA", "Unknown", "00000", "Test", "Test")
        assertEquals("🏳️", invalid.flagEmoji)
    }

    @Test
    fun hintsForCountry_returnsMappedAndFallbackDefaults() {
        assertEquals("ja-JP" to "Asia/Tokyo", RegionDefaults.hintsForCountry("jp"))
        assertEquals("en-ZZ" to "UTC", RegionDefaults.hintsForCountry("zz"))
    }

    @Test
    fun quickPresets_areNonEmptyAndValid() {
        assertTrue(RegionDefaults.quickPresets.isNotEmpty())
        assertTrue(RegionDefaults.quickPresets.all { it.cc2.length == 2 && it.mccmnc.length >= 5 })
    }
}
