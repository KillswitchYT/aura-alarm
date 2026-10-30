package com.killswitch.aura.ui

import androidx.activity.ComponentActivity
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
import com.killswitch.aura.glass.LiquidBottomTab
import com.killswitch.aura.glass.LiquidBottomTabs
import com.killswitch.aura.glass.LiquidButton
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import kotlinx.coroutines.delay

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
    var alarms by remember { mutableStateOf(Store.alarms(ctx)) }
    var checks by remember { mutableStateOf(Perms.check(ctx)) }
    var editing by remember { mutableIntStateOf(NONE) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val is24 = Settings.is24(ctx)
    val backdrop = rememberLayerBackdrop()

    LaunchedEffect(startTab) { tab = startTab }
    LaunchedEffect(reload) { alarms = Store.alarms(ctx) }
    LaunchedEffect(Unit) { while (true) { now = System.currentTimeMillis(); delay(1000L - now % 1000) } }
    DisposableEffect(activity) {
        val obs = LifecycleEventObserver { _, e ->
            if (e == Lifecycle.Event.ON_RESUME) { alarms = Store.alarms(ctx); checks = Perms.check(ctx) }
        }
        activity.lifecycle.addObserver(obs)
        onDispose { activity.lifecycle.removeObserver(obs) }
    }

    fun save(list: List<Alarm>) {
        val sorted = list.sortedBy { it.time }
        alarms = sorted
        Store.saveAlarms(ctx, sorted)
        Scheduler.scheduleAll(ctx)
    }

    val bars = WindowInsets.systemBars.asPaddingValues()
    val pad = PaddingValues(16.dp, bars.calculateTopPadding() + 12.dp, 16.dp, bars.calculateBottomPadding() + 190.dp)

    Box(Modifier.fillMaxSize()) {
        // Everything inside this box is what the glass refracts.
        Box(Modifier.layerBackdrop(backdrop).fillMaxSize()) {
            Canvas(Modifier.fillMaxSize()) { drawAuraScene() }
            when (tab) {
                0 -> HomeTab(
                    alarms, now, is24, checks.count { it.ok }, checks.size, pad,
                    onProtection = { tab = 4 },
                    onToggle = { i, on -> save(alarms.toMutableList().also { l -> l[i] = l[i].copy(on = on, skip = 0) }) },
                    onOpen = { editing = it },
                )
                4 -> SetupTab(checks, pad, { Perms.fix(activity, it); checks = Perms.check(ctx) }, { Perms.runFix(activity, it); checks = Perms.check(ctx) })
                else -> NextUp(TABS[tab].label, pad)
            }
        }

        LiquidBottomTabs(
            selectedTabIndex = { tab }, onTabSelected = { tab = it }, backdrop = backdrop, tabsCount = TABS.size,
            modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            TABS.forEachIndexed { i, t ->
                LiquidBottomTab({ tab = i }) {
                    Icon(t.icon, if (tab == i) Aura.Periwinkle else Aura.Muted, Modifier.size(24.dp))
                    BasicText(t.label, style = ts(11f, if (tab == i) Aura.Text else Aura.Muted, FontWeight.Bold))
                }
            }
        }

        if (tab == 0 && editing == NONE) {
            LiquidButton(
                { editing = NEW }, backdrop,
                Modifier.align(Alignment.BottomEnd).navigationBarsPadding().padding(end = 20.dp, bottom = 100.dp),
                tint = Aura.Periwinkle,
            ) {
                Icon(Ic.Plus, Aura.Ink, Modifier.size(22.dp))
                BasicText("New alarm", style = ts(16f, Aura.Ink, FontWeight.ExtraBold))
            }
        }

        if (editing != NONE) {
            val idx = editing
            val start = if (idx == NEW) Alarm() else alarms.getOrNull(idx)
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
    }
}

@Composable
private fun NextUp(title: String, pad: PaddingValues) {
    Box(Modifier.fillMaxSize().padding(pad), contentAlignment = Alignment.Center) {
        Column(Modifier.glassCard().padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            BasicText(title, style = ts(22f, weight = FontWeight.ExtraBold))
            BasicText("This screen lands in the next build.", style = ts(14f, Aura.Muted))
        }
    }
}

/** The 7 protection checks with a one-tap fix for each, plus the repair buttons. The rest of Setup comes next. */
@Composable
private fun SetupTab(checks: List<Check>, pad: PaddingValues, onFix: (Int) -> Unit, onRunFix: (String) -> Unit) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = pad, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Column(Modifier.padding(start = 4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                BasicText("Setup", style = ts(26f, weight = FontWeight.ExtraBold))
                BasicText("${checks.count { it.ok }} of ${checks.size} protections on", style = ts(14f, Aura.Muted))
            }
        }
        items(checks.withIndex().toList(), key = { it.index }) { iv ->
            Row(Modifier.fillMaxWidth().glassCard(RoundedCornerShape(24.dp)).padding(horizontal = 20.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    BasicText(iv.value.title, style = ts(16f, weight = FontWeight.Bold))
                    BasicText(iv.value.hint, style = ts(13f, Aura.Muted))
                }
                if (iv.value.ok) BasicText("On", style = ts(15f, Aura.Mint, FontWeight.Bold))
                else Box(Modifier.pressable { onFix(iv.index) }.padding(8.dp)) { BasicText("Fix", style = ts(15f, Aura.Peach, FontWeight.ExtraBold)) }
            }
        }
        item { BasicText("Fixes", Modifier.padding(start = 4.dp, top = 8.dp), style = ts(13f, Aura.Muted, FontWeight.Bold)) }
        items(Perms.FIXES, key = { it.id }) { f ->
            Row(Modifier.fillMaxWidth().pressable { onRunFix(f.id) }.glassCard(RoundedCornerShape(24.dp)).padding(horizontal = 20.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    BasicText(f.title, style = ts(16f, weight = FontWeight.Bold))
                    BasicText(f.hint, style = ts(13f, Aura.Muted))
                }
            }
        }
    }
}
