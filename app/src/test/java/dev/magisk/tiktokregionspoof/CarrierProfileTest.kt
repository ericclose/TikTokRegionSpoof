package dev.magisk.tiktokregionspoof

import dev.magisk.tiktokregionspoof.model.AppThemeMode
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
    fun flagEmoji_invalidOrNonLetterCodeFallsBackToWhiteFlag() {
        assertEquals("🏳️", CarrierProfile("USA", "Unknown", "00000", "Test", "Test").flagEmoji)
        assertEquals("🏳️", CarrierProfile("", "Empty", "00000", "Test", "Test").flagEmoji)
        assertEquals("🏳️", CarrierProfile("12", "Digits", "00000", "Test", "Test").flagEmoji)
        assertEquals("🏳️", CarrierProfile("A!", "Symbol", "00000", "Test", "Test").flagEmoji)
        assertEquals("🇬🇧", CarrierProfile(" gb ", "UK", "23415", "Vodafone", "Vodafone").flagEmoji)
    }

    @Test
    fun hintsForCountry_returnsMappedAndFallbackDefaults() {
        assertEquals("ja-JP" to "Asia/Tokyo", RegionDefaults.hintsForCountry("jp"))
        assertEquals("ja-JP" to "Asia/Tokyo", RegionDefaults.hintsForCountry(" jp "))
        assertEquals("en-ZZ" to "UTC", RegionDefaults.hintsForCountry("zz"))
        assertEquals("en-US" to "UTC", RegionDefaults.hintsForCountry(""))
        assertEquals("en-US" to "UTC", RegionDefaults.hintsForCountry("12"))
    }

    @Test
    fun quickPresets_areNonEmptyValidAndUnique() {
        assertTrue(RegionDefaults.quickPresets.isNotEmpty())
        assertTrue(RegionDefaults.quickPresets.all { it.cc2.length == 2 && it.mccmnc.length >= 5 })
        val distinctCountries = RegionDefaults.quickPresets.map { it.isoLower }.toSet()
        assertEquals(RegionDefaults.quickPresets.size, distinctCountries.size)
    }

    @Test
    fun appThemeMode_fromOrdinalHandlesOutOfRange() {
        assertEquals(AppThemeMode.SYSTEM, AppThemeMode.fromOrdinal(0))
        assertEquals(AppThemeMode.LIGHT, AppThemeMode.fromOrdinal(1))
        assertEquals(AppThemeMode.DARK, AppThemeMode.fromOrdinal(2))
        assertEquals(AppThemeMode.SYSTEM, AppThemeMode.fromOrdinal(-1))
        assertEquals(AppThemeMode.SYSTEM, AppThemeMode.fromOrdinal(99))
    }
}
