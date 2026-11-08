package com.killswitch.aura

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * An alarm that rings its full 10 minutes with nobody answering goes quiet by itself. With no trace at all the person never learns it
 * happened, so a note is left on the phone. Switchable in Setup, Safety nets.
 */
object Missed {
    /** Only a real alarm that nobody answered. A test is meant to be ignored and a timer has its own notification. */
    fun shouldNotify(enabled: Boolean, test: Boolean, timer: Boolean) = enabled && !test && !timer

    fun text(label: String, at: String) = "$label rang at $at and nobody answered it."

    fun post(c: Context, label: String, firedAt: Long, is24: Boolean) {
        try {
            val nm = c.getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(NotificationChannel("missed", "Missed alarms", NotificationManager.IMPORTANCE_DEFAULT))
            val at = SimpleDateFormat(if (is24) "HH:mm" else "h:mm a", Locale.US).format(Date(if (firedAt > 0) firedAt else System.currentTimeMillis()))
            val open = PendingIntent.getActivity(c, 0, Intent(c, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
            nm.notify(11, Notification.Builder(c, "missed").setSmallIcon(R.drawable.ic_small)
                .setContentTitle("Missed alarm").setContentText(text(label, at)).setAutoCancel(true).setContentIntent(open).build())
        } catch (e: Exception) { }
    }
}
