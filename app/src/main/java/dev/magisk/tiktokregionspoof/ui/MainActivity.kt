package dev.magisk.tiktokregionspoof.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.core.os.LocaleListCompat
import androidx.core.widget.NestedScrollView
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.lifecycleScope
import com.google.android.material.chip.Chip
import com.google.android.material.color.MaterialColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputLayout
import dev.magisk.tiktokregionspoof.R
import dev.magisk.tiktokregionspoof.databinding.ActivityMainBinding
import dev.magisk.tiktokregionspoof.model.AppThemeMode
import dev.magisk.tiktokregionspoof.model.CarrierProfile
import dev.magisk.tiktokregionspoof.model.RegionDefaults
import dev.magisk.tiktokregionspoof.model.SpoofConfig
import dev.magisk.tiktokregionspoof.model.SpoofConfigStore
import dev.magisk.tiktokregionspoof.root.MagiskEnvironment
import dev.magisk.tiktokregionspoof.root.MagiskRootManager
import dev.magisk.tiktokregionspoof.root.ModuleStatus
import dev.magisk.tiktokregionspoof.root.ShellResult
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var currentEnv: MagiskEnvironment? = null
    private var lastShellLog: String = ""
    private var propertiesExpanded = false
    private var activeTabId = R.id.nav_dashboard
    private var refreshJob: Job? = null
    private var isBindingUi = false

    private val dash get() = binding.tabDashboard
    private val opts get() = binding.tabOptions

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        lastShellLog = savedInstanceState?.getString(KEY_SHELL_LOG)
            ?: getString(R.string.no_root_commands_yet)

        setupFragmentResultListener()
        setupQuickPresets()
        setupSwitchLabels()
        setupAppearanceControls()
        bindConfigToUi(SpoofConfigStore.load(this))
        setupNavigationAndScroll()
        setupListeners()

        activeTabId = savedInstanceState?.getInt(KEY_ACTIVE_TAB, R.id.nav_dashboard) ?: R.id.nav_dashboard
        if (activeTabId == R.id.nav_options) {
            dash.pageDashboard.visibility = View.GONE
            opts.pageOptions.visibility = View.VISIBLE
            binding.fabApply.hide()
            binding.bottomNav.selectedItemId = R.id.nav_options
        } else {
            MotionHelper.staggerEnter(
                listOf(
                    dash.cardMagiskStatus,
                    dash.cardRegionPreset,
                    dash.cardProperties,
                    dash.cardTelemetry
                )
            )
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(KEY_ACTIVE_TAB, activeTabId)
        outState.putString(KEY_SHELL_LOG, lastShellLog)
    }

    override fun onResume() {
        super.onResume()
        refreshEnvironment()
    }

    private fun setupNavigationAndScroll() {
        binding.bottomNav.setOnItemSelectedListener { item ->
            activeTabId = item.itemId
            when (item.itemId) {
                R.id.nav_dashboard -> {
                    MotionHelper.animatePageSwitch(dash.pageDashboard, opts.pageOptions)
                    binding.fabApply.show()
                    true
                }
                R.id.nav_options -> {
                    MotionHelper.animatePageSwitch(opts.pageOptions, dash.pageDashboard)
                    binding.fabApply.hide()
                    true
                }
                else -> false
            }
        }

        dash.pageDashboard.setOnScrollChangeListener(
            NestedScrollView.OnScrollChangeListener { _, _, scrollY, _, oldScrollY ->
                if (scrollY - oldScrollY > 12) {
                    binding.fabApply.shrink()
                } else if (oldScrollY - scrollY > 12 || scrollY == 0) {
                    binding.fabApply.extend()
                }
            }
        )
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
        bindSwitch(opts.swMirrorDualSim, R.string.opt_mirror_dual_sim, R.string.opt_mirror_dual_sim_desc)
        bindSwitch(opts.swSpoofWifiCountry, R.string.opt_spoof_wifi_country, R.string.opt_spoof_wifi_country_desc)
        bindSwitch(opts.swSpoofLocale, R.string.opt_spoof_locale, R.string.opt_spoof_locale_desc)
        bindSwitch(opts.swSpoofTimezone, R.string.opt_spoof_timezone, R.string.opt_spoof_timezone_desc)
        bindSwitch(opts.swPersistModule, R.string.opt_install_magisk_module, R.string.opt_install_magisk_module_desc)
        bindSwitch(opts.swRilGuard, R.string.opt_enable_ril_watchdog, R.string.opt_enable_ril_watchdog_desc)
        bindSwitch(opts.swForceStop, R.string.opt_force_stop_tiktok, R.string.opt_force_stop_tiktok_desc)
        bindSwitch(opts.swClearCache, R.string.opt_clear_tiktok_cache, R.string.opt_clear_tiktok_cache_desc)
    }

    private fun setupAppearanceControls() {
        val currentThemeBtn = when (SpoofConfigStore.loadThemeMode(this)) {
            AppThemeMode.SYSTEM -> opts.btnThemeSystem.id
            AppThemeMode.LIGHT -> opts.btnThemeLight.id
            AppThemeMode.DARK -> opts.btnThemeDark.id
        }
        opts.toggleThemeMode.check(currentThemeBtn)
        opts.toggleThemeMode.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            val nextMode = when (checkedId) {
                opts.btnThemeLight.id -> AppThemeMode.LIGHT
                opts.btnThemeDark.id -> AppThemeMode.DARK
                else -> AppThemeMode.SYSTEM
            }
            if (nextMode != SpoofConfigStore.loadThemeMode(this)) {
                SpoofConfigStore.saveThemeMode(this, nextMode)
            }
        }

        val currentTags = AppCompatDelegate.getApplicationLocales().toLanguageTags()
        val currentLangBtn = when {
            currentTags.startsWith("zh") -> opts.btnLangZh.id
            currentTags.startsWith("en") -> opts.btnLangEn.id
            else -> opts.btnLangSystem.id
        }
        opts.toggleLanguage.check(currentLangBtn)
        opts.toggleLanguage.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            val targetLocales = when (checkedId) {
                opts.btnLangEn.id -> LocaleListCompat.forLanguageTags("en")
                opts.btnLangZh.id -> LocaleListCompat.forLanguageTags("zh-CN")
                else -> LocaleListCompat.getEmptyLocaleList()
            }
            if (AppCompatDelegate.getApplicationLocales() != targetLocales) {
                AppCompatDelegate.setApplicationLocales(targetLocales)
            }
        }
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

    private fun createTagChip(
        label: String,
        cornerPx: Float? = null,
        tagValue: Any? = null,
        onClick: ((View) -> Unit)? = null
    ): Chip = Chip(this).apply {
        text = label
        tag = tagValue
        chipStrokeWidth = 0f
        if (cornerPx != null) {
            shapeAppearanceModel = shapeAppearanceModel.toBuilder()
                .setAllCornerSizes(cornerPx)
                .build()
        }
        if (onClick != null) {
            setOnClickListener(onClick)
        } else {
            isClickable = false
        }
    }

    private fun setupQuickPresets() {
        dash.chipGroupQuickPresets.removeAllViews()
        val cornerPx = 16f * resources.displayMetrics.density
        RegionDefaults.quickPresets.forEach { preset ->
            val chip = createTagChip(
                label = getString(R.string.quick_preset_chip_format, preset.flagEmoji, preset.cc2),
                cornerPx = cornerPx,
                tagValue = preset.isoLower
            ) {
                it.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                applyCarrierToFields(preset)
            }
            dash.chipGroupQuickPresets.addView(chip)
        }
        updateQuickPresetSelection(SpoofConfigStore.load(this).simCountryIso)
    }

    private fun updateQuickPresetSelection(activeIso: String) {
        val selectedBg = android.content.res.ColorStateList.valueOf(
            MaterialColors.getColor(
                dash.chipGroupQuickPresets,
                com.google.android.material.R.attr.colorPrimaryContainer
            )
        )
        val selectedText = MaterialColors.getColor(
            dash.chipGroupQuickPresets,
            com.google.android.material.R.attr.colorOnPrimaryContainer
        )
        val normalBg = android.content.res.ColorStateList.valueOf(
            MaterialColors.getColor(
                dash.chipGroupQuickPresets,
                com.google.android.material.R.attr.colorSurfaceContainerHigh
            )
        )
        val normalText = MaterialColors.getColor(
            dash.chipGroupQuickPresets,
            com.google.android.material.R.attr.colorOnSurface
        )
        val target = activeIso.lowercase()
        for (i in 0 until dash.chipGroupQuickPresets.childCount) {
            val chip = dash.chipGroupQuickPresets.getChildAt(i) as? Chip ?: continue
            val isSelected = (chip.tag as? String) == target
            chip.chipBackgroundColor = if (isSelected) selectedBg else normalBg
            chip.setTextColor(if (isSelected) selectedText else normalText)
        }
    }

    private fun applyCarrierToFields(picked: CarrierProfile) {
        dash.etSimCountryIso.setText(picked.isoLower)
        dash.etNetCountryIso.setText(picked.isoLower)
        dash.etSimMccMnc.setText(picked.mccmnc)
        dash.etNetMccMnc.setText(picked.mccmnc)
        dash.etSimOperatorName.setText(picked.brand)
        dash.etNetOperatorName.setText(picked.brand)
        clearFieldErrors()

        val (localeHint, tzHint) = RegionDefaults.hintsForCountry(picked.cc2)
        opts.etLocaleTag.setText(localeHint)
        opts.etTimezoneId.setText(tzHint)
        dash.tvSelectedPreset.text = getString(
            R.string.preset_summary_format,
            "${picked.flagEmoji} ${picked.country}",
            picked.brand,
            picked.mccmnc
        )
        updateQuickPresetSelection(picked.isoLower)
    }

    private fun bindConfigToUi(cfg: SpoofConfig) {
        isBindingUi = true
        try {
            dash.etSimCountryIso.setText(cfg.simCountryIso)
            dash.etNetCountryIso.setText(cfg.networkCountryIso)
            dash.etSimMccMnc.setText(cfg.simOperatorMccMnc)
            dash.etNetMccMnc.setText(cfg.networkOperatorMccMnc)
            dash.etSimOperatorName.setText(cfg.simOperatorName)
            dash.etNetOperatorName.setText(cfg.networkOperatorName)

            opts.swMirrorDualSim.isChecked = cfg.mirrorDualSim
            opts.swSpoofWifiCountry.isChecked = cfg.spoofWifiCountry
            opts.swPersistModule.isChecked = cfg.persistMagiskModule
            opts.swRilGuard.isChecked = cfg.rilGuardLoop
            opts.swRilGuard.isEnabled = cfg.persistMagiskModule

            opts.swSpoofLocale.isChecked = cfg.spoofLocale
            opts.etLocaleTag.setText(cfg.localeTag)
            opts.tilLocaleTag.visibility = if (cfg.spoofLocale) View.VISIBLE else View.GONE

            opts.swSpoofTimezone.isChecked = cfg.spoofTimezone
            opts.etTimezoneId.setText(cfg.timezoneId)
            opts.tilTimezoneId.visibility = if (cfg.spoofTimezone) View.VISIBLE else View.GONE

            opts.swForceStop.isChecked = cfg.forceStopTikTok
            opts.swClearCache.isChecked = cfg.clearTikTokCache
            val flag = CarrierProfile(cfg.simCountryIso, "", "", "", "").flagEmoji
            dash.tvSelectedPreset.text = getString(
                R.string.preset_summary_format,
                "$flag ${cfg.simCountryIso.uppercase()}",
                cfg.simOperatorName,
                cfg.simOperatorMccMnc
            )
            updateQuickPresetSelection(cfg.simCountryIso)
        } finally {
            isBindingUi = false
        }
    }

    private fun saveCurrentConfig() {
        if (isBindingUi) return
        SpoofConfigStore.save(this, readConfigFromUi())
    }

    private fun readConfigFromUi(): SpoofConfig = SpoofConfig(
        simCountryIso = dash.etSimCountryIso.text?.toString()?.trim().orEmpty().lowercase(),
        networkCountryIso = dash.etNetCountryIso.text?.toString()?.trim().orEmpty().lowercase(),
        simOperatorMccMnc = dash.etSimMccMnc.text?.toString()?.trim().orEmpty(),
        networkOperatorMccMnc = dash.etNetMccMnc.text?.toString()?.trim().orEmpty(),
        simOperatorName = dash.etSimOperatorName.text?.toString()?.trim().orEmpty(),
        networkOperatorName = dash.etNetOperatorName.text?.toString()?.trim().orEmpty(),
        mirrorDualSim = opts.swMirrorDualSim.isChecked,
        spoofWifiCountry = opts.swSpoofWifiCountry.isChecked,
        persistMagiskModule = opts.swPersistModule.isChecked,
        rilGuardLoop = opts.swRilGuard.isChecked,
        spoofLocale = opts.swSpoofLocale.isChecked,
        localeTag = opts.etLocaleTag.text?.toString()?.trim().orEmpty(),
        spoofTimezone = opts.swSpoofTimezone.isChecked,
        timezoneId = opts.etTimezoneId.text?.toString()?.trim().orEmpty(),
        forceStopTikTok = opts.swForceStop.isChecked,
        clearTikTokCache = opts.swClearCache.isChecked
    )

    private fun clearFieldErrors() {
        dash.tilSimCountryIso.error = null
        dash.tilNetCountryIso.error = null
        dash.tilSimMccMnc.error = null
        dash.tilNetMccMnc.error = null
    }

    private fun validateConfig(cfg: SpoofConfig): Boolean {
        clearFieldErrors()
        var valid = true
        if (cfg.simCountryIso.length != 2 || !cfg.simCountryIso.all { it.isLetter() }) {
            dash.tilSimCountryIso.error = getString(R.string.err_iso_format)
            valid = false
        }
        if (cfg.networkCountryIso.length != 2 || !cfg.networkCountryIso.all { it.isLetter() }) {
            dash.tilNetCountryIso.error = getString(R.string.err_iso_format)
            valid = false
        }
        if (cfg.simOperatorMccMnc.length < 5 || !cfg.simOperatorMccMnc.all { it.isDigit() }) {
            dash.tilSimMccMnc.error = getString(R.string.err_mccmnc_format)
            valid = false
        }
        if (cfg.networkOperatorMccMnc.length < 5 || !cfg.networkOperatorMccMnc.all { it.isDigit() }) {
            dash.tilNetMccMnc.error = getString(R.string.err_mccmnc_format)
            valid = false
        }
        if (!valid && !propertiesExpanded) {
            togglePropertiesSection(forceExpand = true)
        }
        return valid
    }

    private fun togglePropertiesSection(forceExpand: Boolean? = null) {
        val next = forceExpand ?: !propertiesExpanded
        if (next == propertiesExpanded) return
        propertiesExpanded = next
        MotionHelper.beginContainerTransition(dash.dashboardColumn)
        dash.layoutPropertiesContent.visibility = if (next) View.VISIBLE else View.GONE
        MotionHelper.rotateChevron(dash.ivPropertiesChevron, next)
    }

    private fun setupListeners() {
        dash.btnRefreshEnv.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            refreshEnvironment()
        }

        dash.headerProperties.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            togglePropertiesSection()
        }

        dash.btnOpenMagisk.setOnClickListener {
            val intent = packageManager.getLaunchIntentForPackage(MAGISK_PACKAGE)
            if (intent != null) {
                startActivity(intent)
            } else {
                showMessage(getString(R.string.magisk_app_not_found))
            }
        }

        dash.btnChooseCarrier.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            CarrierPickerBottomSheet()
                .show(supportFragmentManager, CarrierPickerBottomSheet.TAG)
        }

        opts.swPersistModule.setOnCheckedChangeListener { _, checked ->
            opts.swRilGuard.isEnabled = checked
            saveCurrentConfig()
        }

        fun bindConditionalInput(sw: MaterialSwitch, til: TextInputLayout) {
            sw.setOnCheckedChangeListener { _, checked ->
                MotionHelper.beginContainerTransition(opts.optionsColumn)
                til.visibility = if (checked) View.VISIBLE else View.GONE
                saveCurrentConfig()
            }
        }
        bindConditionalInput(opts.swSpoofLocale, opts.tilLocaleTag)
        bindConditionalInput(opts.swSpoofTimezone, opts.tilTimezoneId)

        listOf(
            opts.swMirrorDualSim,
            opts.swSpoofWifiCountry,
            opts.swRilGuard,
            opts.swForceStop,
            opts.swClearCache
        ).forEach { sw ->
            sw.setOnCheckedChangeListener { _, _ -> saveCurrentConfig() }
        }
        opts.etLocaleTag.doAfterTextChanged { saveCurrentConfig() }
        opts.etTimezoneId.doAfterTextChanged { saveCurrentConfig() }

        binding.fabApply.setOnClickListener { v ->
            val cfg = readConfigFromUi()
            if (!validateConfig(cfg)) return@setOnClickListener

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                v.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
            } else {
                v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            }

            SpoofConfigStore.save(this, cfg)
            setActionsEnabled(false)
            lifecycleScope.launch {
                val env = currentEnv ?: MagiskRootManager.inspectEnvironment(this@MainActivity)
                if (!env.hasRoot) {
                    setActionsEnabled(true)
                    showMessage(getString(R.string.msg_root_required))
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
                    val snack = Snackbar.make(
                        binding.rootCoordinator,
                        getString(R.string.msg_applied_format, cfg.simCountryIso.uppercase()),
                        Snackbar.LENGTH_LONG
                    )
                    if (env.installedTikTokPackages.isNotEmpty()) {
                        snack.setAction(R.string.action_launch) { launchTikTokFlow() }
                    }
                    snack.show()
                    refreshEnvironment()
                    verifyLiveDiagnostics()
                } else {
                    showMessage(getString(R.string.msg_apply_failed_format, result.exitCode))
                }
            }
        }

        dash.btnVerify.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            verifyLiveDiagnostics()
        }

        dash.btnRestore.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            MaterialAlertDialogBuilder(this)
                .setTitle(R.string.confirm_restore_title)
                .setMessage(R.string.confirm_restore_message)
                .setPositiveButton(R.string.action_restore) { _, _ -> performRestoreDefaults() }
                .setNegativeButton(R.string.action_cancel, null)
                .show()
        }

        dash.btnViewRootLog.setOnClickListener {
            MaterialAlertDialogBuilder(this)
                .setTitle(R.string.dialog_root_log_title)
                .setMessage(lastShellLog)
                .setPositiveButton(R.string.action_copy) { _, _ ->
                    val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    cm.setPrimaryClip(ClipData.newPlainText("MagiskRootLog", lastShellLog))
                    showMessage(getString(R.string.msg_log_copied))
                }
                .setNegativeButton(R.string.action_close, null)
                .show()
        }

        dash.btnLaunchTikTok.setOnClickListener {
            launchTikTokFlow()
        }
    }

    private fun performRestoreDefaults() {
        setActionsEnabled(false)
        lifecycleScope.launch {
            val env = currentEnv ?: MagiskRootManager.inspectEnvironment(this@MainActivity)
            val result = MagiskRootManager.restoreDefaults(env.installedTikTokPackages)
            recordShellLog("restoreDefaults", result)
            setActionsEnabled(true)
            if (result.isSuccess) {
                showMessage(getString(R.string.msg_restored))
                refreshEnvironment()
                verifyLiveDiagnostics()
            } else {
                showMessage(getString(R.string.msg_restore_failed))
            }
        }
    }

    private fun launchTikTokFlow() {
        val installed = currentEnv?.installedTikTokPackages.orEmpty()
        when {
            installed.isEmpty() -> showMessage(getString(R.string.msg_no_tiktok))
            installed.size == 1 -> launchPackage(installed.first().packageName)
            else -> {
                val labels = installed.map { it.label }.toTypedArray()
                MaterialAlertDialogBuilder(this)
                    .setTitle(R.string.launch_tiktok)
                    .setItems(labels) { _, which ->
                        launchPackage(installed[which].packageName)
                    }
                    .show()
            }
        }
    }

    private fun launchPackage(packageName: String) {
        val intent = packageManager.getLaunchIntentForPackage(packageName)
        if (intent != null) {
            startActivity(intent)
        } else {
            showMessage(getString(R.string.msg_unable_launch_format, packageName))
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
        refreshJob?.cancel()
        refreshJob = lifecycleScope.launch {
            val env = MagiskRootManager.inspectEnvironment(this@MainActivity)
            currentEnv = env

            dash.tvMagiskStatusTitle.text = getString(
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
            val onCardColor = MaterialColors.getColor(dash.cardMagiskStatus, iconColorAttr)
            MotionHelper.animateCardColor(
                dash.cardMagiskStatus,
                MaterialColors.getColor(dash.cardMagiskStatus, cardColorAttr)
            )
            dash.ivMagiskIcon.setColorFilter(onCardColor)
            dash.tvMagiskStatusTitle.setTextColor(onCardColor)
            dash.tvMagiskDetails.setTextColor(onCardColor)
            dash.tvMagiskGuidance.setTextColor(onCardColor)
            dash.btnRefreshEnv.iconTint = android.content.res.ColorStateList.valueOf(onCardColor)
            MotionHelper.beginContainerTransition(dash.dashboardColumn)
            val missingRootVisibility = if (env.hasRoot) View.GONE else View.VISIBLE
            dash.tvMagiskDetails.visibility = if (env.hasRoot) View.VISIBLE else View.GONE
            dash.tvMagiskGuidance.visibility = missingRootVisibility
            dash.btnOpenMagisk.visibility = missingRootVisibility

            if (env.hasRoot) {
                val modText = when (env.moduleStatus) {
                    ModuleStatus.ACTIVE -> getString(R.string.module_active)
                    ModuleStatus.DISABLED -> getString(R.string.module_disabled)
                    ModuleStatus.NOT_INSTALLED -> getString(R.string.module_not_installed)
                }
                dash.tvMagiskDetails.text = getString(
                    R.string.magisk_details_format,
                    env.magiskVersion,
                    env.simSlotCount,
                    modText
                )
            }

            dash.chipGroupTikTokApps.removeAllViews()
            if (env.installedTikTokPackages.isEmpty()) {
                dash.btnLaunchTikTok.visibility = View.GONE
                dash.chipGroupTikTokApps.addView(createTagChip(getString(R.string.no_tiktok_detected)))
            } else {
                dash.btnLaunchTikTok.visibility = View.VISIBLE
                env.installedTikTokPackages.forEach { pkg ->
                    dash.chipGroupTikTokApps.addView(createTagChip(pkg.label))
                }
            }
        }
    }

    private fun verifyLiveDiagnostics() {
        val targetIso = dash.etSimCountryIso.text?.toString()?.trim().orEmpty().lowercase()
        lifecycleScope.launch {
            val diag = MagiskRootManager.readLiveDiagnostics(this@MainActivity)

            val propMatches = diag.propSimCountry.lowercase().split(",").any { it == targetIso }
            val tmMatches = diag.tmSimCountry.lowercase() == targetIso

            MotionHelper.beginContainerTransition(dash.dashboardColumn)
            dash.tvDiagnosticsSummary.visibility = View.VISIBLE
            when {
                tmMatches && propMatches -> {
                    dash.tvDiagnosticsSummary.text = getString(
                        R.string.diag_effective_format,
                        targetIso.uppercase()
                    )
                    dash.tvDiagnosticsSummary.setTextColor(
                        ContextCompat.getColor(this@MainActivity, R.color.status_ok)
                    )
                }
                propMatches && !tmMatches -> {
                    dash.tvDiagnosticsSummary.text = getString(
                        R.string.diag_overridden_format,
                        targetIso.uppercase(),
                        diag.tmSimCountry.uppercase()
                    )
                    dash.tvDiagnosticsSummary.setTextColor(
                        ContextCompat.getColor(this@MainActivity, R.color.status_warn)
                    )
                }
                else -> {
                    dash.tvDiagnosticsSummary.text = getString(
                        R.string.diag_not_spoofed_format,
                        targetIso.uppercase(),
                        diag.propSimCountry,
                        diag.tmSimCountry
                    )
                    dash.tvDiagnosticsSummary.setTextColor(
                        ContextCompat.getColor(this@MainActivity, R.color.status_warn)
                    )
                }
            }

            dash.tvDiagnosticsOutput.text = buildString {
                appendLine(getString(R.string.telemetry_sim_format, diag.tmSimCountry, diag.propSimCountry))
                appendLine(getString(R.string.telemetry_net_format, diag.tmNetworkCountry, diag.propNetworkCountry))
                appendLine(getString(R.string.telemetry_op_format, diag.tmSimOperator, diag.tmSimOperatorName))
                append(getString(R.string.telemetry_sys_format, diag.activeLocale, diag.activeTimezone))
            }
        }
    }

    private fun setActionsEnabled(enabled: Boolean) {
        binding.fabApply.isEnabled = enabled
        binding.fabApply.setText(if (enabled) R.string.fab_apply else R.string.fab_applying)
        dash.btnVerify.isEnabled = enabled
        dash.btnRestore.isEnabled = enabled
    }

    private fun showMessage(msg: String) {
        Snackbar.make(binding.rootCoordinator, msg, Snackbar.LENGTH_LONG).show()
    }

    companion object {
        private const val MAGISK_PACKAGE = "com.topjohnwu.magisk"
        private const val KEY_ACTIVE_TAB = "active_tab_id"
        private const val KEY_SHELL_LOG = "shell_log"
    }
}
