package com.killswitch.aura

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** [acc] is time banked while stopped, [startedAt] is when the current run began. [laps] are cumulative marks. */
data class Stopwatch(
    val acc: Long = 0, val running: Boolean = false, val startedAt: Long = 0, val laps: List<Long> = emptyList(),
) {
    fun elapsed(now: Long) = acc + if (running) now - startedAt else 0
    fun toggle(now: Long) = if (running) copy(acc = elapsed(now), running = false) else copy(running = true, startedAt = now)
    /** While running this marks a lap. Stopped with time on the clock it resets everything. */
    fun lapOrReset(now: Long) = when {
        running -> copy(laps = laps + elapsed(now))
        acc > 0 -> Stopwatch()
        else -> this
    }
    val primaryLabel get() = if (running) "Stop" else if (acc > 0) "Resume" else "Start"
    fun secondaryLabel() = if (running || acc == 0L) "Lap" else "Reset"

    fun splits(): List<Long> = laps.mapIndexed { i, v -> v - (laps.getOrNull(i - 1) ?: 0) }
    /** 1 = best lap, 2 = worst lap, 0 = neither (only marked once there are two laps). */
    fun mark(i: Int): Int {
        val s = splits(); if (s.size < 2) return 0
        return when (s[i]) { s.min() -> 1; s.max() -> 2; else -> 0 }
    }
    fun copyText(): String = if (laps.isEmpty()) "No laps"
        else splits().mapIndexed { i, v -> "Lap ${i + 1}  ${fmt(v)}  total ${fmt(laps[i])}" }.joinToString("\n")

    fun toJson(): JSONObject = JSONObject().put("acc", acc).put("run", if (running) 1 else 0).put("st", startedAt)
        .put("laps", JSONArray(laps))

    companion object {
        fun from(o: JSONObject): Stopwatch {
            val l = o.optJSONArray("laps")
            return Stopwatch(o.optLong("acc"), o.optInt("run") == 1, o.optLong("st"), (0 until (l?.length() ?: 0)).map { l!!.optLong(it) })
        }
        /** Null means damaged. Nothing saved yet is a fresh stopwatch. */
        fun parse(json: String): Stopwatch? = try { if (json.isBlank()) Stopwatch() else from(JSONObject(json)) } catch (e: Exception) { null }

        /** mm:ss.cc */
        fun fmt(ms: Long) = "%02d:%02d.%02d".format(ms / 60000, ms / 1000 % 60, ms / 10 % 100)
    }
}

/** The stopwatch survives leaving the app: it is stored as epoch times, so a running one keeps counting. */
object StopwatchStore {
    fun load(c: Context): Stopwatch? = Stopwatch.parse(Store.get(c, "sw"))
    fun save(c: Context, s: Stopwatch) = Store.set(c, "sw", s.toJson().toString())
    fun clear(c: Context) = Store.set(c, "sw", "")
}
