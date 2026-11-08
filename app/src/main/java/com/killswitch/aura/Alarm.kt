package com.killswitch.aura

import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar
import java.util.Locale

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
    val soundName: String = "",      // title of a phone-picked sound ("sn" in the web build)
) {
    val hour get() = time.substringBefore(':').toInt()
    val minute get() = time.substringAfter(':').toInt()

    /** Next ring time after [now], or -1 when there is none. */
    /** A one-off alarm whose date and time are already behind us. It can never ring, whatever its switch says. */
    fun pastDue(now: Long = System.currentTimeMillis()): Boolean = date.isNotEmpty() && next(now) < 0

    fun next(now: Long = System.currentTimeMillis()): Long {
        val h = hour
        val m = minute
        if (date.isNotEmpty()) {
            val (y, mo, d) = date.split("-").map { it.toInt() }
            val c = Calendar.getInstance().apply { set(y, mo - 1, d, h, m, 0); set(Calendar.MILLISECOND, 0) }
            val t = c.timeInMillis
            return if (t > now && t != skip) t else -1
        }
        // Two weeks, not one: skipping the next ring of a one-day weekly alarm has to land on the week after, not on nothing.
        for (i in 0 until 15) {
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
        .also { if (soundName.isNotEmpty()) it.put("sn", soundName) }

    companion object {
        val DAY_NAMES = listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")

        /**
         * Reads one alarm from saved or imported JSON. A file is not trusted: a time or date that is not real can never reach the
         * screen or the scheduler, where it used to crash the app on every launch. Such an alarm is kept but switched off.
         */
        fun from(o: JSONObject): Alarm {
            val d = o.optJSONArray("d")
            val t = cleanTime(o.optString("t", "07:00"))
            val rawDate = o.optString("dt", "")
            val dt = cleanDate(rawDate)
            val readable = t != null && (rawDate.isBlank() || dt.isNotEmpty())
            return Alarm(
                time = t ?: "07:00",
                label = o.optString("l", ""),
                on = readable && o.optInt("on", 0) == 1,
                days = (0 until (d?.length() ?: 0)).map { d!!.optInt(it, -1) }.filter { it in 0..6 }.toSet(),
                date = dt,
                sound = o.optString("snd", ""),
                vibrate = o.optInt("vb", 1) == 1,
                snooze = o.optInt("sz", 5).coerceIn(0, 120),
                gradual = o.optInt("gr", 0) == 1,
                skip = o.optLong("sk", 0),
                challenge = o.optInt("ch", 0).coerceIn(0, 2),
                soundName = o.optString("sn", ""),
            )
        }
    }
}

/** Times and dates are stored as plain ASCII, whatever language the phone is set to (Arabic and Persian locales print other digits). */
fun hhmm(h: Int, m: Int): String = String.format(Locale.US, "%02d:%02d", h.coerceIn(0, 23), m.coerceIn(0, 59))
fun ymd(y: Int, mo: Int, d: Int): String = String.format(Locale.US, "%d-%02d-%02d", y, mo, d)

/** "7:5" becomes "07:05", digits of any script are accepted, and anything that is not a time of day is null. */
fun cleanTime(s: String): String? {
    val p = s.trim().split(":")
    if (p.size != 2) return null
    val h = p[0].trim().toIntOrNull() ?: return null
    val m = p[1].trim().toIntOrNull() ?: return null
    return if (h in 0..23 && m in 0..59) hhmm(h, m) else null
}

/** A year-month-day string in the normal form, or "" when it is blank or not a real-looking date. */
fun cleanDate(s: String): String {
    val p = s.trim().split("-")
    if (p.size != 3) return ""
    val y = p[0].trim().toIntOrNull() ?: return ""
    val mo = p[1].trim().toIntOrNull() ?: return ""
    val d = p[2].trim().toIntOrNull() ?: return ""
    return if (y in 2000..2100 && mo in 1..12 && d in 1..31) ymd(y, mo, d) else ""
}

fun fmtTime(h: Int, m: Int, is24: Boolean): String =
    if (is24) "%02d:%02d".format(h, m) else "%d:%02d".format(if (h % 12 == 0) 12 else h % 12, m)

fun ampm(h: Int): String = if (h < 12) "AM" else "PM"
