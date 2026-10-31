package com.killswitch.aura.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.killswitch.aura.Store
import com.killswitch.aura.TimerItem
import com.killswitch.aura.Timers
import com.kyant.shapes.Capsule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

private val PRESETS = listOf(60, 180, 300, 600, 900, 1800)

@Composable
fun TimersTab(rev: Int, now: Long, pad: PaddingValues, seen: MutableSet<Any>) {
    val ctx = LocalContext.current
    var list by remember { mutableStateOf<List<TimerItem>?>(null) } // null while loading
    var failed by remember { mutableStateOf(false) }
    var key by remember { mutableIntStateOf(0) }
    LaunchedEffect(rev, key) {
        val r = withContext(Dispatchers.IO) {
            try { Timers.settleExpired(ctx) } catch (e: Exception) { }
            Timers.tryLoad(ctx)
        }
        failed = r == null
        if (r != null) list = r
    }
    // A timer reached zero while this screen is open: its alarm resets it in storage, so read it again.
    val finished = list.orEmpty().any { it.running && it.end <= now }
    LaunchedEffect(finished, now / 6000) { if (finished) { delay(1600); key++ } }

    fun update(l: List<TimerItem>) { list = l; Timers.save(ctx, l) }
    fun start(sec: Int, label: String) {
        val cur = list.orEmpty()
        if (sec > 0 && cur.size < 60) update(cur + TimerItem.create(sec, label.trim(), System.currentTimeMillis()))
    }

    var h by remember { mutableStateOf("") }
    var m by remember { mutableStateOf("") }
    var s by remember { mutableStateOf("") }
    var label by remember { mutableStateOf("") }
    var hint by remember { mutableStateOf(false) }
    LaunchedEffect(hint) { if (hint) { delay(2400); hint = false } }

    fun startFromFields() {
        val sec = (h.toIntOrNull() ?: 0) * 3600 + (m.toIntOrNull() ?: 0) * 60 + (s.toIntOrNull() ?: 0)
        if (sec <= 0) { hint = true; return }
        start(sec, label); h = ""; m = ""; s = ""; label = ""
    }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = pad, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item(key = "head") { PageTitle("Timers", "Run as many as you like") }
        item(key = "new") {
            Box(Modifier.staggerIn(0, seen.add("t-new"))) {
                Column(Modifier.fillMaxWidth().glassCard().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Field(h, { h = it.filter(Char::isDigit).take(2) }, "h", Modifier.weight(1f), number = true, center = true)
                        Field(m, { m = it.filter(Char::isDigit).take(3) }, "min", Modifier.weight(1f), number = true, center = true)
                        Field(s, { s = it.filter(Char::isDigit).take(2) }, "s", Modifier.weight(1f), number = true, center = true)
                    }
                    Field(label, { label = it.take(20) }, "Label (optional)", onDone = { startFromFields() })
                    BigButton("Start", { startFromFields() }, Modifier.fillMaxWidth(), accent = true)
                    if (hint) BasicText("Enter a time or tap a preset", style = ts(13f, Aura.Rose, FontWeight.Bold))
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PRESETS.forEach { p -> ChipButton("${p / 60} min") { start(p, "") } }
                    }
                }
            }
        }
        val l = list
        when {
            failed -> item(key = "fail") {
                FailCard("Couldn't read your timers", "The saved data looks damaged. Nothing has been changed.",
                    { key++ }, { Store.set(ctx, "tm", "[]"); Timers.scheduleAll(ctx); key++ })
            }
            l == null -> items(2, key = { "skeleton$it" }) { SkeletonCard(2, 24) }
            l.isEmpty() -> item(key = "empty") { EmptyState(Ic.Timer, "No timers running", "Tap a preset or set a time above.") }
            else -> items(l, key = { it.id }) { t ->
                Box(Modifier.staggerIn(1, seen.add("t-${t.id}"))) {
                    TimerCard(
                        t, now,
                        onDelete = { update(l.filter { it.id != t.id }) },
                        onAdd = { update(l.map { if (it.id == t.id) it.addMinute(System.currentTimeMillis()) else it }) },
                        onReset = { update(l.map { if (it.id == t.id) it.reset() else it }) },
                        onToggle = { update(l.map { if (it.id == t.id) it.toggle(System.currentTimeMillis()) else it }) },
                    )
                }
            }
        }
    }
}

@Composable
private fun TimerCard(t: TimerItem, now: Long, onDelete: () -> Unit, onAdd: () -> Unit, onReset: () -> Unit, onToggle: () -> Unit) {
    val left = t.remaining(now)
    val p by animateFloatAsState(t.progress(now), tween(900, easing = LinearEasing), label = "timerProgress")
    Column(Modifier.fillMaxWidth().glassCard().padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            BasicText(t.label.ifEmpty { "Timer" }, Modifier.weight(1f), style = ts(16f, Aura.Muted, FontWeight.Bold))
            CloseButton(onDelete)
        }
        BasicText(TimerItem.fmt(left), Modifier.fillMaxWidth(), style = centered(ts(54f, if (t.running) Aura.Text else Aura.Muted, FontWeight.Bold, -2f)))
        Box(Modifier.fillMaxWidth().height(8.dp).clip(Capsule()).background(Color.White.copy(alpha = .12f))) {
            Box(Modifier.fillMaxWidth(p).height(8.dp).clip(Capsule()).background(if (t.running) Aura.Accent else Aura.Muted))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            SoftButton("+1:00", onAdd)
            SoftButton("Reset", onReset)
            Box(Modifier.weight(1f))
            AccentPill(if (t.running) "Pause" else if (t.state == "pause") "Resume" else "Start", onToggle)
        }
    }
}
