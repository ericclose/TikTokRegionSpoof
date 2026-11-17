package dev.magisk.tiktokregionspoof.root

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.telephony.TelephonyManager
import dev.magisk.tiktokregionspoof.model.SpoofConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale
import java.util.TimeZone

data class ShellResult(
    val exitCode: Int,
    val stdout: String,
    val stderr: String
) {
    val isSuccess: Boolean get() = exitCode == 0
}

enum class ModuleStatus {
    ACTIVE,
    DISABLED,
    NOT_INSTALLED
}

enum class RootImplementation(val displayName: String) {
    MAGISK("Magisk"),
    KERNELSU("KernelSU"),
    UNKNOWN("Root"),
    NONE("Unavailable")
}

data class RootManagerApp(
    val packageName: String,
    val label: String
)

data class MagiskEnvironment(
    val hasRoot: Boolean,
    val magiskVersion: String,
    val hasResetprop: Boolean,
    val moduleStatus: ModuleStatus,
    val simSlotCount: Int,
    val installedTikTokPackages: List<TikTokPackageInfo>,
    val rootImplementation: RootImplementation = RootImplementation.NONE
)

data class TikTokPackageInfo(
    val packageName: String,
    val label: String
)

data class LiveDiagnostics(
    val tmSimCountry: String,
    val tmNetworkCountry: String,
    val tmSimOperator: String,
    val tmNetworkOperator: String,
    val tmSimOperatorName: String,
    val tmNetworkOperatorName: String,
    val propSimCountry: String,
    val propNetworkCountry: String,
    val propSimOperator: String,
    val propNetworkOperator: String,
    val propSimOperatorName: String,
    val propNetworkOperatorName: String,
    val activeLocale: String,
    val activeTimezone: String
)

object MagiskRootManager {

    const val MODULE_ID = "tiktok_region_spoof"
    const val MODULE_DIR = "/data/adb/modules/$MODULE_ID"

    private val ROOT_MANAGER_PACKAGES = listOf(
        "me.weishu.kernelsu" to "KernelSU",
        "com.rifsxd.ksunext" to "KernelSU Next",
        "com.topjohnwu.magisk" to "Magisk"
    )

    private val TIKTOK_TARGETS = listOf(
        "com.zhiliaoapp.musically" to "TikTok Global"
    )

    suspend fun runSu(script: String): ShellResult = withContext(Dispatchers.IO) {
        runCatching {
            val process = ProcessBuilder("su", "-M", "-c", "sh")
                .redirectErrorStream(false)
                .start()
            process.outputStream.bufferedWriter().use { writer ->
                writer.write(script)
                writer.write("\nexit\n")
                writer.flush()
            }
            val stdout = process.inputStream.bufferedReader().readText().trim()
            val stderr = process.errorStream.bufferedReader().readText().trim()
            val code = process.waitFor()
            ShellResult(code, stdout, stderr)
        }.getOrElse { err ->
            ShellResult(-1, "", err.message ?: "Failed to invoke su")
        }
    }

    private fun isPackageInstalled(pm: PackageManager, pkg: String): Boolean =
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getPackageInfo(pkg, PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageInfo(pkg, 0)
            }
            true
        }.getOrDefault(false)

    fun detectInstalledRootManagers(context: Context): List<RootManagerApp> {
        val pm = context.packageManager
        return ROOT_MANAGER_PACKAGES.mapNotNull { (pkg, label) ->
            if (isPackageInstalled(pm, pkg)) RootManagerApp(pkg, label) else null
        }
    }

    fun detectInstalledTikTokPackages(context: Context): List<TikTokPackageInfo> {
        val pm = context.packageManager
        return TIKTOK_TARGETS.mapNotNull { (pkg, label) ->
            if (isPackageInstalled(pm, pkg)) TikTokPackageInfo(pkg, label) else null
        }
    }

    suspend fun inspectEnvironment(context: Context): MagiskEnvironment {
        val installed = detectInstalledTikTokPackages(context)
        val probe = runSu(
            """
            id -u
            if command -v ksud >/dev/null 2>&1 || [ -d /data/adb/ksu ]; then
              echo "KERNELSU"
              ksud -V 2>/dev/null || su -v 2>/dev/null || echo "KernelSU"
            elif command -v magisk >/dev/null 2>&1; then
              echo "MAGISK"
              magisk -v 2>/dev/null || echo "Magisk"
            else
              echo "UNKNOWN"
              su -v 2>/dev/null || echo "Unknown"
            fi
            command -v resetprop >/dev/null 2>&1 && echo "yes" || ([ -x /data/adb/ksu/bin/resetprop ] && echo "yes" || (magisk --list 2>/dev/null | grep -q resetprop && echo "applet" || echo "no"))
            if [ -d "$MODULE_DIR" ]; then
              if [ -f "$MODULE_DIR/disable" ]; then echo "DISABLED"; else echo "ACTIVE"; fi
            else
              echo "NOT_INSTALLED"
            fi
            getprop gsm.sim.operator.iso-country
            getprop ro.telephony.sim_slots.count
            getprop persist.radio.multisim.config
            """.trimIndent()
        )
        return parseEnvironmentProbe(probe, installed)
    }

    internal fun parseEnvironmentProbe(
        probe: ShellResult,
        installedPackages: List<TikTokPackageInfo>
    ): MagiskEnvironment {
        if (!probe.isSuccess) {
            return MagiskEnvironment(
                hasRoot = false,
                magiskVersion = "Unavailable",
                hasResetprop = false,
                moduleStatus = ModuleStatus.NOT_INSTALLED,
                simSlotCount = 1,
                installedTikTokPackages = installedPackages,
                rootImplementation = RootImplementation.NONE
            )
        }

        val lines = probe.stdout.lines()
        val uid = lines.getOrNull(0)?.trim()
        val hasRoot = uid == "0"
        val impl = when (lines.getOrNull(1)?.trim()) {
            "KERNELSU" -> RootImplementation.KERNELSU
            "MAGISK" -> RootImplementation.MAGISK
            else -> if (hasRoot) RootImplementation.UNKNOWN else RootImplementation.NONE
        }
        val rootVer = lines.getOrNull(2)?.trim().orEmpty().ifEmpty { impl.displayName }
        val resetpropFlag = lines.getOrNull(3)?.trim().orEmpty()
        val hasResetprop = resetpropFlag == "yes" || resetpropFlag == "applet"
        val modState = when (lines.getOrNull(4)?.trim()) {
            "ACTIVE" -> ModuleStatus.ACTIVE
            "DISABLED" -> ModuleStatus.DISABLED
            else -> ModuleStatus.NOT_INSTALLED
        }

        val simIsoProp = lines.getOrNull(5)?.trim().orEmpty()
        val slotProp = lines.getOrNull(6)?.trim()?.toIntOrNull()
        val multiSimCfg = lines.getOrNull(7)?.trim().orEmpty()
        val slotCount = when {
            simIsoProp.contains(",") -> simIsoProp.split(",").size.coerceIn(1, 2)
            slotProp != null && slotProp > 1 -> slotProp.coerceIn(1, 2)
            multiSimCfg == "dsds" || multiSimCfg == "dsda" -> 2
            else -> 1
        }

        return MagiskEnvironment(
            hasRoot = hasRoot,
            magiskVersion = rootVer,
            hasResetprop = hasResetprop,
            moduleStatus = modState,
            simSlotCount = slotCount,
            installedTikTokPackages = installedPackages,
            rootImplementation = impl
        )
    }

    suspend fun applySpoof(
        config: SpoofConfig,
        slotCount: Int,
        installedPackages: List<TikTokPackageInfo>
    ): ShellResult = runSu(buildApplyScript(config, slotCount, installedPackages))

    internal fun buildApplyScript(
        config: SpoofConfig,
        slotCount: Int,
        installedPackages: List<TikTokPackageInfo>
    ): String {
        val slots = if (config.mirrorDualSim && slotCount > 1) slotCount else 1

        val simIso = formatSlotValue(config.simCountryIso.lowercase().trim(), slots)
        val netIso = formatSlotValue(config.networkCountryIso.lowercase().trim(), slots)
        val simNum = formatSlotValue(config.simOperatorMccMnc.trim(), slots)
        val netNum = formatSlotValue(config.networkOperatorMccMnc.trim(), slots)
        val simAlpha = formatSlotValue(config.simOperatorName.trim(), slots)
        val netAlpha = formatSlotValue(config.networkOperatorName.trim(), slots)
        val simState = formatSlotValue("READY", slots)
        val phoneType = formatSlotValue("1", slots)
        val netType = formatSlotValue("LTE", slots)
        val roaming = formatSlotValue("false", slots)

        val props = buildList {
            add("gsm.sim.operator.iso-country" to simIso)
            add("gsm.operator.iso-country" to netIso)
            add("gsm.sim.operator.numeric" to simNum)
            add("gsm.operator.numeric" to netNum)
            add("gsm.sim.operator.alpha" to simAlpha)
            add("gsm.operator.alpha" to netAlpha)
            add("gsm.sim.state" to simState)
            add("gsm.current.phone-type" to phoneType)
            add("gsm.network.type" to netType)
            add("gsm.operator.isroaming" to roaming)
            if (config.spoofLocale && config.localeTag.isNotBlank()) {
                add("persist.sys.locale" to config.localeTag.trim())
                add("ro.product.locale" to config.localeTag.trim())
            }
            if (config.spoofTimezone && config.timezoneId.isNotBlank()) {
                add("persist.sys.timezone" to config.timezoneId.trim())
            }
        }

        val resetpropLookup = "RESETPROP=\$(command -v resetprop || ([ -x /data/adb/ksu/bin/resetprop ] && echo '/data/adb/ksu/bin/resetprop') || echo 'magisk resetprop')"
        val propCommands = props.joinToString("\n") { (k, v) ->
            "\$RESETPROP -n ${shQuote(k)} ${shQuote(v)} || PROP_FAILED=1"
        }
        val wifiLine = if (config.spoofWifiCountry && config.simCountryIso.length == 2) {
            "cmd wifi force-country-code enabled ${shQuote(config.simCountryIso.uppercase())} >/dev/null 2>&1 || true"
        } else {
            ""
        }

        val sb = StringBuilder()
        sb.appendLine(resetpropLookup)
        sb.appendLine("APPLY_STATUS=0")
        sb.appendLine("if [ -f ${shQuote("$MODULE_DIR/guard.pid")} ]; then")
        sb.appendLine("  kill \$(cat ${shQuote("$MODULE_DIR/guard.pid")}) >/dev/null 2>&1 || true")
        sb.appendLine("  rm -f ${shQuote("$MODULE_DIR/guard.pid")}")
        sb.appendLine("fi")

        if (config.spoofTimezone && config.timezoneId.isNotBlank()) {
            sb.appendLine("settings put global auto_time_zone 0 >/dev/null 2>&1 || true")
            sb.appendLine("service call alarm 3 s16 ${shQuote(config.timezoneId.trim())} >/dev/null 2>&1 || true")
        }

        if (config.persistMagiskModule) {
            val systemPropContent = props.joinToString("\n") { (k, v) -> "$k=$v" }
            val modulePropContent = """
                id=$MODULE_ID
                name=TikTok Region Spoof
                version=v1.0.0
                versionCode=1
                author=TikTokRegionSpoof
                description=Spoofs SIM/network telephony properties via resetprop (${config.simCountryIso.uppercase()} / ${config.simOperatorName})
            """.trimIndent()

            val applyPropsSh = buildString {
                appendLine("#!/system/bin/sh")
                appendLine(resetpropLookup)
                appendLine("PROP_FAILED=0")
                appendLine(propCommands)
                if (wifiLine.isNotEmpty()) appendLine(wifiLine)
                appendLine("exit \"\$PROP_FAILED\"")
            }.trimEnd()

            val guardScriptContent = """
                #!/system/bin/sh
                MODDIR=${'$'}{0%/*}
                $resetpropLookup
                echo ${'$'}${'$'} > "${'$'}MODDIR/guard.pid"
                while true; do
                  CUR=${'$'}(${'$'}RESETPROP gsm.sim.operator.iso-country 2>/dev/null)
                  if [ "${'$'}CUR" != ${shQuote(simIso)} ]; then
                    sh "${'$'}MODDIR/apply_props.sh"
                  fi
                  ${'$'}RESETPROP -w gsm.sim.operator.iso-country ${shQuote(simIso)} >/dev/null 2>&1 || sleep 5
                done
            """.trimIndent()

            val serviceShContent = """
                #!/system/bin/sh
                MODDIR=${'$'}{0%/*}
                until [ "${'$'}(getprop sys.boot_completed)" = "1" ]; do
                  sleep 2
                done
                sleep 2
                sh "${'$'}MODDIR/apply_props.sh"
                if [ -f "${'$'}MODDIR/guard.pid" ]; then
                  kill ${'$'}(cat "${'$'}MODDIR/guard.pid") >/dev/null 2>&1 || true
                fi
                ${if (config.rilGuardLoop) "nohup sh \"\$MODDIR/guard.sh\" >/dev/null 2>&1 &" else ""}
            """.trimIndent()

            sb.appendLine("mkdir -p ${shQuote(MODULE_DIR)}")
            sb.appendLine("rm -f ${shQuote("$MODULE_DIR/disable")} ${shQuote("$MODULE_DIR/remove")}")
            sb.appendLine("cat <<'EOF_MODPROP' > ${shQuote("$MODULE_DIR/module.prop")}")
            sb.appendLine(modulePropContent)
            sb.appendLine("EOF_MODPROP")
            sb.appendLine("cat <<'EOF_SYSPROP' > ${shQuote("$MODULE_DIR/system.prop")}")
            sb.appendLine(systemPropContent)
            sb.appendLine("EOF_SYSPROP")
            sb.appendLine("cat <<'EOF_APPLY' > ${shQuote("$MODULE_DIR/apply_props.sh")}")
            sb.appendLine(applyPropsSh)
            sb.appendLine("EOF_APPLY")
            sb.appendLine("cat <<'EOF_GUARD' > ${shQuote("$MODULE_DIR/guard.sh")}")
            sb.appendLine(guardScriptContent)
            sb.appendLine("EOF_GUARD")
            sb.appendLine("cat <<'EOF_SVC' > ${shQuote("$MODULE_DIR/service.sh")}")
            sb.appendLine(serviceShContent)
            sb.appendLine("EOF_SVC")
            sb.appendLine("chmod 0644 ${shQuote("$MODULE_DIR/module.prop")} ${shQuote("$MODULE_DIR/system.prop")}")
            sb.appendLine("chmod 0755 ${shQuote("$MODULE_DIR/apply_props.sh")} ${shQuote("$MODULE_DIR/guard.sh")} ${shQuote("$MODULE_DIR/service.sh")}")
            sb.appendLine("sh ${shQuote("$MODULE_DIR/apply_props.sh")} || APPLY_STATUS=1")

            if (config.rilGuardLoop) {
                sb.appendLine("nohup sh ${shQuote("$MODULE_DIR/guard.sh")} >/dev/null 2>&1 &")
            }
        } else {
            sb.appendLine("PROP_FAILED=0")
            sb.appendLine(propCommands)
            sb.appendLine("if [ \"\$PROP_FAILED\" -ne 0 ]; then APPLY_STATUS=1; fi")
            if (wifiLine.isNotEmpty()) sb.appendLine(wifiLine)
        }

        if (config.forceStopTikTok) {
            for (pkg in installedPackages) {
                sb.appendLine("am force-stop ${shQuote(pkg.packageName)} >/dev/null 2>&1 || true")
                if (config.clearTikTokCache) {
                    val pkgDataDir = listOf("", "data", "user", "0", pkg.packageName).joinToString("/")
                    val cacheDir = shQuote("$pkgDataDir/cache")
                    val codeCacheDir = shQuote("$pkgDataDir/code_cache")
                    sb.appendLine("rm -rf $cacheDir/* $codeCacheDir/* >/dev/null 2>&1 || true")
                }
            }
        }

        sb.appendLine("exit \"\$APPLY_STATUS\"")
        return sb.toString()
    }

    suspend fun restoreDefaults(installedPackages: List<TikTokPackageInfo>): ShellResult =
        runSu(buildRestoreScript(installedPackages))

    internal fun buildRestoreScript(installedPackages: List<TikTokPackageInfo>): String {
        val sb = StringBuilder()
        sb.appendLine("if [ -f ${shQuote("$MODULE_DIR/guard.pid")} ]; then")
        sb.appendLine("  kill \$(cat ${shQuote("$MODULE_DIR/guard.pid")}) >/dev/null 2>&1 || true")
        sb.appendLine("fi")
        sb.appendLine("rm -rf ${shQuote(MODULE_DIR)}")
        sb.appendLine("cmd wifi force-country-code disabled >/dev/null 2>&1 || true")
        sb.appendLine("settings put global auto_time_zone 1 >/dev/null 2>&1 || true")
        for (pkg in installedPackages) {
            sb.appendLine("am force-stop ${shQuote(pkg.packageName)} >/dev/null 2>&1 || true")
        }
        return sb.toString()
    }

    suspend fun readLiveDiagnostics(context: Context): LiveDiagnostics {
        val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
        val propResult = runSu(
            """
            RESETPROP=${'$'}(command -v resetprop || echo 'getprop')
            echo "${'$'}(${'$'}RESETPROP gsm.sim.operator.iso-country)"
            echo "${'$'}(${'$'}RESETPROP gsm.operator.iso-country)"
            echo "${'$'}(${'$'}RESETPROP gsm.sim.operator.numeric)"
            echo "${'$'}(${'$'}RESETPROP gsm.operator.numeric)"
            echo "${'$'}(${'$'}RESETPROP gsm.sim.operator.alpha)"
            echo "${'$'}(${'$'}RESETPROP gsm.operator.alpha)"
            """.trimIndent()
        )
        val lines = if (propResult.isSuccess) propResult.stdout.lines() else emptyList()

        return LiveDiagnostics(
            tmSimCountry = tm?.simCountryIso.orEmpty().ifEmpty { "(empty)" },
            tmNetworkCountry = tm?.networkCountryIso.orEmpty().ifEmpty { "(empty)" },
            tmSimOperator = tm?.simOperator.orEmpty().ifEmpty { "(empty)" },
            tmNetworkOperator = tm?.networkOperator.orEmpty().ifEmpty { "(empty)" },
            tmSimOperatorName = tm?.simOperatorName.orEmpty().ifEmpty { "(empty)" },
            tmNetworkOperatorName = tm?.networkOperatorName.orEmpty().ifEmpty { "(empty)" },
            propSimCountry = lines.getOrNull(0).orEmpty().ifEmpty { "(empty)" },
            propNetworkCountry = lines.getOrNull(1).orEmpty().ifEmpty { "(empty)" },
            propSimOperator = lines.getOrNull(2).orEmpty().ifEmpty { "(empty)" },
            propNetworkOperator = lines.getOrNull(3).orEmpty().ifEmpty { "(empty)" },
            propSimOperatorName = lines.getOrNull(4).orEmpty().ifEmpty { "(empty)" },
            propNetworkOperatorName = lines.getOrNull(5).orEmpty().ifEmpty { "(empty)" },
            activeLocale = Locale.getDefault().toLanguageTag(),
            activeTimezone = TimeZone.getDefault().id
        )
    }

    internal fun formatSlotValue(value: String, slots: Int): String =
        if (slots > 1) List(slots) { value }.joinToString(",") else value

    internal fun shQuote(value: String): String =
        "'" + value.replace("'", "'\\''") + "'"
}
