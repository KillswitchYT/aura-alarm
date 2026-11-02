package com.killswitch.aura

import kotlin.math.roundToInt

/** Alarm volume rules, kept free of Android so they can be tested. The alarm stream is a different slider from "ringtone". */
object Vol {
    /** The Sound check stays off below this share of the maximum, because a quiet alarm is an alarm you sleep through. */
    const val MIN_PERCENT = 60

    fun ok(v: Int, max: Int) = max > 0 && v * 100 >= max * MIN_PERCENT

    /** A slider position (0 to 1) as a volume step. */
    fun step(fraction: Float, max: Int): Int = (fraction.coerceIn(0f, 1f) * max).roundToInt().coerceIn(0, max)

    fun fraction(v: Int, max: Int): Float = if (max <= 0) 0f else (v / max.toFloat()).coerceIn(0f, 1f)

    /** What the one-tap fix sets: loud, but not the very top. */
    fun recommended(max: Int): Int = (max * .8f).roundToInt().coerceIn(1, maxOf(1, max))

    /**
     * After a ring: give the old volume back only if the ring itself moved it ([weSet] is what we set, -1 if we did not)
     * and nobody has touched it since. A change the person made with the volume buttons must survive.
     */
    fun restoreTo(orig: Int, weSet: Int, now: Int): Int? = if (weSet >= 0 && orig >= 0 && now == weSet && now != orig) orig else null
}
