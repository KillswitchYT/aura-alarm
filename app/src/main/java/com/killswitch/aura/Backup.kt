package com.killswitch.aura

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Same format as the web build: {"al":[...],"wc":[...],"bed":{...},"vbtn":"vol"}. Old backups restore here. */
object Backup {
    /** [ro], [tfmt] and [wa] are new: ring style, time format and analog world clock. The web build never saved them, so they may be null. */
    data class Data(
        val alarms: List<Alarm>, val cities: List<String>?, val bed: Bed?, val vbtn: String?,
        val ro: Int? = null, val tfmt: String? = null, val wa: Int? = null,
    )

    fun export(alarms: List<Alarm>, cities: List<String>, bed: Bed, vbtn: String, ro: Int? = null, tfmt: String? = null, wa: Int? = null): String {
        val a = JSONArray(); alarms.forEach { a.put(it.toJson()) }
        return JSONObject().put("al", a).put("wc", JSONArray(cities)).put("bed", bed.toJson()).put("vbtn", vbtn)
            .also { o -> ro?.let { o.put("ro", it) }; tfmt?.let { o.put("tfmt", it) }; wa?.let { o.put("wa", it) } }.toString()
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
            ro = if (o.has("ro")) o.optInt("ro", 1).coerceIn(0, 2) else null,
            tfmt = o.optString("tfmt", "").takeIf { it in setOf("auto", "12", "24") },
            wa = if (o.has("wa")) o.optInt("wa", 0).coerceIn(0, 1) else null,
        )
    } catch (e: Exception) { null }

    fun exportAll(c: Context) = export(Store.alarms(c), WorldClock.savedOrDefault(c), Settings.bed(c), Settings.volumeButtons(c),
        Settings.ringStyle(c), Settings.timeFormat(c), if (Settings.analogWorld(c)) 1 else 0)

    /** Returns false (and changes nothing) when [text] is not a valid backup. */
    fun restore(c: Context, text: String): Boolean {
        val d = parse(text) ?: return false
        Store.saveAlarms(c, d.alarms)
        d.cities?.let { WorldClock.save(c, it) }
        d.vbtn?.let { Settings.setStr(c, "vbtn", it) }
        d.bed?.let { Store.set(c, "bed", it.toJson().toString()) }
        d.ro?.let { Settings.setInt(c, "ro", it) }
        d.tfmt?.let { Settings.setStr(c, "tfmt", it) }
        d.wa?.let { Settings.setInt(c, "wa", it) }
        Scheduler.scheduleAll(c)
        return true
    }
}
