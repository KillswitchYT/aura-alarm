package com.killswitch.aura

/**
 * Which days are the weekend depends on where you are: Friday and Saturday in Egypt, Saturday and Sunday in much of the world.
 * Days are numbered the way the rest of the app does it: 0 is Sunday and 6 is Saturday.
 */
object Weekend {
    /** A weekend the person chose in Setup, or null to follow the phone's region. */
    @Volatile private var chosen: Set<Int>? = null

    /** The weekend the day labels use right now. */
    val current: Set<Int> get() = chosen ?: auto

    /** What the phone's region says. Asked of the phone's own calendar data, with a short country list as the backup. */
    val auto: Set<Int> by lazy { fromPhone() ?: fromCountry(java.util.Locale.getDefault().country) }

    /** Setup's choice: "auto", or the day numbers written together, like "56" for Friday and Saturday. */
    fun apply(mode: String) { chosen = parse(mode) }

    fun parse(mode: String): Set<Int>? {
        if (mode == "auto" || mode.isEmpty()) return null
        val days = mode.mapNotNull { it.digitToIntOrNull() }.filter { it in 0..6 }.toSet()
        return if (days.isEmpty() || days.size > 3) null else days
    }

    /** Fri and Sat for the countries that keep that weekend, Sat and Sun for everyone else. */
    fun fromCountry(code: String): Set<Int> = when (code.uppercase()) {
        "BH", "BD", "DZ", "EG", "IQ", "IL", "JO", "KW", "LY", "MV", "OM", "QA", "SA", "SD", "SY", "YE" -> setOf(5, 6)
        "IR" -> setOf(5)
        "AF" -> setOf(4, 5)
        else -> setOf(0, 6)
    }

    private fun fromPhone(): Set<Int>? = try {
        val cls = Class.forName("android.icu.util.Calendar")
        val cal = cls.getMethod("getInstance").invoke(null)
        val type = cls.getMethod("getDayOfWeekType", Int::class.javaPrimitiveType)
        val weekend = cls.getField("WEEKEND").getInt(null)
        (1..7).filter { (type.invoke(cal, it) as Int) == weekend }.map { it - 1 }.toSet().takeIf { it.isNotEmpty() && it.size <= 3 }
    } catch (e: Throwable) { null }
}

/** The short sentence under an alarm that says when it repeats. Kept free of Android so it can be tested. */
object Days {
    val SHORT = listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")
    val LONG = listOf("Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday")

    /** "Fri and Sat", "Fri", "Sat and Sun": days named in week order. */
    fun list(days: Set<Int>): String = days.filter { it in 0..6 }.sorted().joinToString(" and ") { SHORT[it] }

    /**
     * "Every weekday", "Every weekend", "Every day except Fri and Sat", "Sun to Tue, Thu" and so on. [weekend] is the set of weekend days
     * of the phone's region, so a Sunday to Thursday alarm in Egypt is "Every weekday".
     */
    fun summary(days: Set<Int>, weekend: Set<Int>): String {
        val set = days.filter { it in 0..6 }.toSet()
        if (set.isEmpty()) return "Once"
        if (set.size == 7) return "Every day"
        val wk = weekend.filter { it in 0..6 }.toSet()
        if (wk.isNotEmpty() && wk.size < 7) {
            if (set == (0..6).toSet() - wk) return "Every weekday"
            if (set == wk) return "Every weekend"
        }
        if (set.size == 1) return "Every " + LONG[set.first()]
        val missing = (0..6).filter { it !in set }
        if (missing.size <= 2) return "Every day except " + missing.joinToString(" and ") { SHORT[it] }
        // Three or more days in a row become "Sun to Tue". Everything else is named.
        val parts = mutableListOf<String>()
        val sorted = set.sorted()
        var i = 0
        while (i < sorted.size) {
            var j = i
            while (j + 1 < sorted.size && sorted[j + 1] == sorted[j] + 1) j++
            if (j - i >= 2) parts += SHORT[sorted[i]] + " to " + SHORT[sorted[j]]
            else for (k in i..j) parts += SHORT[sorted[k]]
            i = j + 1
        }
        return if (parts.size == 2) parts[0] + " and " + parts[1] else parts.joinToString(", ")
    }
}
