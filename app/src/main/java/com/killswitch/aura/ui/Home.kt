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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.graphics.lerp
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

private const val HERO_DP = 190

/** Filter chips and search only appear once the list is long enough to need them. */
private const val FILTER_FROM = 3
private const val SEARCH_FROM = 5

@Composable
fun HomeTab(
    state: AlarmsState, now: Long, is24: Boolean, protectionOk: Int, protectionTotal: Int, padding: PaddingValues,
    seen: MutableSet<Any>, onProtection: () -> Unit, onToggle: (Int, Boolean) -> Unit, onOpen: (Int) -> Unit,
    onAdd: () -> Unit, onRetry: () -> Unit, onReset: () -> Unit, notice: String? = null, onNotice: () -> Unit = {},
) {
    var query by remember { mutableStateOf("") }
    var filter by remember { mutableIntStateOf(0) } // 0 all, 1 on, 2 off
    val listState = rememberLazyListState()
    val alarms = (state as? AlarmsState.Ready)?.list.orEmpty()
    val q = if (alarms.size >= SEARCH_FROM) query.trim().lowercase() else ""
    val f = if (alarms.size >= FILTER_FROM) filter else 0
    val shown = alarms.withIndex().filter {
        (f == 0 || (f == 1) == it.value.on) &&
            (q.isEmpty() || (it.value.time + " " + it.value.label + " " + it.value.daysSummary()).lowercase().contains(q))
    }
    val nextMs = Scheduler.nextOf(alarms, now)
    val nextAlarm = if (nextMs > 0) alarms.firstOrNull { it.on && it.next(now) == nextMs } else null

    LazyColumn(Modifier.fillMaxSize(), state = listState, contentPadding = padding) {
        item(key = "hero") {
            // The header does not shrink into a bar, it just lets go: it fades and sinks a little as the list scrolls over it.
            Box(
                Modifier.staggerIn(0, seen.add("hero")).graphicsLayer {
                    val full = HERO_DP.dp.toPx()
                    val off = if (listState.firstVisibleItemIndex == 0) listState.firstVisibleItemScrollOffset.toFloat() else full
                    val t = (off / full).coerceIn(0f, 1f)
                    alpha = 1f - t; scaleX = 1f - .10f * t; scaleY = scaleX; translationY = off * .35f
                },
            ) { Hero(now, is24, nextMs, nextAlarm) }
        }
        if (notice != null) item(key = "notice") { Notice(notice, onNotice) }
        if (protectionOk < protectionTotal) item(key = "prot") {
            Box(Modifier.staggerIn(1, seen.add("prot"))) { Protection(protectionOk, protectionTotal, onProtection) }
        }
        when (state) {
            AlarmsState.Loading -> items(3, key = { "skeleton$it" }) { SkeletonRow() }
            AlarmsState.Failed -> item(key = "error") {
                FailCard("Couldn't read your alarms", "The saved data looks damaged. Nothing has been changed.", onRetry, onReset)
            }
            is AlarmsState.Ready -> {
                if (alarms.isEmpty()) {
                    item(key = "empty") { EmptyState(Ic.Alarm, "No alarms yet", "Set one and it rings even when your phone is asleep.", "Add your first alarm", onAdd) }
                } else {
                    item(key = "list-head") {
                        Row(Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            BasicText("Alarms", Modifier.weight(1f).padding(start = 4.dp), style = ts(20f, weight = FontWeight.ExtraBold))
                            if (alarms.size >= FILTER_FROM) Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                ChipButton("All", f == 0) { filter = 0 }
                                ChipButton("On", f == 1) { filter = 1 }
                                ChipButton("Off", f == 2) { filter = 2 }
                            }
                        }
                    }
                    if (alarms.size >= SEARCH_FROM) item(key = "search") { Search(query) { query = it } }
                    if (shown.isEmpty()) item(key = "nomatch") {
                        NoResults(if (q.isNotEmpty()) "Nothing matches \u201C${query.trim()}\u201D" else "No alarms in this filter")
                    }
                    itemsIndexed(shown, key = { _, iv -> iv.index }) { pos, iv ->
                        Column(Modifier.animateItem().staggerIn(minOf(pos, 8) + 2, seen.add("row${iv.index}"))) {
                            if (pos > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = .07f)))
                            AlarmRow(
                                iv.value, now, is24, isNext = nextMs > 0 && iv.value.on && iv.value.next(now) == nextMs,
                                onToggle = { on -> onToggle(iv.index, on) }, onOpen = { onOpen(iv.index) },
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * The clock, and the one thing you came for: when the next alarm rings. No box around it, it sits on the scene.
 * The ring is driven by the frame clock, so it sweeps instead of ticking.
 */
@Composable
private fun Hero(now: Long, is24: Boolean, nextMs: Long, next: Alarm?) {
    val cal = Calendar.getInstance().apply { timeInMillis = now }
    val h = cal.get(Calendar.HOUR_OF_DAY)
    var frameMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
    // 30 fps is plenty for an arc that moves 6 degrees a second, and it halves the work the glass has to redo behind it.
    LaunchedEffect(Unit) { var last = 0L; while (true) withFrameNanos { ns -> if (ns - last >= 33_000_000L) { last = ns; frameMs = System.currentTimeMillis() } } }

    Row(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 22.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(152.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                val w = 5.dp.toPx()
                val tl = Offset(w / 2, w / 2)
                val sz = Size(size.width - w, size.height - w)
                // Even minutes the arc grows from the top, odd minutes it shrinks from the tail, so it never snaps back.
                val ms = frameMs
                val fr = (ms % 60_000L) / 60_000f
                val odd = (ms / 60_000L) % 2L == 1L
                val start = if (odd) -90f + 360f * fr else -90f
                val sweep = if (odd) 360f * (1f - fr) else 360f * fr
                drawArc(Color.White.copy(alpha = .08f), 0f, 360f, false, topLeft = tl, size = sz, style = Stroke(w))
                drawArc(Aura.Accent.copy(alpha = .20f), start, sweep, false, topLeft = tl, size = sz, style = Stroke(w * 2.4f, cap = StrokeCap.Round))
                drawArc(Aura.Accent, start, sweep, false, topLeft = tl, size = sz, style = Stroke(w, cap = StrokeCap.Round))
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                BasicText(fmtTime(h, cal.get(Calendar.MINUTE), is24), style = ts(40f, weight = FontWeight.Bold, tracking = -1.5f))
                BasicText(
                    if (is24) ":%02d".format(cal.get(Calendar.SECOND)) else ":%02d  %s".format(cal.get(Calendar.SECOND), ampm(h)),
                    style = ts(12f, Aura.Muted, FontWeight.Bold),
                )
            }
        }
        Spacer(Modifier.width(22.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            BasicText("Next alarm", style = ts(13f, Aura.Muted, FontWeight.Bold))
            if (nextMs > 0) {
                BasicText("in ${countdown(nextMs - now)}", maxLines = 1, style = ts(26f, Aura.Accent, FontWeight.ExtraBold, -1f))
                if (next != null) BasicText(
                    fmtTime(next.hour, next.minute, is24) + (if (is24) "" else " " + ampm(next.hour)) + (if (next.label.isNotEmpty()) "  \u00B7  " + next.label else ""),
                    maxLines = 1, style = ts(14f, Aura.Muted),
                )
            } else BasicText("None set", style = ts(24f, Aura.Muted, FontWeight.Bold))
            Spacer(Modifier.height(6.dp))
            BasicText(SimpleDateFormat("EEEE, MMM d", Locale.getDefault()).format(Date(now)), maxLines = 1, style = ts(13f, Aura.Muted))
        }
    }
}

/** Something the person should know about: an alarm that needed its backup, or Android refusing to arm one. */
@Composable
private fun Notice(text: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().pressable(onClick).padding(horizontal = 4.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(Ic.Alert, Aura.Rose, Modifier.size(22.dp))
        Spacer(Modifier.size(12.dp))
        BasicText(text, Modifier.weight(1f), style = ts(14f, Aura.Text, FontWeight.Bold))
    }
}

@Composable
private fun Protection(ok: Int, total: Int, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().pressable(onClick).padding(horizontal = 4.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(Ic.Alert, Aura.Rose, Modifier.size(22.dp))
        Spacer(Modifier.size(12.dp))
        Column(Modifier.weight(1f)) {
            BasicText("$ok of $total protections on", style = ts(15f, weight = FontWeight.Bold))
            BasicText("Finish setup so Android cannot silence an alarm", style = ts(13f, Aura.Muted))
        }
        Icon(Ic.Chevron, Aura.Muted, Modifier.size(22.dp))
    }
}

/** A plain line with an underline, not a capsule. Only shown once there are enough alarms to lose one. */
@Composable
private fun Search(value: String, onChange: (String) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(bottom = 6.dp)) {
        Row(Modifier.fillMaxWidth().height(44.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
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
        Box(Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = .12f)))
    }
}

/** Big time on the left, quiet details under it. The next alarm to ring is accent, others white, switched-off ones dim. */
@Composable
private fun AlarmRow(a: Alarm, now: Long, is24: Boolean, isNext: Boolean, onToggle: (Boolean) -> Unit, onOpen: () -> Unit) {
    val flat = rememberCanvasBackdrop { drawRect(Aura.Lift2) }
    val on by selectAnim(a.on)
    val nxt by selectAnim(isNext)
    val timeColor = lerp(lerp(Aura.Muted.copy(alpha = .55f), Aura.Text, on), Aura.Accent, nxt)
    Row(Modifier.fillMaxWidth().pressable(onOpen).padding(horizontal = 4.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.Bottom) {
                BasicText(fmtTime(a.hour, a.minute, is24), style = ts(44f, timeColor, FontWeight.Bold, -1.5f))
                if (!is24) BasicText(" " + ampm(a.hour), Modifier.padding(bottom = 7.dp), style = ts(14f, Aura.Muted, FontWeight.Bold))
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

/** Loading: placeholder rows that breathe while the alarms are read from storage. No boxes, just the shapes that are coming. */
@Composable
private fun SkeletonRow() {
    val t = rememberInfiniteTransition(label = "skeleton")
    val a by t.animateFloat(.35f, .85f, infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Reverse), label = "a")
    Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 18.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).graphicsLayer { alpha = a }, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.size(width = 130.dp, height = 34.dp).clip(RoundedCornerShape(8.dp)).background(Color.White.copy(alpha = .14f)))
            Box(Modifier.size(width = 180.dp, height = 12.dp).clip(RoundedCornerShape(6.dp)).background(Color.White.copy(alpha = .10f)))
        }
        Box(Modifier.size(width = 56.dp, height = 30.dp).graphicsLayer { alpha = a }.clip(Capsule()).background(Color.White.copy(alpha = .14f)))
    }
}
