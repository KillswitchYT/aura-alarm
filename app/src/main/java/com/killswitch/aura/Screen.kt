package com.killswitch.aura

import android.app.Activity
import android.view.WindowManager

/** Night clock: dims the screen right down and keeps it on. */
object Night {
    fun set(a: Activity, on: Boolean) {
        val lp = a.window.attributes
        lp.screenBrightness = if (on) 0.02f else WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
        a.window.attributes = lp
        if (on) a.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        else a.window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }
}
