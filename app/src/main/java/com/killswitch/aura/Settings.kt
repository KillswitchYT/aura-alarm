package com.killswitch.aura

import android.content.Context
import android.text.format.DateFormat
import org.json.JSONObject

/** Sleep reminder: [goal] hours of sleep before the first alarm, plus [wind] minutes to wind down. */
data class Bed(val on: Boolean = false, val goal: Int = 8, val wind: Int = 30) {
    /** When to nudge, or -1 if there is nothing to nudge about. */
    fun reminderAt(firstAlarm: Long, now: Long): Long {
        if (!on || firstAlarm <= 0) return -1
        val r = firstAlarm - goal * 3_600_000L - wind * 60_000L
        return if (r > now) r else -1
    }
    fun toJson(): JSONObject = JSONObject().put("on", if (on) 1 else 0).put("goal", goal).put("wind", wind)
    companion object {
        val GOALS = listOf(6, 7, 8, 9, 10)
        val WINDS = listOf(15, 30, 45, 60)
        fun from(o: JSONObject?) = if (o == null) Bed() else Bed(o.optInt("on", 0) == 1, o.optInt("goal", 8), o.optInt("wind", 30))
    }
}

enum class VolAction { PASS, SNOOZE, DISMISS, CONSUME }

/** The web build stored values with JSON.stringify, so strings arrive quoted. Both forms are accepted. */
object Settings {
    fun unquote(s: String): String = s.trim().removeSurrounding("\"")

    private fun raw(c: Context, k: String) = unquote(Store.get(c, k))
    fun str(c: Context, k: String, def: String) = raw(c, k).ifEmpty { def }
    fun setStr(c: Context, k: String, v: String) { Store.set(c, k, "\"$v\""); if (k == "tfmt") ClockWidget.refresh(c) }
    fun int(c: Context, k: String, def: Int) = raw(c, k).toIntOrNull() ?: def
    fun setInt(c: Context, k: String, v: Int) = Store.set(c, k, v.toString())

    /** "auto", "12" or "24". */
    fun timeFormat(c: Context) = str(c, "tfmt", "auto")
    fun is24(c: Context) = when (timeFormat(c)) { "12" -> false; "24" -> true; else -> DateFormat.is24HourFormat(c) }

    /** What the volume buttons do while ringing: vol, snooze, stop or none. */
    fun volumeButtons(c: Context) = str(c, "vbtn", "vol")
    /** 0 gentle ramp, 1 standard, 2 max volume and vibrate with no snooze. */
    fun ringStyle(c: Context) = int(c, "ro", 1)

    /** 0 follows the phone, 1 dark, 2 light. */
    fun theme(c: Context) = int(c, "th", 0).coerceIn(0, 2)

    /** Which days are the weekend for the day labels: "auto" (the phone's region) or day numbers like "56" for Friday and Saturday. */
    fun weekendMode(c: Context): String = Store.get(c, "wkd").ifEmpty { "auto" }
    fun setWeekendMode(c: Context, mode: String) { Store.set(c, "wkd", mode); Weekend.apply(mode) }

    /** Safety nets: switches that are on until the person turns them off. */
    private fun flag(c: Context, k: String) = int(c, k, 1) == 1
    fun setFlag(c: Context, k: String, on: Boolean) = setInt(c, k, if (on) 1 else 0)
    /** The slider cannot go below 30% (otherwise 10%). Also applied when the alarm rings, so an old low setting is raised too. */
    fun volumeFloorOn(c: Context) = flag(c, "vfloor")
    fun alarmFloor(c: Context) = Vol.floor(volumeFloorOn(c))
    /** A note on the phone when an alarm rang its full 10 minutes and nobody answered. */
    fun missedNote(c: Context) = flag(c, "wmiss")
    /** A warning when a one-off alarm's date and time have already passed. */
    fun pastWarn(c: Context) = flag(c, "wpast")

    /** The app's own alarm volume, 1% steps. 100 until the person lowers it. */
    fun alarmPercent(c: Context) = Vol.effective(int(c, "av", 100), alarmFloor(c))
    fun analogWorld(c: Context) = int(c, "wa", 0) == 1

    fun bed(c: Context): Bed = try { Bed.from(JSONObject(Store.get(c, "bed").ifEmpty { "{}" })) } catch (e: Exception) { Bed() }
    fun setBed(c: Context, b: Bed) { Store.set(c, "bed", b.toJson().toString()); Scheduler.scheduleAll(c) }

    fun volumeAction(mode: String, challengeLocked: Boolean): VolAction = when {
        mode == "snooze" -> VolAction.SNOOZE
        mode == "stop" && !challengeLocked -> VolAction.DISMISS
        mode == "none" || mode == "stop" -> VolAction.CONSUME
        else -> VolAction.PASS
    }
}
