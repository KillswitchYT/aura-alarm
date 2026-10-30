package com.killswitch.aura

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray

/** Device-protected storage, so alarms can be re-armed after a reboot before the first unlock. */
object Store {
    private fun p(c: Context): SharedPreferences {
        val d = if (c.isDeviceProtectedStorage) c else c.createDeviceProtectedStorageContext()
        return d.getSharedPreferences("s", Context.MODE_PRIVATE)
    }

    fun get(c: Context, k: String): String = p(c).getString(k, "") ?: ""
    fun set(c: Context, k: String, v: String) { p(c).edit().putString(k, v).commit() }

    fun alarms(c: Context): List<Alarm> = try {
        val a = JSONArray(get(c, "al").ifEmpty { "[]" })
        (0 until a.length()).mapNotNull { i -> a.optJSONObject(i)?.let { Alarm.from(it) } }
    } catch (e: Exception) { emptyList() }

    /** Strict parse: null means the saved data is damaged. A missing or empty value is a normal empty list. */
    fun parseAlarms(json: String): List<Alarm>? = try {
        val a = JSONArray(json.ifBlank { "[]" })
        (0 until a.length()).map { Alarm.from(a.getJSONObject(it)) }
    } catch (e: Exception) { null }

    fun tryAlarms(c: Context): List<Alarm>? = parseAlarms(get(c, "al"))

    fun saveAlarms(c: Context, list: List<Alarm>) {
        val a = JSONArray()
        list.forEach { a.put(it.toJson()) }
        set(c, "al", a.toString())
    }
}
