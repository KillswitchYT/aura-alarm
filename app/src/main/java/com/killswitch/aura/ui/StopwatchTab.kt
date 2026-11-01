package com.killswitch.aura.ui

import android.content.ClipData
import android.content.ClipboardManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicText
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.killswitch.aura.Stopwatch
import com.killswitch.aura.StopwatchStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

@Composable
fun StopwatchTab(rev: Int, pad: PaddingValues, seen: MutableSet<Any>) {
    val ctx = LocalContext.current
    var sw by remember { mutableStateOf<Stopwatch?>(null) } // null while loading
    var failed by remember { mutableStateOf(false) }
    var key by remember { mutableIntStateOf(0) }
    var copied by remember { mutableStateOf(false) }
    var frame by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(rev, key) {
        val r = withContext(Dispatchers.IO) { StopwatchStore.load(ctx) }
        failed = r == null
        if (r != null) sw = r
    }
    val running = sw?.running == true
    // Frame-driven only while it is running and this tab is on screen, so the hundredths sweep instead of stepping.
    LaunchedEffect(running) { if (running) while (true) withFrameNanos { frame = System.currentTimeMillis() } }
    LaunchedEffect(copied) { if (copied) { delay(1600); copied = false } }

    fun apply(n: Stopwatch) { sw = n; StopwatchStore.save(ctx, n) }
    val s = sw

    LazyColumn(Modifier.fillMaxSize(), contentPadding = pad, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item(key = "head") { PageTitle("Stopwatch", "Laps are saved if you leave the app") }
        when {
            failed -> item(key = "fail") {
                FailCard("Couldn't read the stopwatch", "The saved data looks damaged. Nothing has been changed.",
                    { key++ }, { StopwatchStore.clear(ctx); key++ })
            }
            s == null -> item(key = "skeleton") { SkeletonCard(1, 40) }
            else -> {
                item(key = "clock") {
                    Column(
                        Modifier.fillMaxWidth().staggerIn(0, seen.add("sw-clock")).glassCard().padding(horizontal = 20.dp, vertical = 28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(22.dp),
                    ) {
                        BasicText(
                            Stopwatch.fmt(s.elapsed(if (running) frame else System.currentTimeMillis())),
                            style = centered(ts(56f, if (running) Aura.Text else Aura.Muted, FontWeight.Bold, -2f)),
                        )
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            BigButton(s.secondaryLabel(), { apply(s.lapOrReset(System.currentTimeMillis())) }, Modifier.weight(1f), enabled = s.running || s.acc > 0)
                            BigButton(s.primaryLabel, { apply(s.toggle(System.currentTimeMillis())) }, Modifier.weight(1f), accent = true)
                        }
                    }
                }
                item(key = "laps-head") {
                    Row(Modifier.fillMaxWidth().padding(start = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        BasicText("Laps", Modifier.weight(1f), style = ts(18f, weight = FontWeight.ExtraBold))
                        if (s.laps.isNotEmpty()) SoftButton(if (copied) "Copied" else "Copy", {
                            ctx.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("Aura laps", s.copyText()))
                            copied = true
                        })
                    }
                }
                if (s.laps.isEmpty()) item(key = "no-laps") {
                    EmptyState(Ic.Stopwatch, "No laps yet", if (s.running) "Tap Lap to mark one." else "Start it, then tap Lap to mark each round.")
                } else {
                    val splits = s.splits()
                    items(s.laps.indices.reversed().toList(), key = { it }) { i ->
                        val mark = s.mark(i)
                        Row(
                            Modifier.animateItem().fillMaxWidth().glassCard().padding(horizontal = 20.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                BasicText("Lap ${i + 1}", style = ts(15f, Aura.Muted, FontWeight.Bold))
                                if (mark != 0) BasicText(if (mark == 1) "Best" else "Worst", style = ts(12f, if (mark == 1) Aura.Mint else Aura.Rose, FontWeight.Bold))
                            }
                            BasicText(Stopwatch.fmt(splits[i]), style = ts(22f, if (mark == 1) Aura.Mint else if (mark == 2) Aura.Rose else Aura.Text, FontWeight.Bold))
                            BasicText(Stopwatch.fmt(s.laps[i]), Modifier.padding(start = 14.dp), style = ts(13f, Aura.Muted))
                        }
                    }
                }
            }
        }
    }
}
