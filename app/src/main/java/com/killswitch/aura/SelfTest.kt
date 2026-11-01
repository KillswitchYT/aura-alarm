package com.killswitch.aura

import android.content.Context

/** The Setup tab's self-test: re-arms everything, then checks what Android really reports instead of what we hope. */
object SelfTest {
    /** The 7 protection checks followed by two checks on the stored alarms. */
    fun run(c: Context): List<Check> {
        try { Scheduler.scheduleAll(c) } catch (e: Exception) { }
        val perms = Perms.check(c)
        val list = Store.tryAlarms(c)
        val due = list?.count { it.on && it.next() > 0 } ?: 0
        val armed = Store.get(c, "nextT").toLongOrNull() ?: 0L
        return perms + listOf(
            Check("Saved alarms readable", "", list != null),
            Check("Next alarm armed", if (due == 0) "No alarm is waiting" else "$due waiting", due == 0 || (armed > 0 && Store.get(c, "schedFail") != "1")),
        )
    }

    fun summary(results: List<Check>): String {
        val bad = results.count { !it.ok }
        return if (bad == 0) "All protected" else "$bad need attention"
    }
}
