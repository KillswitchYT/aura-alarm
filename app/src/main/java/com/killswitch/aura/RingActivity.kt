package com.killswitch.aura

import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import com.killswitch.aura.ui.RingScreen
import org.json.JSONObject

/** Full-screen ring UI. Sound and vibration live in RingService, this shows the time, the challenge and the buttons. */
class RingActivity : ComponentActivity() {
    private val label = mutableStateOf("Alarm")
    private val snooze = mutableIntStateOf(5)
    private val test = mutableStateOf(false)
    private val timer = mutableStateOf(false)
    private val challenge = mutableIntStateOf(0)
    private val shakes = mutableIntStateOf(0)
    private var cfg = "{}"
    private var counter: ShakeCounter? = null
    private var detector: ShakeDetector? = null
    private var solved = false

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        cur = this
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() { } // an alarm should not vanish by accident
        })
        read(intent)
        setContent {
            RingScreen(
                label = label.value, challenge = challenge.intValue,
                canSnooze = canSnooze(), snoozeText = if (test.value) "Snooze 10 s" else "Snooze ${snooze.intValue} min",
                shakes = shakes.intValue, shakeGoal = SHAKE_GOAL,
                onSnooze = { send("SNOOZE") }, onDismiss = { solved = true; send("DISMISS") },
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        read(intent)
    }

    override fun onResume() { super.onResume(); detector?.start() }
    override fun onPause() { detector?.stop(); super.onPause() }

    override fun onDestroy() {
        detector?.stop()
        if (cur === this) cur = null
        super.onDestroy()
    }

    /** Volume buttons follow the Setup choice. While a challenge is unsolved they can never dismiss. */
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_VOLUME_UP || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
            val locked = challenge.intValue != 0 && !solved
            return when (Settings.volumeAction(Settings.volumeButtons(this), locked)) {
                VolAction.PASS -> super.onKeyDown(keyCode, event)
                VolAction.SNOOZE -> { if (canSnooze()) send("SNOOZE"); true }
                VolAction.DISMISS -> { solved = true; send("DISMISS"); true }
                VolAction.CONSUME -> true
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    /** Same rule as RingService: no snooze for timers or the "Max + vibrate" style, otherwise the button would do nothing. */
    private fun canSnooze() = !timer.value && Settings.ringStyle(this) != 2 && (snooze.intValue > 0 || test.value)

    private fun read(i: Intent) {
        cfg = i.getStringExtra("cfg") ?: "{}"
        val o = try { JSONObject(cfg) } catch (e: Exception) { JSONObject() }
        timer.value = (if (i.hasExtra("timer")) i.getIntExtra("timer", 0) else o.optInt("timer", 0)) == 1
        label.value = i.getStringExtra("l")?.ifEmpty { null } ?: if (timer.value) "Timer" else "Alarm"
        snooze.intValue = if (i.hasExtra("sz")) i.getIntExtra("sz", 5) else o.optInt("sz", 5)
        test.value = i.getBooleanExtra("test", false)
        challenge.intValue = if (i.hasExtra("ch")) i.getIntExtra("ch", 0) else o.optInt("ch", 0)
        solved = false
        detector?.stop(); detector = null; counter = null; shakes.intValue = 0
        if (challenge.intValue == 2) {
            val c = ShakeCounter(SHAKE_GOAL).also { counter = it }
            detector = ShakeDetector(this, c) { n ->
                shakes.intValue = n
                if (c.done) { solved = true; send("DISMISS") }
            }.also { it.start() }
        }
    }

    private fun send(action: String) {
        startService(Intent(this, RingService::class.java).setAction(action).putExtra("cfg", cfg).putExtra("test", test.value))
    }

    companion object {
        var cur: RingActivity? = null
        private const val SHAKE_GOAL = 25
    }
}
