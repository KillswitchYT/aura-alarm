package com.killswitch.aura

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.mutableIntStateOf
import com.killswitch.aura.ui.AuraApp

class MainActivity : ComponentActivity() {
    private val reload = mutableIntStateOf(0)
    private val startTab = mutableIntStateOf(0)

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        route(intent)
        Scheduler.scheduleAll(this)
        setContent { AuraApp(reload.intValue, startTab.intValue) }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        route(intent)
        Scheduler.scheduleAll(this)
        reload.intValue++
    }

    /** Voice-assistant intents create the alarm or timer, then we jump to the matching tab. */
    private fun route(i: Intent?) {
        when (Intents.handle(this, i)) {
            "timer" -> startTab.intValue = 2
            "home" -> startTab.intValue = 0
        }
    }
}
