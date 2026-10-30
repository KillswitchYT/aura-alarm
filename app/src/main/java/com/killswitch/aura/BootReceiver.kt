package com.killswitch.aura

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        try { Scheduler.scheduleAll(c) } catch (e: Exception) { Store.set(c, "bootErr", e.toString()) }
    }
}
