package com.killswitch.aura

import org.json.JSONObject

/** Which alarm a pending snooze belongs to, kept free of Android so it can be tested. */
object Snooze {
    /** Same alarm means same time, label, days and date. Its sound or snooze length may change without it becoming another alarm. */
    private fun same(a: Alarm, b: Alarm) = a.time == b.time && a.label == b.label && a.days == b.days && a.date == b.date

    /**
     * True when the alarm behind [snoozeCfg] was on in [old] and is no longer on in [new] (deleted, switched off, or moved to another time).
     * Then nothing should ring for it. If another alarm still looks exactly the same and is on, the snooze stays.
     */
    fun orphaned(old: List<Alarm>, new: List<Alarm>, snoozeCfg: String): Boolean {
        val s = try { Alarm.from(JSONObject(snoozeCfg)) } catch (e: Exception) { return false }
        return old.any { it.on && same(it, s) } && new.none { it.on && same(it, s) }
    }
}
