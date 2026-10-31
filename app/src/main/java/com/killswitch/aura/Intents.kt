package com.killswitch.aura

import android.content.Context
import android.content.Intent
import android.provider.AlarmClock

/** Lets voice assistants and other apps create alarms and timers here. Returns the tab to open, or null. */
object Intents {
    fun handle(c: Context, i: Intent?): String? {
        if (i == null || i.action == null) return null
        return try {
            when (i.action) {
                AlarmClock.ACTION_SET_ALARM -> {
                    val h = i.getIntExtra(AlarmClock.EXTRA_HOUR, 7)
                    val m = i.getIntExtra(AlarmClock.EXTRA_MINUTES, 0)
                    val days = i.getIntegerArrayListExtra(AlarmClock.EXTRA_DAYS)?.map { it - 1 }?.filter { it in 0..6 }?.toSet() ?: emptySet()
                    val a = Alarm(time = "%02d:%02d".format(h, m), label = i.getStringExtra(AlarmClock.EXTRA_MESSAGE) ?: "", days = days,
                        gradual = Settings.ringStyle(c) == 0)
                    Store.saveAlarms(c, (Store.alarms(c) + a).sortedBy { it.time })
                    Scheduler.scheduleAll(c)
                    "home"
                }
                AlarmClock.ACTION_SET_TIMER -> {
                    val len = i.getIntExtra(AlarmClock.EXTRA_LENGTH, 0)
                    if (len <= 0) null else {
                        val now = System.currentTimeMillis()
                        Timers.save(c, Timers.load(c) + TimerItem.create(len, i.getStringExtra(AlarmClock.EXTRA_MESSAGE) ?: "", now,
                            id = (now % 900_000_000L).toInt() + 1_000_000))
                        "timer"
                    }
                }
                AlarmClock.ACTION_SHOW_ALARMS -> "home"
                else -> null
            }
        } catch (e: Exception) { null }
    }
}
