package com.example.scheduler

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.annotation.StringRes
import com.example.R
import java.util.Locale

enum class OemVendor(
    val id: String,
    @param:StringRes val chipLabelRes: Int,
    @param:StringRes val fullNameRes: Int,
    @param:StringRes val skinNameRes: Int,
    @param:StringRes val step1Res: Int,
    @param:StringRes val step2Res: Int,
    @param:StringRes val step3Res: Int
) {
    XIAOMI(
        id = "xiaomi",
        chipLabelRes = R.string.vendor_xiaomi_name,
        fullNameRes = R.string.vendor_xiaomi_full,
        skinNameRes = R.string.vendor_xiaomi_skin,
        step1Res = R.string.vendor_xiaomi_step_1,
        step2Res = R.string.vendor_xiaomi_step_2,
        step3Res = R.string.vendor_xiaomi_step_3
    ),
    SAMSUNG(
        id = "samsung",
        chipLabelRes = R.string.vendor_samsung_name,
        fullNameRes = R.string.vendor_samsung_full,
        skinNameRes = R.string.vendor_samsung_skin,
        step1Res = R.string.vendor_samsung_step_1,
        step2Res = R.string.vendor_samsung_step_2,
        step3Res = R.string.vendor_samsung_step_3
    ),
    OPPO_ONEPLUS(
        id = "oppo_oneplus",
        chipLabelRes = R.string.vendor_oppo_name,
        fullNameRes = R.string.vendor_oppo_full,
        skinNameRes = R.string.vendor_oppo_skin,
        step1Res = R.string.vendor_oppo_step_1,
        step2Res = R.string.vendor_oppo_step_2,
        step3Res = R.string.vendor_oppo_step_3
    ),
    VIVO(
        id = "vivo",
        chipLabelRes = R.string.vendor_vivo_name,
        fullNameRes = R.string.vendor_vivo_full,
        skinNameRes = R.string.vendor_vivo_skin,
        step1Res = R.string.vendor_vivo_step_1,
        step2Res = R.string.vendor_vivo_step_2,
        step3Res = R.string.vendor_vivo_step_3
    ),
    HUAWEI(
        id = "huawei",
        chipLabelRes = R.string.vendor_huawei_name,
        fullNameRes = R.string.vendor_huawei_full,
        skinNameRes = R.string.vendor_huawei_skin,
        step1Res = R.string.vendor_huawei_step_1,
        step2Res = R.string.vendor_huawei_step_2,
        step3Res = R.string.vendor_huawei_step_3
    ),
    ASUS(
        id = "asus",
        chipLabelRes = R.string.vendor_asus_name,
        fullNameRes = R.string.vendor_asus_full,
        skinNameRes = R.string.vendor_asus_skin,
        step1Res = R.string.vendor_asus_step_1,
        step2Res = R.string.vendor_asus_step_2,
        step3Res = R.string.vendor_asus_step_3
    ),
    STOCK_ANDROID(
        id = "stock",
        chipLabelRes = R.string.vendor_stock_name,
        fullNameRes = R.string.vendor_stock_full,
        skinNameRes = R.string.vendor_stock_skin,
        step1Res = R.string.vendor_stock_step_1,
        step2Res = R.string.vendor_stock_step_2,
        step3Res = R.string.vendor_stock_step_3
    )
}

enum class SettingsLaunchResult {
    OPENED_VENDOR_SETTINGS,
    OPENED_APP_INFO,
    FAILED
}

object OemBackgroundHelper {

    fun detectDeviceVendor(
        manufacturer: String = Build.MANUFACTURER.orEmpty(),
        brand: String = Build.BRAND.orEmpty()
    ): OemVendor {
        val combined = "${manufacturer.lowercase(Locale.ROOT)} ${brand.lowercase(Locale.ROOT)}"
        return when {
            combined.contains("xiaomi") ||
                combined.contains("redmi") ||
                combined.contains("poco") ||
                combined.contains("blackshark") -> OemVendor.XIAOMI

            combined.contains("samsung") -> OemVendor.SAMSUNG

            combined.contains("oppo") ||
                combined.contains("realme") ||
                combined.contains("oneplus") -> OemVendor.OPPO_ONEPLUS

            combined.contains("vivo") ||
                combined.contains("iqoo") -> OemVendor.VIVO

            combined.contains("huawei") ||
                combined.contains("honor") -> OemVendor.HUAWEI

            combined.contains("asus") -> OemVendor.ASUS

            else -> OemVendor.STOCK_ANDROID
        }
    }

    fun formatDetectedBrandLabel(
        manufacturer: String = Build.MANUFACTURER.orEmpty(),
        brand: String = Build.BRAND.orEmpty()
    ): String {
        val raw = manufacturer.trim().ifEmpty { brand.trim() }.ifEmpty { "Android" }
        return raw.replaceFirstChar { ch ->
            if (ch.isLowerCase()) ch.titlecase(Locale.getDefault()) else ch.toString()
        }
    }

    fun openAppBatteryDetails(context: Context): SettingsLaunchResult {
        val appDetailsIntent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", context.packageName, null)
        }
        if (tryStartActivity(context, appDetailsIntent)) {
            return SettingsLaunchResult.OPENED_APP_INFO
        }
        val generalSettings = Intent(Settings.ACTION_SETTINGS)
        return if (tryStartActivity(context, generalSettings)) {
            SettingsLaunchResult.OPENED_APP_INFO
        } else {
            SettingsLaunchResult.FAILED
        }
    }

    fun openVendorBackgroundSettings(context: Context, vendor: OemVendor): SettingsLaunchResult {
        val pkg = context.packageName
        val appLabel = context.getString(R.string.app_name)
        val candidates = buildVendorCandidateIntents(vendor, pkg, appLabel)

        for (intent in candidates) {
            if (tryStartActivity(context, intent)) {
                return SettingsLaunchResult.OPENED_VENDOR_SETTINGS
            }
        }

        // Standard Android or ROMs where proprietary activity is unexported -> open App Info directly
        return openAppBatteryDetails(context)
    }

    internal fun buildVendorCandidateIntents(
        vendor: OemVendor,
        packageName: String,
        appLabel: String
    ): List<Intent> {
        return when (vendor) {
            OemVendor.XIAOMI -> listOf(
                componentIntent(
                    "com.miui.securitycenter",
                    "com.miui.permcenter.autostart.AutoStartManagementActivity"
                ),
                componentIntent(
                    "com.miui.powerkeeper",
                    "com.miui.powerkeeper.ui.HiddenAppsConfigActivity"
                ).apply {
                    putExtra("package_name", packageName)
                    putExtra("package_label", appLabel)
                },
                componentIntent(
                    "com.miui.securitycenter",
                    "com.miui.powercenter.PowerSettings"
                )
            )

            OemVendor.SAMSUNG -> listOf(
                componentIntent(
                    "com.samsung.android.lool",
                    "com.samsung.android.sm.battery.ui.BatteryActivity"
                ),
                componentIntent(
                    "com.samsung.android.lool",
                    "com.samsung.android.sm.ui.battery.BatteryActivity"
                ),
                componentIntent(
                    "com.samsung.android.sm",
                    "com.samsung.android.sm.battery.ui.BatteryActivity"
                ),
                componentIntent(
                    "com.samsung.android.sm",
                    "com.samsung.android.sm.ui.battery.BatteryActivity"
                )
            )

            OemVendor.OPPO_ONEPLUS -> listOf(
                componentIntent(
                    "com.coloros.safecenter",
                    "com.coloros.safecenter.permission.startup.StartupAppListActivity"
                ),
                componentIntent(
                    "com.coloros.safecenter",
                    "com.coloros.safecenter.startupapp.StartupAppListActivity"
                ),
                componentIntent(
                    "com.oplus.battery",
                    "com.oplus.powermanager.fuelgaue.PowerControlActivity"
                ),
                componentIntent(
                    "com.coloros.oppoguardelf",
                    "com.coloros.powermanager.fuelgaue.PowerUsageModelActivity"
                ),
                componentIntent(
                    "com.oneplus.security",
                    "com.oneplus.security.chainlaunch.view.ChainLaunchAppListActivity"
                ),
                componentIntent(
                    "com.oneplus.security",
                    "com.oneplus.security.chainlaunch.view.ChainLaunchAppListAct"
                )
            )

            OemVendor.VIVO -> listOf(
                componentIntent(
                    "com.vivo.permissionmanager",
                    "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"
                ),
                componentIntent(
                    "com.iqoo.secure",
                    "com.iqoo.secure.ui.phoneoptimize.BgStartUpManager"
                ),
                componentIntent(
                    "com.iqoo.secure",
                    "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity"
                ),
                componentIntent(
                    "com.vivo.abe",
                    "com.vivo.applicationbehaviorengine.ui.ExcessivePowerManagerActivity"
                )
            )

            OemVendor.HUAWEI -> listOf(
                componentIntent(
                    "com.huawei.systemmanager",
                    "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"
                ),
                componentIntent(
                    "com.huawei.systemmanager",
                    "com.huawei.systemmanager.appcontrol.activity.StartupAppControlActivity"
                ),
                componentIntent(
                    "com.huawei.systemmanager",
                    "com.huawei.systemmanager.optimize.process.ProtectActivity"
                ),
                componentIntent(
                    "com.hihonor.systemmanager",
                    "com.hihonor.systemmanager.startupmgr.ui.StartupNormalAppListActivity"
                )
            )

            OemVendor.ASUS -> listOf(
                componentIntent(
                    "com.asus.mobilemanager",
                    "com.asus.mobilemanager.autostart.AutoStartActivity"
                ),
                componentIntent(
                    "com.asus.mobilemanager",
                    "com.asus.mobilemanager.powersaver.PowerSaverSettings"
                ),
                componentIntent(
                    "com.asus.mobilemanager",
                    "com.asus.mobilemanager.MainActivity"
                )
            )

            OemVendor.STOCK_ANDROID -> listOf(
                Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
            )
        }
    }

    private fun componentIntent(pkg: String, cls: String): Intent {
        return Intent().apply {
            component = ComponentName(pkg, cls)
        }
    }

    private fun tryStartActivity(context: Context, intent: Intent): Boolean {
        return runCatching {
            val launchIntent = Intent(intent).apply {
                if (context !is Activity) {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            }
            context.startActivity(launchIntent)
            true
        }.getOrDefault(false)
    }
}
