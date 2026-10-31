package com.killswitch.aura

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import org.json.JSONObject

object Scheduler {
    private const val BACKUP_MS = 120_000L

    fun canExact(c: Context): Boolean =
        Build.VERSION.SDK_INT < 31 || c.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()

    private fun pi(c: Context, code: Int, act: String, t: Long, cfg: String, flags: Int, test: Boolean = false): PendingIntent? {
        val i = Intent(c, AlarmReceiver::class.java).setAction(act)
            .putExtra("t", t).putExtra("cfg", cfg).putExtra("i", code).putExtra("test", test)
        return PendingIntent.getBroadcast(c, code, i, flags or PendingIntent.FLAG_IMMUTABLE)
    }

    internal fun show(c: Context): PendingIntent =
        PendingIntent.getActivity(c, 0, Intent(c, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)

    /** Exact when Android allows it, otherwise the closest thing that still wakes the phone. Never throws. */
    internal fun arm(am: AlarmManager, exact: Boolean, t: Long, p: PendingIntent) {
        try {
            if (exact) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, t, p)
            else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, t, p)
        } catch (e: Exception) { }
    }

    /**
     * Rebuilds every pending alarm from storage. The main alarm is setAlarmClock (needs no permission, never throttled);
     * a backup 2 minutes later only rings if the main one never arrived.
     */
    @Synchronized
    fun scheduleAll(c: Context) {
        val am = c.getSystemService(AlarmManager::class.java)
        val exact = canExact(c)
        for (i in 0 until 128) {
            pi(c, i, if (i % 2 == 0) "FIRE" else "BACKUP", 0, "", PendingIntent.FLAG_NO_CREATE)
                ?.let { am.cancel(it); it.cancel() }
        }
        pi(c, 9100, "BED", 0, "", PendingIntent.FLAG_NO_CREATE)?.let { am.cancel(it); it.cancel() }
        var fail = !exact
        var first = 0L
        for ((i, a) in Store.alarms(c).withIndex()) {
            if (i >= 64) break
            if (!a.on) continue
            val t = try { a.next() } catch (e: Exception) { -1L }
            if (t < 0) continue
            if (first == 0L || t < first) first = t
            val cfg = a.toJson().toString()
            try {
                am.setAlarmClock(AlarmManager.AlarmClockInfo(t, show(c)),
                    pi(c, i * 2, "FIRE", t, cfg, PendingIntent.FLAG_UPDATE_CURRENT)!!)
            } catch (e: Exception) { fail = true; continue }
            arm(am, exact, t + BACKUP_MS, pi(c, i * 2 + 1, "BACKUP", t, cfg, PendingIntent.FLAG_UPDATE_CURRENT)!!)
        }
        val bed = Settings.bed(c).reminderAt(first, System.currentTimeMillis())
        if (bed > 0) arm(am, exact, bed, pi(c, 9100, "BED", bed, "", PendingIntent.FLAG_UPDATE_CURRENT)!!)
        Store.set(c, "nextT", first.toString())
        Store.set(c, "schedFail", if (fail) "1" else "")
        Timers.scheduleAll(c)
        ClockWidget.refresh(c)
    }

    fun bedNotify(c: Context) {
        val nm = c.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel("bed", "Bedtime", NotificationManager.IMPORTANCE_DEFAULT))
        nm.notify(8, Notification.Builder(c, "bed").setSmallIcon(R.drawable.ic_small)
            .setContentTitle("Time to wind down")
            .setContentText("Bedtime in ${Settings.bed(c).wind} min to reach your sleep goal")
            .setAutoCancel(true).setContentIntent(show(c)).build())
    }

    /** Earliest ring time across [list], or -1. */
    fun nextOf(list: List<Alarm>, now: Long = System.currentTimeMillis()): Long =
        list.filter { it.on }.map { it.next(now) }.filter { it > 0 }.minOrNull() ?: -1L

    fun snooze(c: Context, cfg: String, secs: Int, test: Boolean) {
        val t = System.currentTimeMillis() + secs * 1000L
        val am = c.getSystemService(AlarmManager::class.java)
        val p = pi(c, 9000, "FIRE", t, cfg, PendingIntent.FLAG_UPDATE_CURRENT, test)!!
        try { am.setAlarmClock(AlarmManager.AlarmClockInfo(t, show(c)), p) }
        catch (e: Exception) { arm(am, canExact(c), t, p) }
        Store.set(c, "snoozeT", t.toString())
    }

    /** Title and body for a ringing notification: an alarm says "Alarm ringing", a timer says "Timer finished". */
    internal fun ringTexts(cfg: String?): Pair<String, String> {
        val o = try { JSONObject(if (cfg.isNullOrEmpty()) "{}" else cfg) } catch (e: Exception) { JSONObject() }
        val timer = o.optInt("timer", 0) == 1
        return o.optString("l", "").ifEmpty { if (timer) "Timer" else "Alarm" } to (if (timer) "Timer finished" else "Alarm ringing")
    }

    /** Android refused to start the ring service from the background: fall back to a loud repeating full-screen notification. */
    fun fallbackRing(c: Context, cfg: String?, test: Boolean) {
        try {
            val o = JSONObject(if (cfg.isNullOrEmpty()) "{}" else cfg)
            val (label, body) = ringTexts(cfg)
            val nm = c.getSystemService(NotificationManager::class.java)
            val aa = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()
            val ch = NotificationChannel("ringfb", "Alarm (backup mode)", NotificationManager.IMPORTANCE_HIGH).apply {
                setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM), aa)
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 700, 500, 700)
                setBypassDnd(true)
            }
            nm.createNotificationChannel(ch)
            val ra = Intent(c, RingActivity::class.java).putExtra("l", label).putExtra("test", test)
                .putExtra("sz", o.optInt("sz", 5)).putExtra("cfg", cfg)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            val full = PendingIntent.getActivity(c, 1, ra, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            val n = Notification.Builder(c, "ringfb").setSmallIcon(R.drawable.ic_small)
                .setContentTitle(label).setContentText(body)
                .setCategory(Notification.CATEGORY_ALARM).setFullScreenIntent(full, true)
                // Tapping or swiping it must not silence a challenge alarm, and nothing else stops it after 10 minutes like RingService does.
                .setContentIntent(full).setAutoCancel(false).setOngoing(o.optInt("ch", 0) != 0)
                .setTimeoutAfter(600_000L).build()
            n.flags = n.flags or Notification.FLAG_INSISTENT
            nm.notify(9, n)
        } catch (e: Exception) { }
    }
}
