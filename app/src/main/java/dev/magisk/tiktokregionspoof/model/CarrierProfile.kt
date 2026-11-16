package dev.magisk.tiktokregionspoof.model

import android.content.Context
import androidx.core.content.edit
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

data class CarrierProfile(
    val cc2: String,
    val country: String,
    val mccmnc: String,
    val brand: String,
    val operator: String
) {
    val isoLower: String
        get() = cc2.lowercase()

    val flagEmoji: String
        get() {
            val code = cc2.uppercase()
            if (code.length != 2) return "🏳️"
            val first = Character.codePointAt(code, 0) - 'A'.code + 0x1F1E6
            val second = Character.codePointAt(code, 1) - 'A'.code + 0x1F1E6
            return String(Character.toChars(first)) + String(Character.toChars(second))
        }

    val displayTitle: String
        get() = "$flagEmoji $country — $brand"

    val displaySubtitle: String
        get() = "ISO: ${cc2.uppercase()} · MCC+MNC: $mccmnc" +
            if (operator.isNotBlank() && !operator.equals(brand, ignoreCase = true)) {
                " · $operator"
            } else {
                ""
            }
}

data class SpoofConfig(
    val simCountryIso: String = "gb",
    val simOperatorMccMnc: String = "23415",
    val simOperatorName: String = "Vodafone UK",
    val networkCountryIso: String = "gb",
    val networkOperatorMccMnc: String = "23415",
    val networkOperatorName: String = "Vodafone UK",
    val simState: String = "READY",
    val phoneType: Int = 1,
    val networkType: String = "LTE",
    val mirrorDualSim: Boolean = true,
    val spoofWifiCountry: Boolean = true,
    val spoofLocale: Boolean = false,
    val localeTag: String = "en-GB",
    val spoofTimezone: Boolean = false,
    val timezoneId: String = "Europe/London",
    val persistMagiskModule: Boolean = true,
    val rilGuardLoop: Boolean = true,
    val forceStopTikTok: Boolean = true,
    val clearTikTokCache: Boolean = false
)

object SpoofConfigStore {
    private const val PREFS_NAME = "magisk_tiktok_spoof_prefs"
    private const val KEY_CONFIG = "spoof_config_json"
    private val gson = Gson()

    fun load(context: Context): SpoofConfig {
        val raw = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_CONFIG, null) ?: return SpoofConfig()
        return runCatching { gson.fromJson(raw, SpoofConfig::class.java) }
            .getOrDefault(SpoofConfig())
    }

    fun save(context: Context, config: SpoofConfig) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit {
            putString(KEY_CONFIG, gson.toJson(config))
        }
    }
}

object RegionDefaults {
    private val localeByCountry = mapOf(
        "US" to ("en-US" to "America/New_York"),
        "GB" to ("en-GB" to "Europe/London"),
        "CA" to ("en-CA" to "America/Toronto"),
        "AU" to ("en-AU" to "Australia/Sydney"),
        "JP" to ("ja-JP" to "Asia/Tokyo"),
        "KR" to ("ko-KR" to "Asia/Seoul"),
        "DE" to ("de-DE" to "Europe/Berlin"),
        "FR" to ("fr-FR" to "Europe/Paris"),
        "IT" to ("it-IT" to "Europe/Rome"),
        "ES" to ("es-ES" to "Europe/Madrid"),
        "BR" to ("pt-BR" to "America/Sao_Paulo"),
        "MX" to ("es-MX" to "America/Mexico_City"),
        "SG" to ("en-SG" to "Asia/Singapore"),
        "TH" to ("th-TH" to "Asia/Bangkok"),
        "VN" to ("vi-VN" to "Asia/Ho_Chi_Minh"),
        "ID" to ("id-ID" to "Asia/Jakarta"),
        "MY" to ("ms-MY" to "Asia/Kuala_Lumpur"),
        "PH" to ("en-PH" to "Asia/Manila"),
        "IN" to ("en-IN" to "Asia/Kolkata"),
        "TW" to ("zh-TW" to "Asia/Taipei"),
        "HK" to ("zh-HK" to "Asia/Hong_Kong"),
        "TR" to ("tr-TR" to "Europe/Istanbul"),
        "AE" to ("ar-AE" to "Asia/Dubai"),
        "SA" to ("ar-SA" to "Asia/Riyadh"),
        "RU" to ("ru-RU" to "Europe/Moscow"),
        "UA" to ("uk-UA" to "Europe/Kyiv"),
        "PL" to ("pl-PL" to "Europe/Warsaw"),
        "NL" to ("nl-NL" to "Europe/Amsterdam")
    )

    fun hintsForCountry(cc2: String): Pair<String, String> {
        val code = cc2.uppercase()
        return localeByCountry[code] ?: ("en-$code" to "UTC")
    }

    val quickPresets: List<CarrierProfile> = listOf(
        CarrierProfile("GB", "United Kingdom", "23415", "Vodafone UK", "Vodafone"),
        CarrierProfile("US", "United States", "310260", "T-Mobile", "T-Mobile US"),
        CarrierProfile("JP", "Japan", "44010", "NTT DOCOMO", "NTT DOCOMO"),
        CarrierProfile("KR", "South Korea", "45005", "SK Telecom", "SK Telecom"),
        CarrierProfile("SG", "Singapore", "52501", "Singtel", "Singtel"),
        CarrierProfile("DE", "Germany", "26201", "Telekom", "Telekom Deutschland"),
        CarrierProfile("TW", "Taiwan", "46692", "Chunghwa", "Chunghwa Telecom")
    )
}

object CarrierRepository {
    private val gson = Gson()
    private val priorityOrder = listOf("US", "GB", "JP", "KR", "DE", "FR", "CA", "AU", "SG", "TW", "BR")

    @Volatile
    private var cachedCarriers: List<CarrierProfile>? = null

    fun loadAll(context: Context): List<CarrierProfile> {
        cachedCarriers?.let { return it }
        val json = context.assets.open("mcc_mnc_carriers.json")
            .bufferedReader()
            .use { it.readText() }
        val type = object : TypeToken<List<CarrierProfile>>() {}.type
        val all: List<CarrierProfile> = gson.fromJson(json, type)
        val sorted = all.sortedWith(
            compareBy<CarrierProfile> { carrier ->
                val idx = priorityOrder.indexOf(carrier.cc2.uppercase())
                if (idx == -1) Int.MAX_VALUE else idx
            }.thenBy { it.country }.thenBy { it.brand }
        )
        cachedCarriers = sorted
        return sorted
    }
}
