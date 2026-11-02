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
    private val TEXT = listOf(
        "Exact alarms" to "\u201CAlarms & reminders\u201D access",
        "Notifications" to "Needed for the alarm and warnings",
        "Full-screen alarm" to "Ring over the lock screen",
        "Never sleeping app" to "Samsung: exempt from sleeping apps",
        "Battery: Unrestricted" to "Stops Android killing the alarm",
        "Do Not Disturb" to "Allow alarms through",
        "Sound check" to "Alarm volume ${Vol.MIN_PERCENT}% or more, Bluetooth off",
    )

    val FIXES = listOf(
        Fix("rf", "Re-arm all alarms", "Reschedule every alarm and backup"),
        Fix("lk", "Check exact-alarm access", "Verify Alarms & reminders"),
        Fix("ph", "Open Samsung sleeping apps", "Jump to Never sleeping apps"),
        Fix("vo", "Raise alarm volume", "Set it to a loud level, or open Bluetooth if that is the problem"),
    )

    /** Order matches the Setup tab: 0 exact alarms, 1 notifications, 2 full-screen, 3 samsung (manual), 4 battery, 5 DND, 6 sound. */
    fun check(c: Context): List<Check> {
        val nm = c.getSystemService(NotificationManager::class.java)
        val au = c.getSystemService(AudioManager::class.java)
        val ok = listOf(
            Scheduler.canExact(c),
            nm.areNotificationsEnabled(),
            Build.VERSION.SDK_INT < 34 || nm.canUseFullScreenIntent(),
            Store.get(c, "samsungOk") == "1",
            c.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(c.packageName),
            nm.currentInterruptionFilter != NotificationManager.INTERRUPTION_FILTER_NONE,
            Vol.ok(au.getStreamVolume(AudioManager.STREAM_ALARM), au.getStreamMaxVolume(AudioManager.STREAM_ALARM)) && !btOut(au),
        )
        return TEXT.mapIndexed { i, t -> Check(t.first, t.second, ok[i]) }
    }

    private fun btOut(au: AudioManager): Boolean = au.getDevices(AudioManager.GET_DEVICES_OUTPUTS).any {
        it.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP || it.type == 26 || it.type == 27 // 26/27 = BLE headset and speaker
    }

    private fun go(a: Activity, i: Intent) {
        try { a.startActivity(i) } catch (e: Exception) {
            try { a.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${a.packageName}"))) } catch (e2: Exception) { }
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
                // Samsung hides the alarm slider in Sound settings, so raise it right here. Bluetooth has to be dealt with by hand.
                val au = a.getSystemService(AudioManager::class.java)
                if (btOut(au)) go(a, Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
                else try { au.setStreamVolume(AudioManager.STREAM_ALARM, Vol.recommended(au.getStreamMaxVolume(AudioManager.STREAM_ALARM)), 0) }
                catch (e: Exception) { go(a, Intent(Settings.ACTION_SOUND_SETTINGS)) }
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

    /** Android has no public API to read Samsung's "Never sleeping apps" list, so this one is confirmed by hand. */
    fun samsung(a: Activity) {
        if (Store.get(a, "samsungOk") == "1") { Store.set(a, "samsungOk", "0"); return }
        Store.set(a, "samsungOk", "1")
        val screens = listOf(
            "com.samsung.android.lool" to "com.samsung.android.sm.battery.ui.BatteryActivity",
            "com.samsung.android.lool" to "com.samsung.android.sm.ui.battery.BatteryActivity",
            "com.samsung.android.sm_cn" to "com.samsung.android.sm.ui.battery.BatteryActivity",
        )
        for ((p, cls) in screens) {
            try { a.startActivity(Intent().setClassName(p, cls)); return } catch (e: Exception) { }
        }
        go(a, Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
    }
}
