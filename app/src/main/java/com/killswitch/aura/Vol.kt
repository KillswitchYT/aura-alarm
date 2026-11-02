package com.killswitch.aura

import kotlin.math.roundToInt

/**
 * Alarm loudness, kept free of Android so it can be tested.
 * The phone's alarm slider only has about 15 steps and Samsung hides it, so the app rings with the phone's alarm volume at full
 * and applies its own 1 to 100 percent on top. [gain] turns the slider into the player volume on a loudness curve.
 */
object Vol {
    /** The Sound check stays off below this, because a quiet alarm is an alarm you sleep through. */
    const val MIN_PERCENT = 60

    /** The slider's lowest point. A silent alarm is not an alarm. */
    const val FLOOR = 10

    /** What the one-tap fix sets: loud, but not the very top. */
    const val RECOMMENDED = 80

    fun ok(percent: Int) = percent >= MIN_PERCENT

    /** A slider position (0 to 1) as a whole percent, 1% steps. */
    fun percent(fraction: Float): Int = (fraction.coerceIn(0f, 1f) * 100).roundToInt().coerceIn(FLOOR, 100)

    fun fraction(percent: Int): Float = percent.coerceIn(0, 100) / 100f

    /** Player volume for a slider percent. Squared, so the slider feels even: half way sounds about half as loud, not almost full. */
    fun gain(percent: Int): Float { val p = percent.coerceIn(0, 100) / 100f; return p * p }

    /**
     * After a ring: give the old system volume back only if the ring itself moved it ([weSet] is what we set, -1 if we did not)
     * and nobody has touched it since. A change the person made with the volume buttons must survive.
     */
    fun restoreTo(orig: Int, weSet: Int, now: Int): Int? = if (weSet >= 0 && orig >= 0 && now == weSet && now != orig) orig else null
}
