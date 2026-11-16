package dev.magisk.tiktokregionspoof.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import android.view.View
import androidx.activity.enableEdgeToEdge
import androidx.core.content.ContextCompat
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.chip.Chip
import com.google.android.material.color.MaterialColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.snackbar.Snackbar
import dev.magisk.tiktokregionspoof.R
import dev.magisk.tiktokregionspoof.databinding.ActivityMainBinding
import dev.magisk.tiktokregionspoof.model.CarrierProfile
import dev.magisk.tiktokregionspoof.model.RegionDefaults
import dev.magisk.tiktokregionspoof.model.SpoofConfig
import dev.magisk.tiktokregionspoof.model.SpoofConfigStore
import dev.magisk.tiktokregionspoof.root.MagiskEnvironment
import dev.magisk.tiktokregionspoof.root.MagiskRootManager
import dev.magisk.tiktokregionspoof.root.ModuleStatus
import dev.magisk.tiktokregionspoof.root.ShellResult
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var currentEnv: MagiskEnvironment? = null
    private var lastShellLog: String = "No root commands executed yet."

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupFragmentResultListener()
        setupQuickPresets()
        setupSwitchLabels()
        bindConfigToUi(SpoofConfigStore.load(this))
        setupListeners()
    }

    private fun setupSwitchLabels() {
        val subtitleColor = MaterialColors.getColor(
            binding.rootCoordinator,
            com.google.android.material.R.attr.colorOnSurfaceVariant
        )
        fun bindSwitch(sw: MaterialSwitch, titleRes: Int, descRes: Int) {
            val title = getString(titleRes)
            val desc = getString(descRes)
            sw.text = SpannableStringBuilder(title).apply {
                append("\n")
                val start = length
                append(desc)
                setSpan(ForegroundColorSpan(subtitleColor), start, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                setSpan(RelativeSizeSpan(0.82f), start, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
        }
        bindSwitch(binding.swMirrorDualSim, R.string.opt_mirror_dual_sim, R.string.opt_mirror_dual_sim_desc)
        bindSwitch(binding.swSpoofWifiCountry, R.string.opt_spoof_wifi_country, R.string.opt_spoof_wifi_country_desc)
        bindSwitch(binding.swSpoofLocale, R.string.opt_spoof_locale, R.string.opt_spoof_locale_desc)
        bindSwitch(binding.swSpoofTimezone, R.string.opt_spoof_timezone, R.string.opt_spoof_timezone_desc)
        bindSwitch(binding.swPersistModule, R.string.opt_install_magisk_module, R.string.opt_install_magisk_module_desc)
        bindSwitch(binding.swRilGuard, R.string.opt_enable_ril_watchdog, R.string.opt_enable_ril_watchdog_desc)
        bindSwitch(binding.swForceStop, R.string.opt_force_stop_tiktok, R.string.opt_force_stop_tiktok_desc)
        bindSwitch(binding.swClearCache, R.string.opt_clear_tiktok_cache, R.string.opt_clear_tiktok_cache_desc)
    }

    override fun onResume() {
        super.onResume()
        refreshEnvironment()
    }

    private fun setupFragmentResultListener() {
        supportFragmentManager.setFragmentResultListener(
            CarrierPickerBottomSheet.REQUEST_KEY,
            this
        ) { _, bundle ->
            val cc2 = bundle.getString(CarrierPickerBottomSheet.KEY_CC2).orEmpty()
            val country = bundle.getString(CarrierPickerBottomSheet.KEY_COUNTRY).orEmpty()
            val mccmnc = bundle.getString(CarrierPickerBottomSheet.KEY_MCCMNC).orEmpty()
            val brand = bundle.getString(CarrierPickerBottomSheet.KEY_BRAND).orEmpty()
            val operator = bundle.getString(CarrierPickerBottomSheet.KEY_OPERATOR).orEmpty()
            if (cc2.isNotEmpty() && mccmnc.isNotEmpty()) {
                applyCarrierToFields(CarrierProfile(cc2, country, mccmnc, brand, operator))
            }
        }
    }

    private fun setupQuickPresets() {
        binding.chipGroupQuickPresets.removeAllViews()
        RegionDefaults.quickPresets.forEach { preset ->
            val chip = Chip(this).apply {
                text = getString(R.string.quick_preset_chip_format, preset.flagEmoji, preset.cc2)
                setOnClickListener { applyCarrierToFields(preset) }
            }
            binding.chipGroupQuickPresets.addView(chip)
        }
    }

    private fun applyCarrierToFields(picked: CarrierProfile) {
        binding.etSimCountryIso.setText(picked.isoLower)
        binding.etNetCountryIso.setText(picked.isoLower)
        binding.etSimMccMnc.setText(picked.mccmnc)
        binding.etNetMccMnc.setText(picked.mccmnc)
        binding.etSimOperatorName.setText(picked.brand)
        binding.etNetOperatorName.setText(picked.brand)
        clearFieldErrors()

        val (localeHint, tzHint) = RegionDefaults.hintsForCountry(picked.cc2)
        binding.etLocaleTag.setText(localeHint)
        binding.etTimezoneId.setText(tzHint)
        binding.tvSelectedPreset.text = getString(
            R.string.preset_summary_format,
            "${picked.flagEmoji} ${picked.country}",
            picked.brand,
            picked.mccmnc
        )
    }

    private fun bindConfigToUi(cfg: SpoofConfig) {
        binding.etSimCountryIso.setText(cfg.simCountryIso)
        binding.etNetCountryIso.setText(cfg.networkCountryIso)
        binding.etSimMccMnc.setText(cfg.simOperatorMccMnc)
        binding.etNetMccMnc.setText(cfg.networkOperatorMccMnc)
        binding.etSimOperatorName.setText(cfg.simOperatorName)
        binding.etNetOperatorName.setText(cfg.networkOperatorName)

        binding.swMirrorDualSim.isChecked = cfg.mirrorDualSim
        binding.swSpoofWifiCountry.isChecked = cfg.spoofWifiCountry
        binding.swPersistModule.isChecked = cfg.persistMagiskModule
        binding.swRilGuard.isChecked = cfg.rilGuardLoop
        binding.swRilGuard.isEnabled = cfg.persistMagiskModule

        binding.swSpoofLocale.isChecked = cfg.spoofLocale
        binding.etLocaleTag.setText(cfg.localeTag)
        binding.tilLocaleTag.visibility = if (cfg.spoofLocale) View.VISIBLE else View.GONE

        binding.swSpoofTimezone.isChecked = cfg.spoofTimezone
        binding.etTimezoneId.setText(cfg.timezoneId)
        binding.tilTimezoneId.visibility = if (cfg.spoofTimezone) View.VISIBLE else View.GONE

        binding.swForceStop.isChecked = cfg.forceStopTikTok
        binding.swClearCache.isChecked = cfg.clearTikTokCache
        binding.tvSelectedPreset.text = getString(
            R.string.preset_summary_format,
            cfg.simCountryIso.uppercase(),
            cfg.simOperatorName,
            cfg.simOperatorMccMnc
        )
    }

    private fun readConfigFromUi(): SpoofConfig {
        val previous = SpoofConfigStore.load(this)
        return previous.copy(
            simCountryIso = binding.etSimCountryIso.text?.toString()?.trim().orEmpty().lowercase(),
            networkCountryIso = binding.etNetCountryIso.text?.toString()?.trim().orEmpty().lowercase(),
            simOperatorMccMnc = binding.etSimMccMnc.text?.toString()?.trim().orEmpty(),
            networkOperatorMccMnc = binding.etNetMccMnc.text?.toString()?.trim().orEmpty(),
            simOperatorName = binding.etSimOperatorName.text?.toString()?.trim().orEmpty(),
            networkOperatorName = binding.etNetOperatorName.text?.toString()?.trim().orEmpty(),
            mirrorDualSim = binding.swMirrorDualSim.isChecked,
            spoofWifiCountry = binding.swSpoofWifiCountry.isChecked,
            persistMagiskModule = binding.swPersistModule.isChecked,
            rilGuardLoop = binding.swRilGuard.isChecked,
            spoofLocale = binding.swSpoofLocale.isChecked,
            localeTag = binding.etLocaleTag.text?.toString()?.trim().orEmpty(),
            spoofTimezone = binding.swSpoofTimezone.isChecked,
            timezoneId = binding.etTimezoneId.text?.toString()?.trim().orEmpty(),
            forceStopTikTok = binding.swForceStop.isChecked,
            clearTikTokCache = binding.swClearCache.isChecked
        )
    }

    private fun clearFieldErrors() {
        binding.tilSimCountryIso.error = null
        binding.tilNetCountryIso.error = null
        binding.tilSimMccMnc.error = null
        binding.tilNetMccMnc.error = null
    }

    private fun validateConfig(cfg: SpoofConfig): Boolean {
        clearFieldErrors()
        var valid = true
        if (cfg.simCountryIso.length != 2 || !cfg.simCountryIso.all { it.isLetter() }) {
            binding.tilSimCountryIso.error = "2-letter ISO (e.g. us)"
            valid = false
        }
        if (cfg.networkCountryIso.length != 2 || !cfg.networkCountryIso.all { it.isLetter() }) {
            binding.tilNetCountryIso.error = "2-letter ISO (e.g. us)"
            valid = false
        }
        if (cfg.simOperatorMccMnc.length < 5 || !cfg.simOperatorMccMnc.all { it.isDigit() }) {
            binding.tilSimMccMnc.error = "5-6 digits"
            valid = false
        }
        if (cfg.networkOperatorMccMnc.length < 5 || !cfg.networkOperatorMccMnc.all { it.isDigit() }) {
            binding.tilNetMccMnc.error = "5-6 digits"
            valid = false
        }
        return valid
    }

    private fun setupListeners() {
        binding.btnRefreshEnv.setOnClickListener { refreshEnvironment() }

        binding.btnOpenMagisk.setOnClickListener {
            val intent = packageManager.getLaunchIntentForPackage(MAGISK_PACKAGE)
            if (intent != null) {
                startActivity(intent)
            } else {
                showMessage(getString(R.string.magisk_app_not_found))
            }
        }

        binding.btnChooseCarrier.setOnClickListener {
            CarrierPickerBottomSheet.newInstance()
                .show(supportFragmentManager, CarrierPickerBottomSheet.TAG)
        }

        binding.swPersistModule.setOnCheckedChangeListener { _, checked ->
            binding.swRilGuard.isEnabled = checked
        }

        binding.swSpoofLocale.setOnCheckedChangeListener { _, checked ->
            binding.tilLocaleTag.visibility = if (checked) View.VISIBLE else View.GONE
        }

        binding.swSpoofTimezone.setOnCheckedChangeListener { _, checked ->
            binding.tilTimezoneId.visibility = if (checked) View.VISIBLE else View.GONE
        }

        binding.btnApply.setOnClickListener {
            val cfg = readConfigFromUi()
            if (!validateConfig(cfg)) return@setOnClickListener

            SpoofConfigStore.save(this, cfg)
            setActionsEnabled(false)
            lifecycleScope.launch {
                val env = currentEnv ?: MagiskRootManager.inspectEnvironment(this@MainActivity)
                if (!env.hasRoot) {
                    setActionsEnabled(true)
                    showMessage("Magisk Root (su) is required but not granted.")
                    return@launch
                }
                val result = MagiskRootManager.applySpoof(
                    config = cfg,
                    slotCount = env.simSlotCount,
                    installedPackages = env.installedTikTokPackages
                )
                recordShellLog("applySpoof (${cfg.simCountryIso.uppercase()})", result)
                setActionsEnabled(true)
                if (result.isSuccess) {
                    showMessage("Applied via Magisk resetprop (${cfg.simCountryIso.uppercase()}).")
                    refreshEnvironment()
                    verifyLiveDiagnostics()
                } else {
                    showMessage("Failed (exit ${result.exitCode}). Tap 'Root Log' for details.")
                }
            }
        }

        binding.btnVerify.setOnClickListener { verifyLiveDiagnostics() }

        binding.btnRestore.setOnClickListener {
            setActionsEnabled(false)
            lifecycleScope.launch {
                val env = currentEnv ?: MagiskRootManager.inspectEnvironment(this@MainActivity)
                val result = MagiskRootManager.restoreDefaults(env.installedTikTokPackages)
                recordShellLog("restoreDefaults", result)
                setActionsEnabled(true)
                if (result.isSuccess) {
                    showMessage("Magisk module removed. Reboot device to restore original baseband props.")
                    refreshEnvironment()
                    verifyLiveDiagnostics()
                } else {
                    showMessage("Restore failed. Tap 'Root Log' for details.")
                }
            }
        }

        binding.btnViewRootLog.setOnClickListener {
            MaterialAlertDialogBuilder(this)
                .setTitle("Magisk Root Execution Log")
                .setMessage(lastShellLog)
                .setPositiveButton("Copy") { _, _ ->
                    val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    cm.setPrimaryClip(ClipData.newPlainText("MagiskRootLog", lastShellLog))
                    showMessage("Log copied to clipboard.")
                }
                .setNegativeButton("Close", null)
                .show()
        }

        binding.btnLaunchTikTok.setOnClickListener {
            val installed = currentEnv?.installedTikTokPackages.orEmpty()
            when {
                installed.isEmpty() -> showMessage("No TikTok packages installed.")
                installed.size == 1 -> launchPackage(installed.first().packageName)
                else -> {
                    val labels = installed.map { it.label }.toTypedArray()
                    MaterialAlertDialogBuilder(this)
                        .setTitle("Launch TikTok")
                        .setItems(labels) { _, which ->
                            launchPackage(installed[which].packageName)
                        }
                        .show()
                }
            }
        }
    }

    private fun launchPackage(packageName: String) {
        val intent = packageManager.getLaunchIntentForPackage(packageName)
        if (intent != null) {
            startActivity(intent)
        } else {
            showMessage("Unable to launch $packageName")
        }
    }

    private fun recordShellLog(action: String, result: ShellResult) {
        lastShellLog = buildString {
            appendLine("Action    : $action")
            appendLine("Exit Code : ${result.exitCode}")
            appendLine("--- STDOUT ---")
            appendLine(result.stdout.ifEmpty { "(empty)" })
            appendLine("--- STDERR ---")
            appendLine(result.stderr.ifEmpty { "(empty)" })
        }
    }

    private fun refreshEnvironment() {
        lifecycleScope.launch {
            val env = MagiskRootManager.inspectEnvironment(this@MainActivity)
            currentEnv = env

            binding.tvMagiskStatusTitle.text = getString(
                if (env.hasRoot) R.string.magisk_root_active_format else R.string.magisk_root_unavailable
            )
            val cardColorAttr = if (env.hasRoot) {
                com.google.android.material.R.attr.colorPrimaryContainer
            } else {
                com.google.android.material.R.attr.colorErrorContainer
            }
            val iconColorAttr = if (env.hasRoot) {
                com.google.android.material.R.attr.colorOnPrimaryContainer
            } else {
                com.google.android.material.R.attr.colorOnErrorContainer
            }
            binding.cardMagiskStatus.setCardBackgroundColor(
                MaterialColors.getColor(binding.cardMagiskStatus, cardColorAttr)
            )
            binding.ivMagiskIcon.setColorFilter(
                MaterialColors.getColor(binding.ivMagiskIcon, iconColorAttr)
            )
            val missingRootVisibility = if (env.hasRoot) View.GONE else View.VISIBLE
            binding.tvMagiskGuidance.visibility = missingRootVisibility
            binding.btnOpenMagisk.visibility = missingRootVisibility

            val modText = when (env.moduleStatus) {
                ModuleStatus.ACTIVE -> getString(R.string.module_active)
                ModuleStatus.DISABLED -> getString(R.string.module_disabled)
                ModuleStatus.NOT_INSTALLED -> getString(R.string.module_not_installed)
            }
            val resetpropState = getString(
                if (env.hasResetprop) R.string.status_ready else R.string.status_missing
            )
            val versionLabel = if (env.hasRoot) env.magiskVersion else getString(R.string.status_missing)
            binding.tvMagiskDetails.text = getString(
                R.string.magisk_details_format,
                versionLabel,
                resetpropState,
                env.simSlotCount,
                modText
            )

            binding.chipGroupTikTokApps.removeAllViews()
            if (env.installedTikTokPackages.isEmpty()) {
                binding.btnLaunchTikTok.visibility = View.GONE
                val chip = Chip(this@MainActivity).apply {
                    text = getString(R.string.no_tiktok_detected)
                    isClickable = false
                }
                binding.chipGroupTikTokApps.addView(chip)
            } else {
                binding.btnLaunchTikTok.visibility = View.VISIBLE
                env.installedTikTokPackages.forEach { pkg ->
                    val chip = Chip(this@MainActivity).apply {
                        text = pkg.label
                        isClickable = false
                    }
                    binding.chipGroupTikTokApps.addView(chip)
                }
            }
        }
    }

    private fun verifyLiveDiagnostics() {
        val targetIso = binding.etSimCountryIso.text?.toString()?.trim().orEmpty().lowercase()
        lifecycleScope.launch {
            val diag = MagiskRootManager.readLiveDiagnostics(this@MainActivity)

            val propMatches = diag.propSimCountry.lowercase().split(",").any { it == targetIso }
            val tmMatches = diag.tmSimCountry.lowercase() == targetIso

            binding.tvDiagnosticsSummary.visibility = View.VISIBLE
            when {
                tmMatches && propMatches -> {
                    binding.tvDiagnosticsSummary.text = getString(
                        R.string.diag_effective_format,
                        targetIso.uppercase()
                    )
                    binding.tvDiagnosticsSummary.setTextColor(
                        ContextCompat.getColor(this@MainActivity, R.color.status_ok)
                    )
                }
                propMatches && !tmMatches -> {
                    binding.tvDiagnosticsSummary.text = getString(
                        R.string.diag_overridden_format,
                        targetIso.uppercase(),
                        diag.tmSimCountry.uppercase()
                    )
                    binding.tvDiagnosticsSummary.setTextColor(
                        ContextCompat.getColor(this@MainActivity, R.color.status_warn)
                    )
                }
                else -> {
                    binding.tvDiagnosticsSummary.text = getString(
                        R.string.diag_not_spoofed_format,
                        targetIso.uppercase(),
                        diag.propSimCountry,
                        diag.tmSimCountry
                    )
                    binding.tvDiagnosticsSummary.setTextColor(
                        ContextCompat.getColor(this@MainActivity, R.color.status_warn)
                    )
                }
            }

            binding.tvDiagnosticsOutput.text = buildString {
                appendLine("[TelephonyManager API]")
                appendLine("simCountryIso     : ${diag.tmSimCountry}")
                appendLine("networkCountryIso : ${diag.tmNetworkCountry}")
                appendLine("simOperator       : ${diag.tmSimOperator} (${diag.tmSimOperatorName})")
                appendLine("networkOperator   : ${diag.tmNetworkOperator} (${diag.tmNetworkOperatorName})")
                appendLine()
                appendLine("[Magisk resetprop]")
                appendLine("gsm.sim.operator.iso-country : ${diag.propSimCountry}")
                appendLine("gsm.operator.iso-country     : ${diag.propNetworkCountry}")
                appendLine("gsm.sim.operator.numeric     : ${diag.propSimOperator}")
                appendLine("gsm.operator.numeric         : ${diag.propNetworkOperator}")
                appendLine("gsm.sim.operator.alpha       : ${diag.propSimOperatorName}")
                appendLine("gsm.operator.alpha           : ${diag.propNetworkOperatorName}")
                appendLine()
                appendLine("[Locale & Timezone]")
                appendLine("Locale   : ${diag.activeLocale}")
                append("Timezone : ${diag.activeTimezone}")
            }
        }
    }

    private fun setActionsEnabled(enabled: Boolean) {
        binding.btnApply.isEnabled = enabled
        binding.btnVerify.isEnabled = enabled
        binding.btnRestore.isEnabled = enabled
    }

    private fun showMessage(msg: String) {
        Snackbar.make(binding.rootCoordinator, msg, Snackbar.LENGTH_LONG).show()
    }

    companion object {
        private const val MAGISK_PACKAGE = "com.topjohnwu.magisk"
    }
}
