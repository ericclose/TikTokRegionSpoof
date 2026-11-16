package dev.magisk.tiktokregionspoof

import dev.magisk.tiktokregionspoof.model.CarrierFilterEngine
import dev.magisk.tiktokregionspoof.model.CarrierProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CarrierFilterEngineTest {

    private val sampleCarriers = listOf(
        CarrierProfile("GB", "United Kingdom", "23415", "Vodafone", "Vodafone UK"),
        CarrierProfile("GB", "United Kingdom", "23430", "EE", "EE Limited"),
        CarrierProfile("DE", "Germany", "26202", "Vodafone", "Vodafone D2"),
        CarrierProfile("JP", "Japan", "44010", "NTT DOCOMO", "NTT DOCOMO"),
        CarrierProfile("JP", "Japan", "44020", "SoftBank", "SoftBank Corp."),
        CarrierProfile("US", "United States", "310260", "T-Mobile", "T-Mobile US")
    )

    private val allCountries = "All Countries"
    private val allCarriers = "All Carriers"

    @Test
    fun filterCarriers_noFiltersReturnsAll() {
        val result = CarrierFilterEngine.filterCarriers(
            allCarriers = sampleCarriers,
            countryIso = "",
            countryText = "",
            allCountriesLabel = allCountries,
            carrierBrand = "",
            carrierText = "",
            allCarriersLabel = allCarriers
        )
        assertEquals(sampleCarriers.size, result.size)
    }

    @Test
    fun filterCarriers_allLabelsTreatedAsNoFilter() {
        val result = CarrierFilterEngine.filterCarriers(
            allCarriers = sampleCarriers,
            countryIso = "",
            countryText = allCountries,
            allCountriesLabel = allCountries,
            carrierBrand = "",
            carrierText = allCarriers,
            allCarriersLabel = allCarriers
        )
        assertEquals(sampleCarriers.size, result.size)
    }

    @Test
    fun filterCarriers_combinesCountryAndCarrierWithAndSemantics() {
        val result = CarrierFilterEngine.filterCarriers(
            allCarriers = sampleCarriers,
            countryIso = "de",
            countryText = "",
            allCountriesLabel = allCountries,
            carrierBrand = "vodafone",
            carrierText = "",
            allCarriersLabel = allCarriers
        )
        assertEquals(1, result.size)
        assertEquals("26202", result.first().mccmnc)
    }

    @Test
    fun filterCarriers_supportsPartialSearchByMccMncAndOperator() {
        val byMcc = CarrierFilterEngine.filterCarriers(
            allCarriers = sampleCarriers,
            countryIso = "",
            countryText = "",
            allCountriesLabel = allCountries,
            carrierBrand = "",
            carrierText = "44020",
            allCarriersLabel = allCarriers
        )
        assertEquals(1, byMcc.size)
        assertEquals("SoftBank", byMcc.first().brand)

        val byCountryText = CarrierFilterEngine.filterCarriers(
            allCarriers = sampleCarriers,
            countryIso = "",
            countryText = "united",
            allCountriesLabel = allCountries,
            carrierBrand = "",
            carrierText = "ee",
            allCarriersLabel = allCarriers
        )
        assertEquals(1, byCountryText.size)
        assertEquals("23430", byCountryText.first().mccmnc)
    }

    @Test
    fun filterCarriers_nonMatchingCombinationReturnsEmpty() {
        val result = CarrierFilterEngine.filterCarriers(
            allCarriers = sampleCarriers,
            countryIso = "JP",
            countryText = "",
            allCountriesLabel = allCountries,
            carrierBrand = "Vodafone",
            carrierText = "",
            allCarriersLabel = allCarriers
        )
        assertTrue(result.isEmpty())
    }

    @Test
    fun buildCountryOptions_cascadesFromSelectedCarrier() {
        val options = CarrierFilterEngine.buildCountryOptions(
            allCarriers = sampleCarriers,
            carrierBrand = "Vodafone",
            carrierText = "",
            allCountriesLabel = allCountries,
            allCarriersLabel = allCarriers
        )
        assertEquals(listOf("", "DE", "GB"), options.map { it.value })
    }

    @Test
    fun buildCarrierOptions_cascadesFromSelectedCountry() {
        val options = CarrierFilterEngine.buildCarrierOptions(
            allCarriers = sampleCarriers,
            countryIso = "JP",
            countryText = "",
            allCountriesLabel = allCountries,
            allCarriersLabel = allCarriers
        )
        assertEquals(listOf("", "NTT DOCOMO", "SoftBank"), options.map { it.value })
    }
}
