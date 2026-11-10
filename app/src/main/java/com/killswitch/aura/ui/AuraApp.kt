package com.killswitch.aura.ui

import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.graphics.toArgb
import androidx.activity.ComponentActivity
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
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
import androidx.compose.runtime.CompositionLocalProvider
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
import androidx.compose.ui.graphics.lerp
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
import com.killswitch.aura.Weekend
import com.killswitch.aura.WorldClock
import com.killswitch.aura.glass.LiquidBottomTab
import com.killswitch.aura.glass.LiquidBottomTabs
import com.killswitch.aura.glass.LiquidButton
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.layout.layout
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.zIndex
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateMapOf
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

    // Theme: follow the phone when asked to, and keep the bar icons readable on whichever palette is showing.
    val sysDark = isSystemInDarkTheme()
    LaunchedEffect(sysDark) { Aura.apply(Settings.theme(ctx), sysDark) }
    val dark = Aura.isDark
    LaunchedEffect(dark) {
        val bars = if (dark) SystemBarStyle.dark(android.graphics.Color.TRANSPARENT) else SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
        activity.enableEdgeToEdge(statusBarStyle = bars, navigationBarStyle = bars)
        activity.window.decorView.setBackgroundColor(Aura.Ink.toArgb())
    }
    var tab by rememberSaveable { mutableIntStateOf(startTab) }
    var state by remember { mutableStateOf<AlarmsState>(AlarmsState.Loading) }
    var loadKey by remember { mutableIntStateOf(0) }
    var checks by remember { mutableStateOf(Perms.check(ctx)) }
    var editing by remember { mutableIntStateOf(NONE) }
    var picking by remember { mutableStateOf(false) }
    var dialog by remember { mutableStateOf<Pair<String, String>?>(null) } // a title and a message for the glass dialog
    remember { Weekend.apply(Settings.weekendMode(ctx)); 0 } // the weekend the day labels use
    var night by remember { mutableStateOf(false) }
    var worldRev by remember { mutableIntStateOf(0) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    // One "already animated" set per tab, wiped each time you arrive, so a tab greets you with its stagger every visit.
    val seenFor = remember { Array(TABS.size) { mutableSetOf<Any>() } }
    val is24 = Settings.is24(ctx)
    val backdrop = rememberLayerBackdrop()
    val scene = rememberLayerBackdrop()
    val alarms = (state as? AlarmsState.Ready)?.list.orEmpty()

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
        val before = alarms
        state = AlarmsState.Ready(sorted)
        Store.saveAlarms(ctx, sorted)
        Scheduler.dropOrphanedSnooze(ctx, before, sorted)
        Scheduler.scheduleAll(ctx)
    }

    // Tab change: the old page slides, shrinks a touch and fades out while the new one grows into place, in the direction of travel.
    // Jumping four tabs (Alarms to Setup) travels further and takes a little longer than moving one.
    var shown by remember { mutableIntStateOf(tab) }
    var leaving by remember { mutableIntStateOf(-1) }
    var dir by remember { mutableIntStateOf(1) }
    var steps by remember { mutableIntStateOf(1) }
    val slide = remember { Animatable(1f) }
    val scope = rememberCoroutineScope()
    var switchInfo by remember { mutableStateOf("") }
    val visited = remember { mutableSetOf(tab) }
    val firstMs = remember { mutableStateMapOf<Int, Int>() } // the first time you open each tab
    val laterMs = remember { mutableStateMapOf<Int, Int>() } // the worst of every visit after that
    var warm by remember { mutableIntStateOf(-1) }
    var warmDone by remember { mutableStateOf(false) }
    // Tabs are built once and kept. A hidden tab is frozen: no clock ticks, no frame loops, no touches. Switching only moves what exists.
    val mounted = remember { mutableStateListOf(tab) }
    val arrivals = remember { mutableStateListOf<Int>().apply { repeat(TABS.size) { add(0) } } }
    val frozenNow = remember { LongArray(TABS.size) { System.currentTimeMillis() } }

    // One tap is one state change. The new page is built in a single step while the slide is already parked at 0, instead of
    // waiting a frame for an effect and then building a whole screen in the same frame the animation starts.
    fun switchTo(i: Int) {
        if (i == shown) { tab = i; return }
        steps = kotlin.math.abs(i - shown).coerceAtLeast(1)
        dir = if (i > shown) 1 else -1
        leaving = shown
        seenFor[i].clear()
        if (i !in mounted) mounted.add(i)
        arrivals[i] = arrivals[i] + 1
        shown = i
        tab = i
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            slide.snapTo(0f)
            slide.animateTo(1f, tween(260 + 40 * steps, easing = FastOutSlowInEasing))
            leaving = -1
        }
        // A meter for the stutter: the longest gap between two frames while the screen changes. Shown at the bottom of Setup.
        scope.launch {
            var last = 0L
            var worst = 0L
            val until = System.nanoTime() + 700_000_000L
            while (System.nanoTime() < until) withFrameNanos { ns -> if (last != 0L) worst = maxOf(worst, ns - last); last = ns }
            val ms = (worst / 1_000_000L).toInt()
            if (visited.add(i)) firstMs[i] = ms else laterMs[i] = maxOf(laterMs[i] ?: 0, ms)
            switchInfo = TABS.indices.filter { it in firstMs }.joinToString("\n") { t ->
                TABS[t].label + ": first visit " + firstMs[t] + " ms" + (laterMs[t]?.let { ", after that " + it + " ms" } ?: "")
            } + "\n" + (if (warmDone) "Pre-loading of the other tabs finished." else "Pre-loading of the other tabs is still running.")
        }
    }
    LaunchedEffect(startTab) { if (startTab != shown) switchTo(startTab) }

    // Build the other tabs one at a time while Home sits untouched, and draw each faintly for a moment so the GPU does its first-time
    // work (effects, shaders) now. After this a tab switch has nothing left to build.
    LaunchedEffect(Unit) {
        delay(1200)
        for (i in TABS.indices) {
            if (i !in mounted) mounted.add(i)
            warm = i
            delay(500)
        }
        warm = -1
        warmDone = true
    }
    val reach = .10f + .04f * (steps - 1)

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

    val page: @Composable (Int, Boolean) -> Unit = { i, active ->
        val seen = seenFor[i]
        if (active) frozenNow[i] = now
        val pageNow = frozenNow[i] // a hidden tab keeps the time it had when you left, so it is not recomposed every second
        when (i) {
            0 -> HomeTab(
                state, pageNow, is24, checks.count { it.ok }, checks.size, pad, seen,
                onProtection = { tab = 4 },
                onToggle = { idx, on -> save(alarms.toMutableList().also { l -> l[idx] = l[idx].copy(on = on, skip = 0) }) },
                onOpen = { editing = it },
                onAdd = { editing = NEW },
                onRetry = { state = AlarmsState.Loading; loadKey++ },
                onReset = { Store.set(ctx, "al", "[]"); Scheduler.scheduleAll(ctx); loadKey++ },
                notice = notice,
                onNotice = { if (armFailed) Scheduler.scheduleAll(ctx) else Store.set(ctx, "missed", "0"); loadKey++ },
                active = active,
            )
            4 -> SetupTab(
                // The minute is all Setup needs, so its many glass controls are not rebuilt every second.
                checks, pad, pageNow / 60_000L * 60_000L, is24, reload * 1000 + loadKey, seen,
                onFix = { Perms.fix(activity, it); checks = Perms.check(ctx) },
                onRunFix = { Perms.runFix(activity, it); checks = Perms.check(ctx) },
                onNight = { night = true },
                onChanged = { loadKey++; checks = Perms.check(ctx) },
                switchInfo = switchInfo,
                active = active,
                onNotice = { t, m -> dialog = t to m },
            )
            1 -> WorldTab(reload * 1000 + loadKey + worldRev * 100_000, pageNow, is24, pad, seen) { picking = true }
            2 -> TimersTab(reload * 1000 + loadKey, pageNow, pad, seen)
            else -> StopwatchTab(reload * 1000 + loadKey, pad, seen, active)
        }
    }

    Box(Modifier.fillMaxSize()) {
        // Everything inside this box is what the glass refracts.
        Box(Modifier.layerBackdrop(backdrop).fillMaxSize()) {
            // The scene on its own, so controls inside a page refract the real background (the library's list demo does the same).
            Box(Modifier.layerBackdrop(scene).fillMaxSize()) { Canvas(Modifier.fillMaxSize()) { drawAuraScene() } }
            CompositionLocalProvider(LocalScene provides scene) {
            for (i in TABS.indices) if (i in mounted) key(i) {
                val isShown = i == shown
                val isLeaving = i == leaving
                val live = isShown || isLeaving
                Box(
                    Modifier.fillMaxSize().zIndex(if (isShown) 2f else if (isLeaving) 1f else 0f)
                        .graphicsLayer {
                            when {
                                isShown -> {
                                    alpha = slide.value; translationX = dir * (1f - slide.value) * size.width * reach
                                    scaleX = .95f + .05f * slide.value; scaleY = scaleX
                                }
                                isLeaving -> {
                                    alpha = 1f - slide.value; translationX = -dir * slide.value * size.width * reach
                                    scaleX = 1f - .05f * slide.value; scaleY = scaleX
                                }
                                else -> alpha = if (i == warm) .01f else 0f // 1% so the GPU really draws it once, 0 the rest of the time
                            }
                            compositingStrategy = CompositingStrategy.ModulateAlpha
                        }
                        .then(
                            if (live) Modifier
                            else Modifier.clearAndSetSemantics { }.pointerInput(Unit) {
                                // A hidden tab swallows any touch that falls through to it, and TalkBack skips it.
                                awaitPointerEventScope { while (true) awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() } }
                            },
                        ),
                ) { CompositionLocalProvider(LocalArrival provides arrivals[i]) { page(i, live) } }
            }
            }
        }

        LiquidBottomTabs(
            selectedTabIndex = { tab }, onTabSelected = { switchTo(it) }, backdrop = backdrop, tabsCount = TABS.size,
            modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            TABS.forEachIndexed { i, t ->
                LiquidBottomTab({ switchTo(i) }) {
                    val on by selectAnim(tab == i)
                    val hop by animateFloatAsState(if (tab == i) 1f else 0f, spring(dampingRatio = .45f, stiffness = 380f), label = "tabHop")
                    Icon(t.icon, lerp(Aura.Muted, Aura.Text, on), Modifier.size(24.dp).graphicsLayer {
                        scaleX = 1f + .14f * hop; scaleY = scaleX; translationY = -3.dp.toPx() * hop
                    })
                    BasicText(t.label, style = ts(11f, lerp(Aura.Muted, Aura.Text, on), FontWeight.Bold))
                }
            }
        }

        // The empty screen has its own button, so the floating one only appears once there is something to add to.
        val fabOn = tab == 0 && editing == NONE && alarms.isNotEmpty()
        val fab by animateFloatAsState(if (fabOn) 1f else 0f, tween(220, easing = FastOutSlowInEasing), label = "fab")
        if (fab > .01f) {
            LiquidButton(
                { if (fabOn) editing = NEW }, backdrop,
                Modifier.align(Alignment.BottomEnd).navigationBarsPadding().padding(end = 20.dp, bottom = 100.dp).graphicsLayer {
                    alpha = fab; scaleX = .85f + .15f * fab; scaleY = scaleX; translationY = (1f - fab) * 28.dp.toPx()
                },
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

        dialog?.let { (t, m) -> NoticeDialog(backdrop, t, m) { dialog = null } }

        if (night) NightClock(is24, now) { night = false }
    }
}
