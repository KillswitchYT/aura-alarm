package com.killswitch.aura

/**
 * Typing a timer on a numpad: digits slide in from the right, so 5, 0, 0 reads 00h 05m 00s.
 * Kept free of Android so it can be tested.
 */
object TimerEntry {
    const val MAX_DIGITS = 6
    private const val MAX_SECONDS = 359_999 // 99:59:59

    /** Adds digits. A leading zero changes nothing, and there is room for six digits. Anything that is not a digit is ignored. */
    fun push(d: String, add: String): String {
        var r = d
        for (c in add) {
            if (!c.isDigit()) continue
            if (r.isEmpty() && c == '0') continue
            if (r.length >= MAX_DIGITS) break
            r += c
        }
        return r
    }

    fun pop(d: String): String = d.dropLast(1)

    /** Hours, minutes, seconds exactly as typed, so 90 shows as 00h 00m 90s until you press Start. */
    fun parts(d: String): Triple<Int, Int, Int> {
        val p = d.padStart(MAX_DIGITS, '0').takeLast(MAX_DIGITS)
        return Triple(p.substring(0, 2).toInt(), p.substring(2, 4).toInt(), p.substring(4, 6).toInt())
    }

    fun seconds(d: String): Int {
        val (h, m, s) = parts(d)
        return minOf(h * 3600 + m * 60 + s, MAX_SECONDS)
    }
}
