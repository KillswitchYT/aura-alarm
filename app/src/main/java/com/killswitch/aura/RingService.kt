package com.killswitch.aura

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.Ringtone
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Foreground service that owns the sound, vibration and wake lock while an alarm or timer rings. */
class RingService : Service() {
    private val h = Handler(Looper.getMainLooper())
    private var mp: MediaPlayer? = null
    private var rt: Ringtone? = null
    private var vib: Vibrator? = null
    private var wl: PowerManager.WakeLock? = null
    private var au: AudioManager? = null
    private var origVol = -1
    private var cfg = JSONObject()
    private var cfgStr = "{}"
    private var label = "Alarm"
    private var snoozeMin = 5
    private var challenge = 0
    private var timer = false
    private var test = false
    private var ringing = false
    private var t0 = 0L

    private val ramp = object : Runnable {
        override fun run() {
            val p = mp ?: return
            val v = minOf(1f, (System.currentTimeMillis() - t0) / 30_000f)
            try { p.setVolume(v, v) } catch (e: Exception) { }
            if (v < 1f) h.postDelayed(this, 500)
        }
    }
    private val auto = Runnable { Store.set(this, "autoStop", "1"); stopRing() }

    override fun onBind(i: Intent?): IBinder? = null

    /** "Max + vibrate" ring style has no snooze, and neither do timers. */
    private fun snoozeAllowed() = !timer && Settings.ringStyle(this) != 2

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            "DISMISS" -> { stopRing(); return START_NOT_STICKY }
            "SNOOZE" -> {
                intent.getStringExtra("cfg")?.let { adopt(it) } // a fresh instance (fallback mode) has no state, so the screen sends it
                if (intent.hasExtra("test")) test = intent.getBooleanExtra("test", false)
                val secs = if (test) 10 else snoozeMin * 60
                if (secs > 0 && snoozeAllowed()) { Scheduler.snooze(this, cfgStr, secs, test); stopRing() }
                return START_NOT_STICKY
            }
        }
        adopt(intent?.getStringExtra("cfg") ?: "{}")
        test = intent?.getBooleanExtra("test", false) ?: false
        foreground()
        if (ringing) return START_NOT_STICKY
        ringing = true
        Store.set(this, "lastFired", SimpleDateFormat("HH:mm:ss", Locale.US).format(Date()))
        sound()
        h.postDelayed(auto, 600_000L)
        try {
            startActivity(ringIntent().addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP))
        } catch (e: Exception) { } // blocked in the background, the full-screen notification covers it
        return START_NOT_STICKY
    }

    private fun adopt(s: String) {
        cfgStr = s
        cfg = try { JSONObject(s) } catch (e: Exception) { JSONObject() }
        timer = cfg.optInt("timer", 0) == 1
        label = cfg.optString("l", "").ifEmpty { if (timer) "Timer" else "Alarm" }
        snoozeMin = cfg.optInt("sz", 5)
        challenge = cfg.optInt("ch", 0)
    }

    private fun ringIntent() = Intent(this, RingActivity::class.java)
        .putExtra("l", label).putExtra("test", test).putExtra("sz", if (snoozeAllowed()) snoozeMin else 0)
        .putExtra("ch", challenge).putExtra("timer", if (timer) 1 else 0).putExtra("cfg", cfgStr)

    private fun act(a: String, code: Int): PendingIntent =
        PendingIntent.getService(this, code, Intent(this, RingService::class.java).setAction(a),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    private fun foreground() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel("ring", "Alarm ringing", NotificationManager.IMPORTANCE_HIGH).apply {
            setSound(null, null); enableVibration(false); setBypassDnd(true)
        })
        val full = PendingIntent.getActivity(this, 1, ringIntent(), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val b = Notification.Builder(this, "ring").setSmallIcon(R.drawable.ic_small).setContentTitle(label)
            .setContentText(if (timer) "Timer finished" else "Alarm ringing")
            .setCategory(Notification.CATEGORY_ALARM).setOngoing(true)
            .setFullScreenIntent(full, true).setContentIntent(full)
        if ((snoozeMin > 0 || test) && snoozeAllowed()) b.addAction(0, "Snooze", act("SNOOZE", 2))
        if (challenge == 0) b.addAction(0, "Dismiss", act("DISMISS", 3)) // a challenge alarm cannot be dismissed from the shade
        try {
            if (Build.VERSION.SDK_INT >= 29) startForeground(7, b.build(), ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
            else startForeground(7, b.build())
        } catch (e: Exception) { Store.set(this, "fgErr", e.toString()) }
    }

    private fun soundUri(snd: String): Uri = when {
        snd.startsWith("raw:") -> Uri.parse("android.resource://$packageName/" +
            resources.getIdentifier("tone" + snd.removePrefix("raw:"), "raw", packageName))
        snd.isNotEmpty() -> Uri.parse(snd)
        else -> defUri()
    }

    private fun defUri(): Uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
        ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)

    private fun sound() {
        val a = getSystemService(AudioManager::class.java).also { au = it }
        val ro = Settings.ringStyle(this)
        val gradual = if (cfg.has("gr")) cfg.optInt("gr") == 1 else ro == 0
        val max = a.getStreamMaxVolume(AudioManager.STREAM_ALARM)
        origVol = a.getStreamVolume(AudioManager.STREAM_ALARM)
        try {
            if (ro == 2) a.setStreamVolume(AudioManager.STREAM_ALARM, max, 0)
            else if (origVol == 0) a.setStreamVolume(AudioManager.STREAM_ALARM, maxOf(1, max / 2), 0)
        } catch (e: Exception) { }
        wl = getSystemService(PowerManager::class.java).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "aura:ring").also { it.acquire(600_000L) }
        val attrs = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()
        val u = soundUri(cfg.optString("snd", ""))
        try {
            mp = MediaPlayer().apply {
                setAudioAttributes(attrs); setDataSource(this@RingService, u)
                isLooping = true; prepare()
                if (gradual) setVolume(0f, 0f)
                start()
            }
            if (gradual) { t0 = System.currentTimeMillis(); h.post(ramp) }
        } catch (e: Exception) {
            Store.set(this, "soundErr", e.toString())
            try { mp?.release() } catch (x: Exception) { }
            mp = null
            try {
                var r = RingtoneManager.getRingtone(this, u).apply { audioAttributes = attrs; if (Build.VERSION.SDK_INT >= 28) isLooping = true; play() }
                if (!r.isPlaying) r = RingtoneManager.getRingtone(this, defUri()).apply { audioAttributes = attrs; if (Build.VERSION.SDK_INT >= 28) isLooping = true; play() }
                rt = r
            } catch (e2: Exception) { }
        }
        vib = getSystemService(Vibrator::class.java)
        if ((cfg.optInt("vb", 1) == 1 || ro == 2) && vib?.hasVibrator() == true) {
            try { vib?.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 700, 500), 0), AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).build()) } catch (e: Exception) { }
        }
    }

    override fun onDestroy() {
        release()
        super.onDestroy()
    }

    private fun stopRing() {
        release()
        getSystemService(NotificationManager::class.java).cancel(9)
        RingActivity.cur?.finish()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    /** Stops sound, vibration and the wake lock. Safe to call twice. */
    private fun release() {
        h.removeCallbacksAndMessages(null)
        try { mp?.stop() } catch (e: Exception) { }
        try { mp?.release() } catch (e: Exception) { }
        mp = null
        try { rt?.stop() } catch (e: Exception) { }
        rt = null
        vib?.cancel(); vib = null
        if (origVol >= 0) {
            try { au?.setStreamVolume(AudioManager.STREAM_ALARM, origVol, 0) } catch (e: Exception) { }
            origVol = -1
        }
        try { if (wl?.isHeld == true) wl?.release() } catch (e: Exception) { }
        wl = null
        ringing = false
    }
}
