package com.killswitch.aura.ui

import androidx.activity.ComponentActivity
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.killswitch.aura.Alarm
import com.killswitch.aura.AlarmReceiver
import com.killswitch.aura.Check
import com.killswitch.aura.Perms
import com.killswitch.aura.Scheduler
import com.killswitch.aura.Settings
import com.killswitch.aura.Store
import com.killswitch.aura.WorldClock
import com.killswitch.aura.glass.LiquidBottomTab
import com.killswitch.aura.glass.LiquidBottomTabs
import com.killswitch.aura.glass.LiquidButton
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

private const val NONE = -2
private const val NEW = -1

private class TabInfo(val icon: Ic, val label: String)

private val TABS = listOf(
    TabInfo(Ic.Alarm, "Alarms"), TabInfo(Ic.World, "World"), TabInfo(Ic.Timer, "Timers"),
    TabInfo(Ic.Stopwatch, "Stopwatch"), TabInfo(Ic.Setup, "Setup"),
)

@Composable
fun AuraApp(reload: Int, startTab: Int) {
    val ctx = LocalContext.current
    val activity = ctx as ComponentActivity
    var tab by rememberSaveable { mutableIntStateOf(startTab) }
    var state by remember { mutableStateOf<AlarmsState>(AlarmsState.Loading) }
    var loadKey by remember { mutableIntStateOf(0) }
    var checks by remember { mutableStateOf(Perms.check(ctx)) }
    var editing by remember { mutableIntStateOf(NONE) }
    var picking by remember { mutableStateOf(false) }
    var night by remember { mutableStateOf(false) }
    var worldRev by remember { mutableIntStateOf(0) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val seen = remember { mutableSetOf<Any>() }
    val is24 = Settings.is24(ctx)
    val backdrop = rememberLayerBackdrop()
    val alarms = (state as? AlarmsState.Ready)?.list.orEmpty()

    LaunchedEffect(startTab) { tab = startTab }
    // Real async read: the skeleton shows only if storage is slow, errors show only if the data is damaged.
    LaunchedEffect(reload, loadKey) {
        val r = withContext(Dispatchers.IO) { Store.tryAlarms(ctx) }
        state = if (r == null) AlarmsState.Failed else AlarmsState.Ready(r)
    }
    LaunchedEffect(Unit) { while (true) { now = System.currentTimeMillis(); delay(1000L - now % 1000) } }
    DisposableEffect(activity) {
        val obs = LifecycleEventObserver { _, e ->
            if (e == Lifecycle.Event.ON_RESUME) { loadKey++; checks = Perms.check(ctx) }
        }
        activity.lifecycle.addObserver(obs)
        onDispose { activity.lifecycle.removeObserver(obs) }
    }

    fun save(list: List<Alarm>) {
        val sorted = list.sortedBy { it.time }
        state = AlarmsState.Ready(sorted)
        Store.saveAlarms(ctx, sorted)
        Scheduler.scheduleAll(ctx)
    }

    // Tab change: the old page slides and fades out while the new one slides and fades in, 280 ms, in the direction of travel.
    var shown by remember { mutableIntStateOf(tab) }
    var leaving by remember { mutableIntStateOf(-1) }
    var dir by remember { mutableIntStateOf(1) }
    val slide = remember { Animatable(1f) }
    LaunchedEffect(tab) {
        if (tab != shown) {
            dir = if (tab > shown) 1 else -1
            leaving = shown
            shown = tab
            slide.snapTo(0f)
            slide.animateTo(1f, tween(280, easing = FastOutSlowInEasing))
            leaving = -1
        }
    }

    // Two things the engine writes down when it had to work around a problem. Say them out loud instead of hiding them.
    val flags = remember(reload, loadKey) { (Store.get(ctx, "missed") == "1") to (Store.get(ctx, "schedFail") == "1") }
    val armFailed = flags.second && checks.firstOrNull()?.ok == true
    val notice = when {
        armFailed -> "Android refused to arm an alarm. Tap to try again."
        flags.first -> "Your last alarm only rang because its backup fired. Tap to dismiss."
        else -> null
    }

    val bars = WindowInsets.systemBars.asPaddingValues()
    val pad = PaddingValues(16.dp, bars.calculateTopPadding() + 12.dp, 16.dp, bars.calculateBottomPadding() + 190.dp)

    val page: @Composable (Int) -> Unit = { i ->
        when (i) {
            0 -> HomeTab(
                state, now, is24, checks.count { it.ok }, checks.size, pad, seen,
                onProtection = { tab = 4 },
                onToggle = { idx, on -> save(alarms.toMutableList().also { l -> l[idx] = l[idx].copy(on = on, skip = 0) }) },
                onOpen = { editing = it },
                onAdd = { editing = NEW },
                onRetry = { state = AlarmsState.Loading; loadKey++ },
                onReset = { Store.set(ctx, "al", "[]"); Scheduler.scheduleAll(ctx); loadKey++ },
                notice = notice,
                onNotice = { if (armFailed) Scheduler.scheduleAll(ctx) else Store.set(ctx, "missed", "0"); loadKey++ },
            )
            4 -> SetupTab(
                checks, pad, now, is24, reload * 1000 + loadKey, seen,
                onFix = { Perms.fix(activity, it); checks = Perms.check(ctx) },
                onRunFix = { Perms.runFix(activity, it); checks = Perms.check(ctx) },
                onNight = { night = true },
                onChanged = { loadKey++; checks = Perms.check(ctx) },
            )
            1 -> WorldTab(reload * 1000 + loadKey + worldRev * 100_000, now, is24, pad, seen) { picking = true }
            2 -> TimersTab(reload * 1000 + loadKey, now, pad, seen)
            else -> StopwatchTab(reload * 1000 + loadKey, pad, seen)
        }
    }

    Box(Modifier.fillMaxSize()) {
        // Everything inside this box is what the glass refracts.
        Box(Modifier.layerBackdrop(backdrop).fillMaxSize()) {
            Canvas(Modifier.fillMaxSize()) { drawAuraScene() }
            if (leaving >= 0) {
                Box(Modifier.fillMaxSize().graphicsLayer { alpha = 1f - slide.value; translationX = -dir * slide.value * size.width * .10f }) { page(leaving) }
            }
            Box(Modifier.fillMaxSize().graphicsLayer { alpha = slide.value; translationX = dir * (1f - slide.value) * size.width * .10f }) { page(shown) }
        }

        LiquidBottomTabs(
            selectedTabIndex = { tab }, onTabSelected = { tab = it }, backdrop = backdrop, tabsCount = TABS.size,
            modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            TABS.forEachIndexed { i, t ->
                LiquidBottomTab({ tab = i }) {
                    Icon(t.icon, if (tab == i) Aura.Text else Aura.Muted, Modifier.size(24.dp))
                    BasicText(t.label, style = ts(11f, if (tab == i) Aura.Text else Aura.Muted, FontWeight.Bold))
                }
            }
        }

        // The empty screen has its own button, so the floating one only appears once there is something to add to.
        if (tab == 0 && editing == NONE && alarms.isNotEmpty()) {
            LiquidButton(
                { editing = NEW }, backdrop,
                Modifier.align(Alignment.BottomEnd).navigationBarsPadding().padding(end = 20.dp, bottom = 100.dp),
                tint = Aura.Accent,
            ) {
                Icon(Ic.Plus, Aura.OnAccent, Modifier.size(22.dp))
                BasicText("New alarm", style = ts(16f, Aura.OnAccent, FontWeight.ExtraBold))
            }
        }

        if (picking) {
            CityPicker(backdrop, now, onPick = { WorldClock.add(ctx, it); worldRev++; picking = false }, onClose = { picking = false })
        }

        if (editing != NONE) {
            val idx = editing
            val start = if (idx == NEW) Alarm(gradual = Settings.ringStyle(ctx) == 0) else alarms.getOrNull(idx)
            if (start != null) {
                Editor(
                    backdrop, start, idx == NEW, is24,
                    onSave = { a -> save(if (idx == NEW) alarms + a else alarms.toMutableList().also { l -> l[idx] = a }); editing = NONE },
                    onDelete = { save(alarms.toMutableList().also { l -> l.removeAt(idx) }); editing = NONE },
                    onTest = { a -> AlarmReceiver.startRing(ctx, a.toJson().toString(), true) },
                    onClose = { editing = NONE },
                )
            }
        }

        if (night) NightClock(is24, now) { night = false }
    }
}
