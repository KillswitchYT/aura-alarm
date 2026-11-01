package com.killswitch.aura.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.killswitch.aura.Alarm
import com.killswitch.aura.Scheduler
import com.killswitch.aura.ampm
import com.killswitch.aura.fmtTime
import com.killswitch.aura.glass.LiquidToggle
import com.kyant.backdrop.backdrops.rememberCanvasBackdrop
import com.kyant.shapes.Capsule
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** What the alarm list can be showing. Loading and Failed are real states, not placeholders. */
sealed interface AlarmsState {
    data object Loading : AlarmsState
    data class Ready(val list: List<Alarm>) : AlarmsState
    data object Failed : AlarmsState
}

fun countdown(ms: Long): String {
    val mins = (ms / 60_000).coerceAtLeast(0)
    val d = mins / 1440
    val h = mins % 1440 / 60
    val m = mins % 60
    return when { d > 0 -> "${d}d ${h}h"; h > 0 -> "${h}h ${m}m"; m > 0 -> "${m}m"; else -> "under a minute" }
}

@Composable
fun HomeTab(
    state: AlarmsState, now: Long, is24: Boolean, protectionOk: Int, protectionTotal: Int, padding: PaddingValues,
    seen: MutableSet<Any>, onProtection: () -> Unit, onToggle: (Int, Boolean) -> Unit, onOpen: (Int) -> Unit,
    onAdd: () -> Unit, onRetry: () -> Unit, onReset: () -> Unit, notice: String? = null, onNotice: () -> Unit = {},
) {
    var query by remember { mutableStateOf("") }
    val alarms = (state as? AlarmsState.Ready)?.list.orEmpty()
    val q = query.trim().lowercase()
    val shown = alarms.withIndex().filter {
        q.isEmpty() || (it.value.time + " " + it.value.label + " " + it.value.daysSummary()).lowercase().contains(q)
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = padding, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item(key = "title") { BasicText("Aura Alarm", Modifier.padding(start = 4.dp), style = ts(16f, Aura.Muted, FontWeight.Bold)) }
        item(key = "hero") { Box(Modifier.staggerIn(0, seen.add("hero"))) { Hero(now, is24, Scheduler.nextOf(alarms, now)) } }
        if (notice != null) item(key = "notice") { Notice(notice, onNotice) }
        if (protectionOk < protectionTotal) item(key = "prot") {
            Box(Modifier.staggerIn(1, seen.add("prot"))) { Protection(protectionOk, protectionTotal, onProtection) }
        }
        when (state) {
            AlarmsState.Loading -> items(3, key = { "skeleton$it" }) { SkeletonRow() }
            AlarmsState.Failed -> item(key = "error") { ErrorCard(onRetry, onReset) }
            is AlarmsState.Ready -> {
                if (alarms.isEmpty()) {
                    item(key = "empty") { EmptyCard(onAdd) }
                } else {
                    item(key = "search") { Box(Modifier.staggerIn(2, seen.add("search"))) { Search(query) { query = it } } }
                    if (shown.isEmpty()) item(key = "nomatch") { NoMatch(query) }
                    items(shown, key = { it.index }) { iv ->
                        Box(Modifier.staggerIn(3 + iv.index, seen.add("row${iv.index}"))) {
                            AlarmRow(iv.value, now, is24, { on -> onToggle(iv.index, on) }, { onOpen(iv.index) })
                        }
                    }
                }
            }
        }
    }
}

/** The one big number on the screen. The ring is driven by the frame clock, so it sweeps instead of ticking. */
@Composable
private fun Hero(now: Long, is24: Boolean, next: Long) {
    val cal = Calendar.getInstance().apply { timeInMillis = now }
    val h = cal.get(Calendar.HOUR_OF_DAY)
    var frameMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
    // 30 fps is plenty for an arc that moves 6 degrees a second, and it halves the work the glass has to redo behind it.
    LaunchedEffect(Unit) { var last = 0L; while (true) withFrameNanos { ns -> if (ns - last >= 33_000_000L) { last = ns; frameMs = System.currentTimeMillis() } } }

    Box(Modifier.fillMaxWidth().glassCard(RoundedCornerShape(36.dp)).padding(24.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.size(250.dp), contentAlignment = Alignment.Center) {
                Canvas(Modifier.fillMaxSize()) {
                    val w = 7.dp.toPx()
                    val tl = Offset(w / 2, w / 2)
                    val sz = Size(size.width - w, size.height - w)
                    // Even minutes the arc grows from the top, odd minutes it shrinks from the tail, so it never snaps back.
                    val ms = frameMs
                    val f = (ms % 60_000L) / 60_000f
                    val odd = (ms / 60_000L) % 2L == 1L
                    val start = if (odd) -90f + 360f * f else -90f
                    val sweep = if (odd) 360f * (1f - f) else 360f * f
                    drawArc(Color.White.copy(alpha = .08f), 0f, 360f, false, topLeft = tl, size = sz, style = Stroke(w))
                    drawArc(Aura.Accent.copy(alpha = .20f), start, sweep, false, topLeft = tl, size = sz, style = Stroke(w * 2.4f, cap = StrokeCap.Round))
                    drawArc(Aura.Accent, start, sweep, false, topLeft = tl, size = sz, style = Stroke(w, cap = StrokeCap.Round))
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    BasicText(fmtTime(h, cal.get(Calendar.MINUTE), is24), style = ts(66f, weight = FontWeight.Bold, tracking = -2.5f))
                    BasicText(
                        if (is24) ":%02d".format(cal.get(Calendar.SECOND)) else ":%02d  %s".format(cal.get(Calendar.SECOND), ampm(h)),
                        style = ts(14f, Aura.Muted, FontWeight.Bold),
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            BasicText(SimpleDateFormat("EEEE, MMMM d", Locale.getDefault()).format(Date(now)), style = ts(13f, Aura.Muted))
            Spacer(Modifier.height(6.dp))
            BasicText(
                if (next > 0) "Next alarm in ${countdown(next - now)}" else "No alarm set",
                style = ts(14f, if (next > 0) Aura.Accent else Aura.Muted, FontWeight.Bold),
            )
        }
    }
}

/** Something the person should know about: an alarm that needed its backup, or Android refusing to arm one. */
@Composable
private fun Notice(text: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().pressable(onClick).glassCard(RoundedCornerShape(24.dp)).padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Ic.Alert, Aura.Rose, Modifier.size(22.dp))
        Spacer(Modifier.size(12.dp))
        BasicText(text, Modifier.weight(1f), style = ts(14f, Aura.Text, FontWeight.Bold))
    }
}

@Composable
private fun Protection(ok: Int, total: Int, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().pressable(onClick).glassCard(RoundedCornerShape(24.dp)).padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            BasicText("$ok of $total protections on", style = ts(15f, weight = FontWeight.Bold))
            BasicText("Finish setup so Android cannot silence an alarm", style = ts(13f, Aura.Muted))
        }
        Icon(Ic.Chevron, Aura.Muted, Modifier.size(22.dp))
    }
}

@Composable
private fun Search(value: String, onChange: (String) -> Unit) {
    Row(Modifier.fillMaxWidth().height(48.dp).glassCard(CircleShape).padding(horizontal = 18.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(Ic.Search, Aura.Muted, Modifier.size(20.dp))
        Spacer(Modifier.size(10.dp))
        BasicTextField(
            value, onChange, Modifier.fillMaxWidth(), singleLine = true, textStyle = ts(15f),
            cursorBrush = SolidColor(Aura.Accent),
            decorationBox = { inner ->
                if (value.isEmpty()) BasicText("Search alarms", style = ts(15f, Aura.Muted))
                inner()
            },
        )
    }
}

@Composable
private fun AlarmRow(a: Alarm, now: Long, is24: Boolean, onToggle: (Boolean) -> Unit, onOpen: () -> Unit) {
    val flat = rememberCanvasBackdrop { drawRect(Aura.Card) }
    Row(
        Modifier.fillMaxWidth().pressable(onOpen).glassCard().padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.Bottom) {
                BasicText(fmtTime(a.hour, a.minute, is24), style = ts(32f, if (a.on) Aura.Text else Aura.Muted, FontWeight.Bold, -1f))
                if (!is24) BasicText(" " + ampm(a.hour), Modifier.padding(bottom = 5.dp), style = ts(13f, Aura.Muted, FontWeight.Bold))
            }
            val challenge = when (a.challenge) { 1 -> "Math"; 2 -> "Shake"; else -> null }
            BasicText(
                listOfNotNull(a.label.ifEmpty { null }, a.daysSummary(), challenge, if (a.skip > now) "skipping next" else null).joinToString("  \u00B7  "),
                style = ts(13f, Aura.Muted),
            )
        }
        LiquidToggle(selected = { a.on }, onSelect = onToggle, backdrop = flat)
    }
}

/** Loading: placeholder rows that breathe while the alarms are read from storage. */
@Composable
private fun SkeletonRow() {
    val t = rememberInfiniteTransition(label = "skeleton")
    val a by t.animateFloat(.35f, .85f, infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Reverse), label = "a")
    Row(Modifier.fillMaxWidth().glassCard().padding(horizontal = 20.dp, vertical = 20.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).graphicsLayer { alpha = a }, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.size(width = 120.dp, height = 28.dp).clip(RoundedCornerShape(8.dp)).background(Color.White.copy(alpha = .14f)))
            Box(Modifier.size(width = 180.dp, height = 12.dp).clip(RoundedCornerShape(6.dp)).background(Color.White.copy(alpha = .10f)))
        }
        Box(Modifier.size(width = 56.dp, height = 30.dp).graphicsLayer { alpha = a }.clip(Capsule()).background(Color.White.copy(alpha = .14f)))
    }
}

/** Empty: says what to do and gives the button right there. */
@Composable
private fun EmptyCard(onAdd: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().glassCard().padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(Ic.Alarm, Aura.Muted, Modifier.size(40.dp))
        BasicText("No alarms yet", style = ts(18f, weight = FontWeight.Bold))
        BasicText("Set one and it rings even when your phone is asleep.", style = centered(ts(14f, Aura.Muted)))
        Spacer(Modifier.height(4.dp))
        AccentPill("Add your first alarm", onAdd)
    }
}

@Composable
private fun NoMatch(query: String) {
    Column(
        Modifier.fillMaxWidth().glassCard().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(Ic.Search, Aura.Muted, Modifier.size(32.dp))
        BasicText("Nothing matches \u201C${query.trim()}\u201D", style = centered(ts(15f, Aura.Muted, FontWeight.Bold)))
    }
}

/** Error: the saved data could not be read. Nothing is changed unless you choose Reset, and Reset asks twice. */
@Composable
private fun ErrorCard(onRetry: () -> Unit, onReset: () -> Unit) {
    var confirm by remember { mutableStateOf(false) }
    Column(
        Modifier.fillMaxWidth().glassCard().padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(Ic.Alert, Aura.Rose, Modifier.size(38.dp))
        BasicText("Couldn't read your alarms", style = ts(18f, weight = FontWeight.Bold))
        BasicText("The saved data looks damaged. Nothing has been changed.", style = centered(ts(14f, Aura.Muted)))
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            AccentPill("Try again", onRetry)
            Box(Modifier.pressable { if (confirm) onReset() else confirm = true }.padding(8.dp)) {
                BasicText(if (confirm) "Tap again to erase all" else "Reset", style = ts(15f, Aura.Rose, FontWeight.Bold))
            }
        }
    }
}
