package com.killswitch.aura

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import org.json.JSONArray
import org.json.JSONObject

/** States: "idle", "run", "pause". Times are epoch millis, durations are millis. */
data class TimerItem(
    val id: Int, val label: String, val dur: Long,
    val state: String = "idle", val end: Long = 0, val left: Long = dur,
) {
    val running get() = state == "run"
    fun remaining(now: Long) = if (running) (end - now).coerceAtLeast(0) else left
    fun progress(now: Long) = if (dur > 0) (remaining(now).toFloat() / dur).coerceIn(0f, 1f) else 0f

    /** The +1:00 button on a card. */
    fun addMinute(now: Long): TimerItem {
        val t = if (running) copy(end = end + 60_000) else copy(left = left + 60_000)
        return t.copy(dur = maxOf(t.dur, if (running) t.end - now else t.left))
    }
    fun reset() = copy(state = "idle", left = dur)
    /** Start/Pause button. */
    fun toggle(now: Long): TimerItem =
        if (running) copy(left = end - now, state = "pause")
        else (if (left <= 0) copy(left = dur) else this).let { it.copy(end = now + it.left, state = "run") }

    fun toJson(): JSONObject = JSONObject().put("id", id).put("l", label).put("dur", dur).put("st", state).put("end", end).put("left", left)

    companion object {
        fun from(o: JSONObject) = TimerItem(o.optInt("id"), o.optString("l", ""), o.optLong("dur"),
            o.optString("st", "idle"), o.optLong("end"), o.optLong("left"))
        fun newId(rnd: Double = Math.random()) = (rnd * 9e8).toInt() + 1_000_000
        fun create(seconds: Int, label: String, now: Long, id: Int = newId()) =
            TimerItem(id, label, seconds * 1000L, "run", now + seconds * 1000L, seconds * 1000L)

        /** 1:05:09 or 05:09 */
        fun fmt(ms: Long): String {
            val s = Math.ceil(ms.coerceAtLeast(0) / 1000.0).toLong()
            val h = s / 3600; val m = s % 3600 / 60; val sec = s % 60
            return (if (h > 0) "$h:" else "") + "%02d:%02d".format(m, sec)
        }
    }
}

object Timers {
    fun load(c: Context): List<TimerItem> = try {
        val a = JSONArray(Store.get(c, "tm").ifEmpty { "[]" })
        (0 until a.length()).mapNotNull { a.optJSONObject(it)?.let { o -> TimerItem.from(o) } }
    } catch (e: Exception) { emptyList() }

    fun save(c: Context, list: List<TimerItem>) {
        val a = JSONArray(); list.forEach { a.put(it.toJson()) }
        Store.set(c, "tm", a.toString())
        scheduleAll(c)
    }

    private fun tpi(c: Context, i: Int, id: Int, flags: Int): PendingIntent? =
        PendingIntent.getBroadcast(c, 5000 + i, Intent(c, AlarmReceiver::class.java).setAction("TIMER").putExtra("id", id),
            flags or PendingIntent.FLAG_IMMUTABLE)

    private fun act(c: Context, a: String, id: Int): PendingIntent =
        PendingIntent.getBroadcast(c, 7000 + (id % 1000) * 2 + (if (a == "T_ADD") 1 else 0),
            Intent(c, AlarmReceiver::class.java).setAction(a).putExtra("id", id),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    /** Arms every running timer and shows its live countdown notification. */
    @Synchronized
    fun scheduleAll(c: Context) {
        val am = c.getSystemService(AlarmManager::class.java)
        val nm = c.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel("timer", "Running timers", NotificationManager.IMPORTANCE_LOW))
        for (i in 0 until 64) tpi(c, i, 0, PendingIntent.FLAG_NO_CREATE)?.let { am.cancel(it); it.cancel() }
        val now = System.currentTimeMillis()
        val exact = Scheduler.canExact(c)
        for ((i, t) in load(c).withIndex()) {
            if (i >= 64) break
            nm.cancel(6000 + t.id % 1000)
            if (!t.running || t.end <= now) continue
            Scheduler.arm(am, exact, t.end, tpi(c, i, t.id, PendingIntent.FLAG_UPDATE_CURRENT)!!)
            nm.notify(6000 + t.id % 1000, Notification.Builder(c, "timer").setSmallIcon(R.drawable.ic_small)
                .setContentTitle(t.label.ifEmpty { "Timer" })
                .setUsesChronometer(true).setChronometerCountDown(true).setWhen(t.end)
                .setOngoing(true).setOnlyAlertOnce(true).setContentIntent(Scheduler.show(c))
                .addAction(0, "+1 min", act(c, "T_ADD", t.id))
                .addAction(0, "Stop", act(c, "T_STOP", t.id)).build())
        }
    }

    /** A timer's alarm fired: reset it and ring. */
    fun fired(c: Context, id: Int) {
        val list = load(c)
        val hit = list.firstOrNull { it.id == id }
        Store.set(c, "tm", JSONArray().also { a -> list.forEach { a.put((if (it.id == id) it.reset() else it).toJson()) } }.toString())
        val cfg = JSONObject().put("l", hit?.label?.ifEmpty { "Timer" } ?: "Timer").put("timer", 1).put("sz", 0).put("ch", 0).put("vb", 1)
        AlarmReceiver.startRing(c, cfg.toString(), false)
        scheduleAll(c)
    }

    /** Notification buttons: "+1 min" or "Stop". */
    fun action(c: Context, i: Intent) {
        val id = i.getIntExtra("id", 0)
        val out = load(c).map {
            when {
                it.id != id -> it
                i.action == "T_STOP" -> it.reset()
                else -> it.copy(end = it.end + 60_000)
            }
        }
        val a = JSONArray(); out.forEach { a.put(it.toJson()) }
        Store.set(c, "tm", a.toString())
        scheduleAll(c)
    }
}
