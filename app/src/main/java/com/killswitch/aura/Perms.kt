package com.killswitch.aura

import android.app.Activity
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings

data class Check(val title: String, val hint: String, val ok: Boolean)

/** A one-tap repair shown on the Setup tab. */
data class Fix(val id: String, val title: String, val hint: String)

object Perms {
    private const val SAMSUNG_CONFIRMED = "samsungNeverSleepingConfirmed"
    private const val SAMSUNG_PENDING = "samsungNeverSleepingPending"

    val FIXES = listOf(
        Fix("rf", "Re-arm all alarms", "Reschedule every alarm and backup"),
        Fix("lk", "Check exact-alarm access", "Verify Alarms & reminders"),
        Fix("ph", "Open app battery settings", "Review manufacturer-specific background restrictions"),
        Fix("vo", "Raise alarm volume", "Set it to a loud level, or open Bluetooth if that is the problem"),
    )

    /** Samsung's extra sleeping-app list is not present on other Android manufacturers. */
    fun isSamsungDevice(): Boolean =
        Build.MANUFACTURER.equals("samsung", ignoreCase = true) ||
            Build.BRAND.equals("samsung", ignoreCase = true)

    /** True only after the user explicitly confirms the Samsung setting; opening Settings is not confirmation. */
    fun samsungConfirmationPending(c: Context): Boolean =
        isSamsungDevice() && Store.get(c, SAMSUNG_PENDING) == "1"

    fun confirmSamsung(c: Context) {
        if (!isSamsungDevice() || !samsungConfirmationPending(c)) return
        Store.set(c, SAMSUNG_CONFIRMED, "1")
        Store.set(c, SAMSUNG_PENDING, "0")
    }

    /** Order matches the Setup tab: exact alarms, notifications, full-screen, Samsung-only, battery, DND, sound. */
    fun check(c: Context): List<Check> {
        val nm = c.getSystemService(NotificationManager::class.java)
        val au = c.getSystemService(AudioManager::class.java)
        val samsung = isSamsungDevice()
        return listOf(
            Check("Exact alarms", "“Alarms & reminders” access", Scheduler.canExact(c)),
            Check("Notifications", "Needed for the alarm and warnings", nm.areNotificationsEnabled()),
            Check("Full-screen alarm", "Ring over the lock screen", Build.VERSION.SDK_INT < 34 || nm.canUseFullScreenIntent()),
            Check(
                if (samsung) "Samsung: Never sleeping apps" else "Manufacturer battery settings",
                if (samsung) "Manual check: add Aura to Never sleeping apps, return here, then confirm."
                else "No Samsung-only list applies. If alarms are delayed, allow background activity in app info; menu names vary by manufacturer.",
                !samsung || Store.get(c, SAMSUNG_CONFIRMED) == "1",
            ),
            Check(
                "Battery: Unrestricted",
                "Stops Android applying standard battery optimization to Aura",
                c.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(c.packageName),
            ),
            Check(
                "Do Not Disturb",
                "Allow alarms through",
                nm.currentInterruptionFilter != NotificationManager.INTERRUPTION_FILTER_NONE,
            ),
            Check(
                "Sound check",
                "Alarm volume ${Vol.MIN_PERCENT}% or more, Bluetooth off",
                Vol.ok(com.killswitch.aura.Settings.alarmPercent(c)) && !btOut(au),
            ),
        )
    }

    private fun btOut(au: AudioManager): Boolean = au.getDevices(AudioManager.GET_DEVICES_OUTPUTS).any {
        it.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP || it.type == 26 || it.type == 27
    }

    private fun go(a: Activity, i: Intent) {
        try {
            a.startActivity(i)
        } catch (e: Exception) {
            openAppBatterySettings(a)
        }
    }

    private fun openAppBatterySettings(a: Activity) {
        goAppDetails(a)
    }

    private fun goAppDetails(a: Activity) {
        try {
            a.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${a.packageName}")))
        } catch (e: Exception) {
            try { a.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) } catch (ignored: Exception) { }
        }
    }

    /** Opens whatever fixes check [i]. Notifications ask for the runtime permission the first time. */
    fun fix(a: Activity, i: Int) {
        val pk = Uri.parse("package:${a.packageName}")
        when (i) {
            0 -> go(a, Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, pk))
            1 -> if (Build.VERSION.SDK_INT >= 33 && Store.get(a, "askedN") != "1") {
                Store.set(a, "askedN", "1")
                a.requestPermissions(arrayOf("android.permission.POST_NOTIFICATIONS"), 1)
            } else go(a, Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, a.packageName))
            2 -> go(a, Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, pk))
            3 -> samsung(a)
            4 -> go(a, Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, pk))
            5 -> go(a, Intent("android.settings.ZEN_MODE_SETTINGS"))
            else -> {
                val au = a.getSystemService(AudioManager::class.java)
                if (btOut(au)) go(a, Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
                else com.killswitch.aura.Settings.setInt(a, "av", Vol.RECOMMENDED)
            }
        }
    }

    /** The buttons under "Fixes". */
    fun runFix(a: Activity, id: String) {
        when (id) {
            "rf" -> Scheduler.scheduleAll(a)
            "lk" -> fix(a, 0)
            "ph" -> samsung(a)
            "vo" -> fix(a, 6)
        }
    }

    /**
     * Samsung does not expose its "Never sleeping apps" list through a public API.
     * Opening its settings never marks the check complete; the user must confirm after returning.
     */
    fun samsung(a: Activity) {
        if (!isSamsungDevice()) {
            openAppBatterySettings(a)
            return
        }

        Store.set(a, SAMSUNG_PENDING, "1")
        val screens = listOf(
            "com.samsung.android.lool" to "com.samsung.android.sm.battery.ui.BatteryActivity",
            "com.samsung.android.lool" to "com.samsung.android.sm.ui.battery.BatteryActivity",
            "com.samsung.android.sm_cn" to "com.samsung.android.sm.ui.battery.BatteryActivity",
        )
        for ((p, cls) in screens) {
            try {
                val intent = Intent().setClassName(p, cls)
                if (intent.resolveActivity(a.packageManager) != null) {
                    a.startActivity(intent)
                    return
                }
            } catch (e: Exception) { }
        }
        openAppBatterySettings(a)
    }
}
