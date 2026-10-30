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

    fun saveAlarms(c: Context, list: List<Alarm>) {
        val a = JSONArray()
        list.forEach { a.put(it.toJson()) }
        set(c, "al", a.toString())
    }
}
