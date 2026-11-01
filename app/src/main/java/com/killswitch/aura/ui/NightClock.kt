package com.killswitch.aura.ui

import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.killswitch.aura.Night
import com.killswitch.aura.ampm
import com.killswitch.aura.fmtTime
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.random.Random

private val Dim = Color(0xFF7A1A1A)

/** Black screen, dim red clock. Drifts slowly every 30 s so no pixel stays lit (burn-in). Tap or Back to leave. */
@Composable
fun NightClock(is24: Boolean, now: Long, onExit: () -> Unit) {
    val activity = LocalContext.current as ComponentActivity
    DisposableEffect(Unit) { Night.set(activity, true); onDispose { Night.set(activity, false) } }
    BackHandler { onExit() }

    val dx = remember { Animatable(0f) }
    val dy = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000)
            dx.animateTo(Random.nextFloat() * .14f - .07f, tween(4000, easing = FastOutSlowInEasing))
            dy.animateTo(Random.nextFloat() * .14f - .07f, tween(4000, easing = FastOutSlowInEasing))
        }
    }
    val cal = Calendar.getInstance().apply { timeInMillis = now }
    val h = cal.get(Calendar.HOUR_OF_DAY)
    Box(
        Modifier.fillMaxSize().background(Color.Black)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onExit() },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier.graphicsLayer { translationX = dx.value * size.width; translationY = dy.value * size.height },
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            BasicText(fmtTime(h, cal.get(Calendar.MINUTE), is24), style = ts(96f, Dim, FontWeight.Bold, -3f))
            if (!is24) BasicText(ampm(h), style = ts(22f, Dim, FontWeight.Bold))
            BasicText(SimpleDateFormat("EEEE, MMMM d", Locale.getDefault()).format(Date(now)), style = ts(18f, Dim))
            BasicText("Tap to exit", style = ts(13f, Dim.copy(alpha = .6f)))
        }
    }
}
