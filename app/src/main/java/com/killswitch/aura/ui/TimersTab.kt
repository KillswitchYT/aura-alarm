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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.killswitch.aura.Store
import com.killswitch.aura.TimerEntry
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
    var digits by remember { mutableStateOf("") }
    var label by remember { mutableStateOf("") }
    var open by remember { mutableStateOf<Boolean?>(null) } // null: open while there are no timers, closed once there are

    fun start(sec: Int, name: String) {
        val cur = list.orEmpty()
        if (sec > 0 && cur.size < 60) update(cur + TimerItem.create(sec, name.trim(), System.currentTimeMillis()))
        digits = ""; label = ""; open = false
    }

    val l = list
    val entryOpen = open ?: (l != null && l.isEmpty())

    LazyColumn(Modifier.fillMaxSize(), contentPadding = pad) {
        item(key = "head") { PageTitle("Timers", "Type a time, or tap a preset") }

        if (!failed && l != null) item(key = "entry") {
            Column(Modifier.fillMaxWidth().staggerIn(0, seen.add("t-entry")).padding(top = 14.dp, bottom = 6.dp)) {
                if (entryOpen) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.weight(1f)) { EntryDisplay(digits) }
                        if (digits.isNotEmpty()) TextAction("Clear", { digits = "" })
                    }
                    LineField(label, { label = it.take(20) }, "Label (optional)")
                    Spacer(Modifier.height(4.dp))
                    Numpad({ digits = TimerEntry.push(digits, it) }, { digits = TimerEntry.pop(digits) })
                    Spacer(Modifier.height(6.dp))
                    BigButton("Start", { start(TimerEntry.seconds(digits), label) }, Modifier.fillMaxWidth(), accent = true, enabled = digits.isNotEmpty())
                    Spacer(Modifier.height(14.dp))
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PRESETS.forEach { p -> ChipButton("${p / 60} min") { start(p, label) } }
                    }
                    if (l.isNotEmpty()) TextAction("Cancel", { open = false; digits = "" }, modifier = Modifier.padding(top = 6.dp))
                } else {
                    BigButton("New timer", { open = true }, Modifier.fillMaxWidth(), accent = true)
                }
            }
        }

        when {
            failed -> item(key = "fail") {
                FailCard("Couldn't read your timers", "The saved data looks damaged. Nothing has been changed.",
                    { key++ }, { Store.set(ctx, "tm", "[]"); Timers.scheduleAll(ctx); key++ })
            }
            l == null -> items(2, key = { "skeleton$it" }) { SkeletonCard(2, 24) }
            else -> items(l, key = { it.id }) { t ->
                Column(Modifier.animateItem().staggerIn(1, seen.add("t-${t.id}"))) {
                    Box(Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = .07f)))
                    TimerRow(
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

/** 00h 05m 00s. The parts you have typed are bright, the rest wait dim. */
@Composable
private fun EntryDisplay(digits: String) {
    val (h, m, s) = TimerEntry.parts(digits)
    val n = digits.length
    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Segment("%02d".format(h), "h", n > 4)
        Segment("%02d".format(m), "m", n > 2)
        Segment("%02d".format(s), "s", n > 0)
    }
}

@Composable
private fun Segment(value: String, unit: String, typed: Boolean) {
    val a by selectAnim(typed)
    val c = lerp(Aura.Muted.copy(alpha = .5f), Aura.Text, a)
    Row(verticalAlignment = Alignment.Bottom) {
        BasicText(value, style = ts(46f, c, FontWeight.Bold, -1.5f))
        BasicText(unit, Modifier.padding(start = 2.dp, bottom = 8.dp), style = ts(17f, c, FontWeight.Bold))
    }
}

@Composable
private fun Numpad(onDigit: (String) -> Unit, onBack: () -> Unit) {
    val rows = listOf(listOf("1", "2", "3"), listOf("4", "5", "6"), listOf("7", "8", "9"), listOf("00", "0", "<"))
    Column(Modifier.fillMaxWidth()) {
        rows.forEach { r ->
            Row(Modifier.fillMaxWidth()) {
                r.forEach { k ->
                    Box(Modifier.weight(1f).height(60.dp).pressable { if (k == "<") onBack() else onDigit(k) }, contentAlignment = Alignment.Center) {
                        if (k == "<") Icon(Ic.Backspace, Aura.Text, Modifier.size(28.dp))
                        else BasicText(k, style = ts(30f, weight = FontWeight.Medium))
                    }
                }
            }
        }
    }
}

/** Label and the small actions on top, the countdown big on the left, one round button on the right, a thin line for progress. */
@Composable
private fun TimerRow(t: TimerItem, now: Long, onDelete: () -> Unit, onAdd: () -> Unit, onReset: () -> Unit, onToggle: () -> Unit) {
    val p by animateFloatAsState(t.progress(now), tween(900, easing = LinearEasing), label = "timerProgress")
    Column(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 16.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            BasicText(t.label.ifEmpty { "Timer" }, Modifier.weight(1f).padding(start = 4.dp), maxLines = 1, style = ts(15f, Aura.Muted, FontWeight.Bold))
            TextAction("+1:00", onAdd)
            TextAction("Reset", onReset)
            TextAction("Delete", onDelete, Aura.Rose)
        }
        Row(Modifier.fillMaxWidth().padding(start = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            BasicText(
                TimerItem.fmt(t.remaining(now)), Modifier.weight(1f),
                style = ts(52f, if (t.running) Aura.Text else Aura.Muted, FontWeight.Bold, -2f),
            )
            Box(Modifier.size(60.dp).pressable(onToggle).clip(CircleShape).background(Aura.Accent), contentAlignment = Alignment.Center) {
                Icon(if (t.running) Ic.Pause else Ic.Play, Aura.OnAccent, Modifier.size(26.dp))
            }
        }
        Spacer(Modifier.height(10.dp))
        Box(Modifier.fillMaxWidth().padding(horizontal = 4.dp).height(3.dp).clip(Capsule()).background(Color.White.copy(alpha = .12f))) {
            Box(Modifier.fillMaxWidth(p).height(3.dp).clip(Capsule()).background(if (t.running) Aura.Accent else Aura.Muted))
        }
    }
}
