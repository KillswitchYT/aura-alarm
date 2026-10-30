package com.killswitch.aura.ui

import androidx.compose.foundation.Canvas
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.killswitch.aura.Alarm
import com.killswitch.aura.Scheduler
import com.killswitch.aura.ampm
import com.killswitch.aura.fmtTime
import com.kyant.backdrop.backdrops.rememberCanvasBackdrop
import com.killswitch.aura.glass.LiquidToggle
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

fun countdown(ms: Long): String {
    val mins = (ms / 60_000).coerceAtLeast(0)
    val d = mins / 1440
    val h = mins % 1440 / 60
    val m = mins % 60
    return when { d > 0 -> "${d}d ${h}h"; h > 0 -> "${h}h ${m}m"; m > 0 -> "${m}m"; else -> "under a minute" }
}

@Composable
fun HomeTab(
    alarms: List<Alarm>, now: Long, is24: Boolean, protectionOk: Int, protectionTotal: Int, padding: PaddingValues,
    onProtection: () -> Unit, onToggle: (Int, Boolean) -> Unit, onOpen: (Int) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val q = query.trim().lowercase()
    val shown = alarms.withIndex().filter {
        q.isEmpty() || (it.value.time + " " + it.value.label + " " + it.value.daysSummary()).lowercase().contains(q)
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = padding, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { BasicText("Aura Alarm", Modifier.padding(start = 4.dp), style = ts(26f, weight = FontWeight.ExtraBold)) }
        item { Hero(now, is24, Scheduler.nextOf(alarms, now)) }
        if (protectionOk < protectionTotal) item { Protection(protectionOk, protectionTotal, onProtection) }
        item { Search(query) { query = it } }
        if (shown.isEmpty()) item {
            Box(Modifier.fillMaxWidth().glassCard().padding(24.dp)) {
                BasicText(if (alarms.isEmpty()) "No alarms yet. Tap New alarm to add one." else "Nothing matches your search.", style = ts(15f, Aura.Muted))
            }
        }
        items(shown, key = { it.index }) { iv ->
            AlarmRow(iv.value, now, is24, { on -> onToggle(iv.index, on) }, { onOpen(iv.index) })
        }
    }
}

@Composable
private fun Hero(now: Long, is24: Boolean, next: Long) {
    val cal = Calendar.getInstance().apply { timeInMillis = now }
    val h = cal.get(Calendar.HOUR_OF_DAY)
    val sec = cal.get(Calendar.SECOND)
    Box(Modifier.fillMaxWidth().glassCard(RoundedCornerShape(36.dp)).padding(24.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.size(236.dp), contentAlignment = Alignment.Center) {
                Canvas(Modifier.fillMaxSize()) {
                    val w = 8.dp.toPx()
                    val tl = Offset(w / 2, w / 2)
                    val sz = Size(size.width - w, size.height - w)
                    drawArc(Color.White.copy(alpha = .09f), 0f, 360f, false, topLeft = tl, size = sz, style = Stroke(w))
                    drawArc(
                        Brush.linearGradient(listOf(Aura.Periwinkle, Aura.Peach)), -90f, 360f * (sec + 1) / 60f, false,
                        topLeft = tl, size = sz, style = Stroke(w, cap = StrokeCap.Round),
                    )
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    BasicText(fmtTime(h, cal.get(Calendar.MINUTE), is24), style = ts(54f, weight = FontWeight.Bold, tracking = -2f))
                    BasicText(if (is24) ":%02d".format(sec) else ":%02d  %s".format(sec, ampm(h)), style = ts(16f, Aura.Muted, FontWeight.Bold))
                }
            }
            Spacer(Modifier.height(8.dp))
            BasicText(SimpleDateFormat("EEEE, MMMM d", Locale.getDefault()).format(Date(now)), style = ts(14f, Aura.Muted))
            Spacer(Modifier.height(10.dp))
            BasicText(
                if (next > 0) "Next alarm in ${countdown(next - now)}" else "No alarm set",
                style = ts(15f, if (next > 0) Aura.Peach else Aura.Muted, FontWeight.Bold),
            )
        }
    }
}

@Composable
private fun Protection(ok: Int, total: Int, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().pressable(onClick).glassCard(RoundedCornerShape(24.dp)).padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            BasicText("$ok of $total protections on", style = ts(16f, weight = FontWeight.Bold))
            BasicText("Tap to finish setup so Android cannot silence an alarm", style = ts(13f, Aura.Muted))
        }
        BasicText("\u203A", style = ts(28f, Aura.Peach, FontWeight.Bold))
    }
}

@Composable
private fun Search(value: String, onChange: (String) -> Unit) {
    Row(Modifier.fillMaxWidth().height(48.dp).glassCard(CircleShape).padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
        BasicTextField(
            value, onChange, Modifier.fillMaxWidth(), singleLine = true, textStyle = ts(15f),
            cursorBrush = SolidColor(Aura.Periwinkle),
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
                BasicText(fmtTime(a.hour, a.minute, is24), style = ts(36f, if (a.on) Aura.Text else Aura.Muted, FontWeight.Bold, -1f))
                if (!is24) BasicText(" " + ampm(a.hour), Modifier.padding(bottom = 6.dp), style = ts(14f, Aura.Muted, FontWeight.Bold))
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
