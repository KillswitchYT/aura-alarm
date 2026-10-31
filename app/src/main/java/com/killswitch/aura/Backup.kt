package com.killswitch.aura

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Same format as the web build: {"al":[...],"wc":[...],"bed":{...},"vbtn":"vol"}. Old backups restore here. */
object Backup {
    data class Data(val alarms: List<Alarm>, val cities: List<String>?, val bed: Bed?, val vbtn: String?)

    fun export(alarms: List<Alarm>, cities: List<String>, bed: Bed, vbtn: String): String {
        val a = JSONArray(); alarms.forEach { a.put(it.toJson()) }
        return JSONObject().put("al", a).put("wc", JSONArray(cities)).put("bed", bed.toJson()).put("vbtn", vbtn).toString()
    }

    /** Null when [text] is not a backup. Only "al" is required. */
    fun parse(text: String): Data? = try {
        val o = JSONObject(text)
        val al = o.getJSONArray("al")
        val wc = o.optJSONArray("wc")
        Data(
            alarms = (0 until al.length()).mapNotNull { al.optJSONObject(it)?.let { j -> Alarm.from(j) } },
            cities = wc?.let { w -> (0 until w.length()).map { w.optString(it) } },
            bed = o.optJSONObject("bed")?.let { Bed.from(it) },
            vbtn = o.optString("vbtn", "").ifEmpty { null },
        )
    } catch (e: Exception) { null }

    fun exportAll(c: Context) = export(Store.alarms(c), WorldClock.savedOrDefault(c), Settings.bed(c), Settings.volumeButtons(c))

    /** Returns false (and changes nothing) when [text] is not a valid backup. */
    fun restore(c: Context, text: String): Boolean {
        val d = parse(text) ?: return false
        Store.saveAlarms(c, d.alarms)
        d.cities?.let { WorldClock.save(c, it) }
        d.vbtn?.let { Settings.setStr(c, "vbtn", it) }
        d.bed?.let { Store.set(c, "bed", it.toJson().toString()) }
        Scheduler.scheduleAll(c)
        return true
    }
}
