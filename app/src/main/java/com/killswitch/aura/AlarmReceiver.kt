package com.killswitch.aura

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.PowerManager

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        try {
            // Bridges the gap until RingService takes its own wake lock. Times out by itself.
            c.getSystemService(PowerManager::class.java)
                .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "aura:fire").acquire(15_000L)
            handle(c, i)
        } catch (e: Exception) {
            Store.set(c, "rxErr", e.toString())
        }
    }

    private fun handle(c: Context, i: Intent) {
        when (i.action) {
            "TIMER" -> { Timers.fired(c, i.getIntExtra("id", 0)); return }
            "T_ADD", "T_STOP" -> { Timers.action(c, i); return }
            "BED" -> { Scheduler.bedNotify(c); Scheduler.scheduleAll(c); return }
        }
        val t = i.getLongExtra("t", 0)
        val code = i.getIntExtra("i", 0)
        val key = "f${code / 2}"
        if (t.toString() == Store.get(c, key)) return // main and backup both arrived, ring once
        if (i.action == "BACKUP") Store.set(c, "missed", "1")
        Store.set(c, key, t.toString())
        if (code < 9000) markFired(c, code / 2, t) else if (code == 9000) Store.set(c, "snoozeT", "0")
        startRing(c, i.getStringExtra("cfg"), i.getBooleanExtra("test", false))
        Scheduler.scheduleAll(c)
    }

    companion object {
        /** Starts the ring service. If Android refuses (background start limits), a loud full-screen notification takes over. */
        fun startRing(c: Context, cfg: String?, test: Boolean) {
            try {
                c.startForegroundService(Intent(c, RingService::class.java).putExtra("cfg", cfg).putExtra("test", test))
            } catch (e: Exception) {
                Store.set(c, "fgsErr", e.toString())
                Scheduler.fallbackRing(c, cfg, test)
            }
        }

        /** One-time and dated alarms switch themselves off after ringing; a spent skip is cleared. */
        private fun markFired(c: Context, idx: Int, t: Long) {
            val list = Store.alarms(c).toMutableList()
            val a = list.getOrNull(idx) ?: return
            val once = a.days.isEmpty() || a.date.isNotEmpty()
            list[idx] = a.copy(on = if (once) false else a.on, skip = if (a.skip <= t) 0 else a.skip)
            Store.saveAlarms(c, list)
        }
    }
}
