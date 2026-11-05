package com.killswitch.aura

import kotlin.math.pow

/**
 * The two colour sets as plain ARGB numbers, so the readability of every text and background pair can be tested without Android.
 * The UI turns these into Compose colours (see Aura in ui/Theme.kt).
 */
object Palettes {
    class P(
        val ink: Int,         // the scene's base colour
        val lift: Int,        // the soft glow top left
        val lift2: Int,       // the soft glow bottom right, also what small glass controls refract when they sit on the scene
        val card: Int,
        val sheet: Int,       // sheets (editor, city picker)
        val text: Int,
        val muted: Int,
        val accent: Int,      // shapes: ring, toggle track, button fill
        val accentText: Int,  // the accent when it has to be read as text
        val onAccent: Int,    // text on top of an accent fill
        val mint: Int,        // on / good, as text
        val rose: Int,        // off / danger, as text
    )

    private fun c(rgb: Int) = 0xFF000000.toInt() or rgb

    val DARK = P(
        ink = c(0x090B10), lift = c(0x1A2030), lift2 = c(0x121722), card = c(0x181D2A), sheet = c(0x151A26),
        text = c(0xECEEF3), muted = c(0x9AA0AE), accent = c(0xFFB07A), accentText = c(0xFFB07A), onAccent = c(0x1C1208),
        mint = c(0x6FE3A0), rose = c(0xFF8A80),
    )

    /** Cool light grey scene, near-black text. The accent is a deeper apricot so it still reads on a light background. */
    val LIGHT = P(
        ink = c(0xE9ECF2), lift = c(0xFBFCFE), lift2 = c(0xF2F4F8), card = c(0xFFFFFF), sheet = c(0xF7F8FB),
        text = c(0x13161D), muted = c(0x586070), accent = c(0xD2661B), accentText = c(0xA84B14), onAccent = c(0x1C1208),
        mint = c(0x0B7A41), rose = c(0xB8322A),
    )

    private fun channel(v: Int): Double { val s = v / 255.0; return if (s <= 0.03928) s / 12.92 else ((s + 0.055) / 1.055).pow(2.4) }

    fun luminance(argb: Int): Double =
        0.2126 * channel((argb shr 16) and 255) + 0.7152 * channel((argb shr 8) and 255) + 0.0722 * channel(argb and 255)

    /** WCAG contrast ratio, 1 (identical) to 21 (black on white). Body text should reach 4.5, shapes 3. */
    fun contrast(a: Int, b: Int): Double {
        val la = luminance(a)
        val lb = luminance(b)
        return (maxOf(la, lb) + 0.05) / (minOf(la, lb) + 0.05)
    }

    /**
     * Which palette to show. [mode] is the person's choice: 0 follows the phone, 1 dark, 2 light.
     * A screen that is always dark (the ring, so waking up is not a flash of white) passes [forcedDark].
     */
    fun useDark(mode: Int, systemDark: Boolean, forcedDark: Boolean): Boolean = forcedDark || mode == 1 || (mode != 2 && systemDark)
}
