package com.smartbluetoothsleeptracker.core.util

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import java.util.Locale

object AutoStartHelper {

    fun isAutoStartOrBatteryOptimizationExempt(context: Context): Boolean {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? android.os.PowerManager
        return powerManager?.isIgnoringBatteryOptimizations(context.packageName) ?: false
    }

    fun openAutoStartSettings(context: Context) {
        val manufacturer = Build.MANUFACTURER.lowercase(Locale.ROOT)
        val intentsToTry = mutableListOf<Intent>()

        when {
            manufacturer.contains("xiaomi") || manufacturer.contains("redmi") || manufacturer.contains("poco") -> {
                intentsToTry.add(
                    Intent().setComponent(
                        ComponentName(
                            "com.miui.securitycenter",
                            "com.miui.permcenter.autostart.AutoStartManagementActivity"
                        )
                    )
                )
            }
            manufacturer.contains("oppo") || manufacturer.contains("realme") -> {
                intentsToTry.add(
                    Intent().setComponent(
                        ComponentName(
                            "com.coloros.safecenter",
                            "com.coloros.safecenter.permission.startup.StartupAppListActivity"
                        )
                    )
                )
                intentsToTry.add(
                    Intent().setComponent(
                        ComponentName(
                            "com.coloros.safecenter",
                            "com.coloros.safecenter.startupapp.StartupAppListActivity"
                        )
                    )
                )
                intentsToTry.add(
                    Intent().setComponent(
                        ComponentName(
                            "com.oplus.safecenter",
                            "com.oplus.safecenter.startupapp.StartupAppListActivity"
                        )
                    )
                )
            }
            manufacturer.contains("vivo") || manufacturer.contains("iqoo") -> {
                intentsToTry.add(
                    Intent().setComponent(
                        ComponentName(
                            "com.vivo.permissionmanager",
                            "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"
                        )
                    )
                )
                intentsToTry.add(
                    Intent().setComponent(
                        ComponentName(
                            "com.iqoo.secure",
                            "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity"
                        )
                    )
                )
            }
            manufacturer.contains("oneplus") -> {
                intentsToTry.add(
                    Intent().setComponent(
                        ComponentName(
                            "com.oneplus.security",
                            "com.oneplus.security.chainlaunch.view.ChainLaunchAppListActivity"
                        )
                    )
                )
            }
            manufacturer.contains("samsung") -> {
                intentsToTry.add(
                    Intent().setComponent(
                        ComponentName(
                            "com.samsung.android.lool",
                            "com.samsung.android.sm.ui.battery.BatteryActivity"
                        )
                    )
                )
                intentsToTry.add(
                    Intent().setComponent(
                        ComponentName(
                            "com.samsung.android.sm",
                            "com.samsung.android.sm.ui.battery.BatteryActivity"
                        )
                    )
                )
            }
            manufacturer.contains("huawei") || manufacturer.contains("honor") -> {
                intentsToTry.add(
                    Intent().setComponent(
                        ComponentName(
                            "com.huawei.systemmanager",
                            "com.huawei.systemmanager.optimize.process.ProtectActivity"
                        )
                    )
                )
                intentsToTry.add(
                    Intent().setComponent(
                        ComponentName(
                            "com.huawei.systemmanager",
                            "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"
                        )
                    )
                )
            }
            manufacturer.contains("asus") -> {
                intentsToTry.add(
                    Intent().setComponent(
                        ComponentName(
                            "com.asus.mobilemanager",
                            "com.asus.mobilemanager.autostart.AutoStartActivity"
                        )
                    )
                )
            }
        }

        // Generic battery optimization intent
        intentsToTry.add(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
        intentsToTry.add(
            Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:${context.packageName}")
            )
        )

        var launched = false
        for (intent in intentsToTry) {
            try {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                launched = true
                Toast.makeText(
                    context,
                    "Allow SleepBT in Auto-Start and Background Activity to prevent timer interruption",
                    Toast.LENGTH_LONG
                ).show()
                break
            } catch (_: Exception) {
                // Try next intent
            }
        }

        if (!launched) {
            try {
                val appSettingsIntent = Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:${context.packageName}")
                ).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(appSettingsIntent)
            } catch (_: Exception) {
                Toast.makeText(context, "Could not open system settings", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
