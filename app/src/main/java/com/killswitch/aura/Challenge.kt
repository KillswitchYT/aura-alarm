package com.killswitch.aura

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.content.Context
import kotlin.math.sqrt
import kotlin.random.Random

/** Wake-up challenge 1: three sums in a row. A wrong answer re-rolls the sum but keeps your progress. */
class MathChallenge(private val rnd: Random = Random.Default, val goal: Int = 3) {
    var a = 0; private set
    var b = 0; private set
    var solved = 0; private set
    init { roll() }
    private fun roll() { a = 7 + rnd.nextInt(40); b = 3 + rnd.nextInt(40) }
    val remaining get() = goal - solved
    val prompt get() = "$a + $b = ?"
    val done get() = solved >= goal
    /** Returns true when correct. Either way a fresh sum follows unless the challenge is finished. */
    fun answer(v: Int?): Boolean {
        val ok = v == a + b
        if (ok) solved++
        if (!done) roll()
        return ok
    }
}

/** Wake-up challenge 2: shake 25 times. A shake is acceleration above 22 m/s2, at most one every 180 ms. */
class ShakeCounter(val goal: Int = 25, private val threshold: Float = 22f, private val gapMs: Long = 180) {
    var count = 0; private set
    private var last = 0L
    val done get() = count >= goal
    val progress get() = (count.toFloat() / goal).coerceIn(0f, 1f)
    /** Feed raw accelerometer values (gravity included). Returns true when this sample counted as a shake. */
    fun feed(x: Float, y: Float, z: Float, nowMs: Long): Boolean {
        if (done) return false
        val m = sqrt(x * x + y * y + z * z)
        if (m > threshold && nowMs - last > gapMs) { last = nowMs; count++; return true }
        return false
    }
}

/** Binds a [ShakeCounter] to the phone's accelerometer. Call [start] and [stop] with the screen's lifecycle. */
class ShakeDetector(private val c: Context, private val counter: ShakeCounter, private val onShake: (Int) -> Unit) : SensorEventListener {
    private val sm = c.getSystemService(SensorManager::class.java)
    fun start() { sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)?.let { sm.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME) } }
    fun stop() = sm.unregisterListener(this)
    override fun onSensorChanged(e: SensorEvent) {
        if (counter.feed(e.values[0], e.values[1], e.values[2], System.currentTimeMillis())) onShake(counter.count)
    }
    override fun onAccuracyChanged(s: Sensor?, a: Int) { }
}
