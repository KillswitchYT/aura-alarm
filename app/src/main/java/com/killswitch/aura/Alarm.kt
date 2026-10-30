package com.killswitch.aura

import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar

/** Same JSON keys as the old web build, so saved alarms survive the move to native. */
data class Alarm(
    val time: String = "07:00",
    val label: String = "",
    val on: Boolean = true,
    val days: Set<Int> = emptySet(), // 0 = Sunday
    val date: String = "",           // yyyy-m-d for one-off dated alarms
    val sound: String = "",          // "" default, "raw:1..3" bundled, anything else a content uri
    val vibrate: Boolean = true,
    val snooze: Int = 5,
    val gradual: Boolean = false,
    val skip: Long = 0L,
    val challenge: Int = 0,          // 0 none, 1 math, 2 shake
) {
    val hour get() = time.substringBefore(':').toInt()
    val minute get() = time.substringAfter(':').toInt()

    /** Next ring time after [now], or -1 when there is none. */
    fun next(now: Long = System.currentTimeMillis()): Long {
        val h = hour
        val m = minute
        if (date.isNotEmpty()) {
            val (y, mo, d) = date.split("-").map { it.toInt() }
            val c = Calendar.getInstance().apply { set(y, mo - 1, d, h, m, 0); set(Calendar.MILLISECOND, 0) }
            val t = c.timeInMillis
            return if (t > now && t != skip) t else -1
        }
        for (i in 0 until 8) {
            val c = Calendar.getInstance().apply {
                timeInMillis = now
                add(Calendar.DAY_OF_YEAR, i)
                set(Calendar.HOUR_OF_DAY, h); set(Calendar.MINUTE, m); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            }
            val t = c.timeInMillis
            if (t <= now || t == skip) continue
            if (days.isEmpty() || (c.get(Calendar.DAY_OF_WEEK) - 1) in days) return t
        }
        return -1
    }

    fun daysSummary(): String = when {
        date.isNotEmpty() -> date
        days.isEmpty() -> "Once"
        days.size == 7 -> "Every day"
        days == setOf(1, 2, 3, 4, 5) -> "Weekdays"
        days == setOf(0, 6) -> "Weekends"
        else -> days.sorted().joinToString(" ") { DAY_NAMES[it] }
    }

    fun toJson(): JSONObject = JSONObject()
        .put("t", time).put("l", label).put("on", if (on) 1 else 0)
        .put("d", JSONArray(days.sorted())).put("dt", date).put("snd", sound)
        .put("vb", if (vibrate) 1 else 0).put("sz", snooze).put("gr", if (gradual) 1 else 0).put("ch", challenge)
        .also { if (skip > 0) it.put("sk", skip) }

    companion object {
        val DAY_NAMES = listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")

        fun from(o: JSONObject): Alarm {
            val d = o.optJSONArray("d")
            return Alarm(
                time = o.optString("t", "07:00"),
                label = o.optString("l", ""),
                on = o.optInt("on", 0) == 1,
                days = (0 until (d?.length() ?: 0)).map { d!!.optInt(it, -1) }.filter { it in 0..6 }.toSet(),
                date = o.optString("dt", ""),
                sound = o.optString("snd", ""),
                vibrate = o.optInt("vb", 1) == 1,
                snooze = o.optInt("sz", 5),
                gradual = o.optInt("gr", 0) == 1,
                skip = o.optLong("sk", 0),
                challenge = o.optInt("ch", 0).coerceIn(0, 2),
            )
        }
    }
}

fun fmtTime(h: Int, m: Int, is24: Boolean): String =
    if (is24) "%02d:%02d".format(h, m) else "%d:%02d".format(if (h % 12 == 0) 12 else h % 12, m)

fun ampm(h: Int): String = if (h < 12) "AM" else "PM"
