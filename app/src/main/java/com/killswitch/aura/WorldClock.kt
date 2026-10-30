package com.killswitch.aura

import android.content.Context
import org.json.JSONArray
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.math.abs

/** A saved city is stored as "Name|Zone|Country", same as the web build. */
data class City(val name: String, val zone: String, val country: String) {
    val key get() = "$name|$zone|$country"
    companion object {
        fun parse(s: String): City? { val p = s.split("|"); return if (p.size >= 2) City(p[0], p[1], p.getOrElse(2) { "" }) else null }
    }
}

object WorldClock {
    private val ALIASES = mapOf(
        "usa" to "united states", "us" to "united states", "america" to "united states",
        "uk" to "united kingdom", "england" to "united kingdom", "britain" to "united kingdom", "scotland" to "united kingdom",
        "uae" to "united arab emirates", "emirates" to "united arab emirates", "ksa" to "saudi arabia",
        "korea" to "south korea", "drc" to "congo", "holland" to "netherlands", "czech" to "czechia", "burma" to "myanmar",
    )
    private val REGIONS = setOf("Africa", "America", "Antarctica", "Arctic", "Asia", "Atlantic", "Australia", "Europe", "Indian", "Pacific")

    /** [data] is "Country:City=Zone,City=Zone;..." Zones this phone does not know are skipped. Every other zone is appended. */
    fun parseData(data: String, valid: Set<String>): List<City> {
        val out = ArrayList<City>()
        val have = HashSet<String>()
        data.split(";").forEach { r ->
            val i = r.indexOf(':')
            if (i < 0) return@forEach
            val country = r.substring(0, i)
            r.substring(i + 1).split(",").forEach { x ->
                val e = x.split("=")
                if (e.size == 2 && e[1] in valid) { out.add(City(e[0], e[1], country)); have.add(e[1]) }
            }
        }
        valid.filter { it !in have && (it == "UTC" || ('/' in it && it.substringBefore('/') in REGIONS)) }.sorted().forEach { z ->
            val p = z.split("/")
            out.add(City(p.last().replace('_', ' '), z, if (p.size > 1) p[0] else ""))
        }
        return out
    }

    private var cache: List<City>? = null
    fun cities(c: Context): List<City> = cache ?: parseData(
        c.resources.openRawResource(R.raw.cities).bufferedReader().use { it.readText() }.trim(),
        ZoneId.getAvailableZoneIds(),
    ).also { cache = it }

    /** Picker search: name, country or zone, with a few nicknames. Prefix matches rank first. */
    fun search(all: List<City>, query: String, limit: Int = 80): List<City> {
        val q = query.trim().lowercase()
        val alias = ALIASES[q]
        fun score(x: City) = if (x.name.lowercase().startsWith(q)) 0 else if (x.country.lowercase().startsWith(q)) 1 else 2
        return all.filter {
            val h = (it.name + " " + it.country + " " + it.zone.replace('_', ' ')).lowercase()
            h.contains(q) || (alias != null && h.contains(alias))
        }.sortedWith(compareBy({ score(it) }, { it.name.lowercase() })).take(limit)
    }

    fun hms(zone: String, now: Long): Triple<Int, Int, Int> {
        val z = ZonedDateTime.ofInstant(Instant.ofEpochMilli(now), ZoneId.of(zone))
        return Triple(z.hour, z.minute, z.second)
    }

    /** Minutes this zone is ahead of (+) or behind (-) [home]. */
    fun offsetMinutes(zone: String, now: Long, home: ZoneId = ZoneId.systemDefault()): Int {
        val i = Instant.ofEpochMilli(now)
        return (ZoneId.of(zone).rules.getOffset(i).totalSeconds - home.rules.getOffset(i).totalSeconds) / 60
    }

    fun offsetText(min: Int): String {
        if (min == 0) return "Same time"
        val ab = abs(min)
        return (if (min > 0) "+" else "-") + "${ab / 60}h" + (if (ab % 60 != 0) " ${ab % 60}m" else "")
    }

    fun dayLabel(zone: String, now: Long, home: ZoneId = ZoneId.systemDefault()): String {
        val i = Instant.ofEpochMilli(now)
        val a = ZonedDateTime.ofInstant(i, ZoneId.of(zone)).toLocalDate()
        val b = ZonedDateTime.ofInstant(i, home).toLocalDate()
        return when { a == b -> "Today"; a.isAfter(b) -> "Tomorrow"; else -> "Yesterday" }
    }

    /** "GMT", "GMT+2", "GMT-5", "GMT+5:30". */
    fun gmtLabel(zone: String, now: Long): String {
        val s = ZoneId.of(zone).rules.getOffset(Instant.ofEpochMilli(now)).totalSeconds
        if (s == 0) return "GMT"
        val m = abs(s) / 60
        return "GMT" + (if (s > 0) "+" else "-") + m / 60 + (if (m % 60 != 0) ":%02d".format(m % 60) else "")
    }

    /** Time converter: what [h]:[m] today on your clock is in [zone]. */
    fun converted(zone: String, h: Int, m: Int, today: LocalDate = LocalDate.now(), home: ZoneId = ZoneId.systemDefault()): Pair<Int, Int> {
        val z = ZonedDateTime.of(today, LocalTime.of(h, m), home).withZoneSameInstant(ZoneId.of(zone))
        return z.hour to z.minute
    }

    fun saved(c: Context): List<String> = try {
        val a = JSONArray(Store.get(c, "wc").ifEmpty { "[]" })
        (0 until a.length()).map { a.optString(it) }.filter { it.isNotEmpty() }
    } catch (e: Exception) { emptyList() }

    fun save(c: Context, list: List<String>) = Store.set(c, "wc", JSONArray(list).toString())
    fun add(c: Context, city: City) { val l = saved(c); if (city.key !in l) save(c, l + city.key) }
    fun remove(c: Context, index: Int) { val l = saved(c).toMutableList(); if (index in l.indices) { l.removeAt(index); save(c, l) } }
    fun setAnalog(c: Context, on: Boolean) = Settings.setInt(c, "wa", if (on) 1 else 0)
}
