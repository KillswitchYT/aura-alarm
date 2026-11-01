package com.killswitch.aura.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.text.format.DateFormat
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.killswitch.aura.AlarmReceiver
import com.killswitch.aura.Backup
import com.killswitch.aura.Bed
import com.killswitch.aura.Check
import com.killswitch.aura.Perms
import com.killswitch.aura.Scheduler
import com.killswitch.aura.SelfTest
import com.killswitch.aura.Settings
import com.killswitch.aura.Store
import com.killswitch.aura.ampm
import com.killswitch.aura.fmtTime
import com.killswitch.aura.glass.LiquidToggle
import com.kyant.backdrop.backdrops.rememberCanvasBackdrop
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Calendar

/** Fades and rises in like the other lists, once. */
@Composable
private fun Stag(i: Int, key: String, seen: MutableSet<Any>, content: @Composable () -> Unit) {
    Box(Modifier.staggerIn(i, seen.add("setup-$key"))) { content() }
}

@Composable
private fun Choice(title: String, sub: String?, selected: Boolean, onClick: () -> Unit) {
    val sel by selectAnim(selected)
    Row(
        Modifier.fillMaxWidth().pressable(onClick).glassCard(RoundedCornerShape(24.dp))
            .background(Color.White.copy(alpha = .08f * sel)).padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            BasicText(title, style = ts(16f, weight = FontWeight.Bold))
            if (sub != null) BasicText(sub, style = ts(13f, Aura.Muted))
        }
        Icon(Ic.Check, Aura.Text, Modifier.size(22.dp).graphicsLayer { alpha = sel; scaleX = .6f + .4f * sel; scaleY = .6f + .4f * sel })
    }
}

@Composable
private fun Panel(seen: MutableSet<Any>, key: String, content: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxWidth().staggerIn(0, seen.add("panel-$key")).glassCard(RoundedCornerShape(24.dp)).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) { content() }
}

private fun fmtLast(raw: String, is24: Boolean): String {
    val p = raw.split(":").mapNotNull { it.toIntOrNull() }
    if (p.size != 3) return raw
    return if (is24) raw else "%d:%02d:%02d %s".format(if (p[0] % 12 == 0) 12 else p[0] % 12, p[1], p[2], ampm(p[0]))
}

private fun fmtAt(ms: Long, is24: Boolean): String {
    val c = Calendar.getInstance().apply { timeInMillis = ms }
    val h = c.get(Calendar.HOUR_OF_DAY)
    return fmtTime(h, c.get(Calendar.MINUTE), is24) + (if (is24) "" else " " + ampm(h))
}

@Composable
fun SetupTab(
    checks: List<Check>, pad: PaddingValues, now: Long, is24: Boolean, rev: Int, seen: MutableSet<Any>,
    onFix: (Int) -> Unit, onRunFix: (String) -> Unit, onNight: () -> Unit, onChanged: () -> Unit,
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var bump by remember { mutableIntStateOf(0) }
    val ro = remember(rev, bump) { Settings.ringStyle(ctx) }
    val vbtn = remember(rev, bump) { Settings.volumeButtons(ctx) }
    val tfmt = remember(rev, bump) { Settings.timeFormat(ctx) }
    val bed = remember(rev, bump) { Settings.bed(ctx) }
    val lastFired = remember(rev, bump) { Store.get(ctx, "lastFired") }
    val nextT = remember(rev, bump) { Store.get(ctx, "nextT").toLongOrNull() ?: 0L }
    val missed = remember(rev, bump) { Store.get(ctx, "missed") == "1" }
    val flat = rememberCanvasBackdrop { drawRect(Aura.Card) }
    val on = checks.count { it.ok }
    val all = on == checks.size && checks.isNotEmpty()
    val tick by animateFloatAsState(if (all) 1f else 0f, tween(300), label = "allOn")

    var test by remember { mutableStateOf<List<Check>?>(null) }
    var testing by remember { mutableStateOf(false) }
    var backupText by remember { mutableStateOf("") }
    var copied by remember { mutableStateOf(false) }
    var restoreMsg by remember { mutableStateOf<String?>(null) }
    var confirmRestore by remember { mutableStateOf(false) }
    LaunchedEffect(copied) { if (copied) { delay(1600); copied = false } }
    LaunchedEffect(backupText) { confirmRestore = false; restoreMsg = null }
    val parsed = remember(backupText) { if (backupText.isBlank()) null else Backup.parse(backupText) }

    fun clip() = ctx.getSystemService(ClipboardManager::class.java)

    LazyColumn(Modifier.fillMaxSize(), contentPadding = pad, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item(key = "head") {
            Row(Modifier.fillMaxWidth().padding(start = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    BasicText("Setup", style = ts(26f, weight = FontWeight.ExtraBold))
                    BasicText("$on of ${checks.size} protections on", style = ts(14f, if (all) Aura.Mint else Aura.Muted))
                }
                Icon(Ic.Check, Aura.Mint, Modifier.size(28.dp).graphicsLayer { alpha = tick; scaleX = .6f + .4f * tick; scaleY = .6f + .4f * tick })
            }
        }
        items(checks.withIndex().toList(), key = { "check${it.index}" }) { iv ->
            Stag(iv.index.coerceAtMost(6), "check${iv.index}", seen) {
                Row(Modifier.fillMaxWidth().glassCard(RoundedCornerShape(24.dp)).padding(horizontal = 20.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        BasicText(iv.value.title, style = ts(16f, weight = FontWeight.Bold))
                        BasicText(iv.value.hint, style = ts(13f, Aura.Muted))
                    }
                    if (iv.value.ok) BasicText("On", style = ts(15f, Aura.Mint, FontWeight.Bold))
                    else Box(Modifier.pressable { onFix(iv.index) }.padding(8.dp)) { BasicText("Fix", style = ts(15f, Aura.Accent, FontWeight.ExtraBold)) }
                }
            }
        }

        item(key = "fixes-l") { SectionLabel("Fixes") }
        items(Perms.FIXES, key = { "fix-" + it.id }) { f ->
            Row(Modifier.fillMaxWidth().pressable { onRunFix(f.id) }.glassCard(RoundedCornerShape(24.dp)).padding(horizontal = 20.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    BasicText(f.title, style = ts(16f, weight = FontWeight.Bold))
                    BasicText(f.hint, style = ts(13f, Aura.Muted))
                }
            }
        }

        item(key = "test-l") { SectionLabel("Test it") }
        item(key = "test") {
            Panel(seen, "test") {
                BasicText("A real ring: sound, vibration and the lock-screen alarm. Snooze in a test lasts 10 seconds.", style = ts(13f, Aura.Muted))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    BigButton("Test alarm now", { AlarmReceiver.startRing(ctx, """{"l":"Test alarm","sz":5,"vb":1}""", true) }, Modifier.weight(1f), accent = true)
                    BigButton(if (testing) "Running" else "Self-test", {
                        if (!testing) scope.launch {
                            testing = true; test = emptyList()
                            val r = SelfTest.run(ctx)
                            for (c in r) { delay(260); test = (test ?: emptyList()) + c }
                            testing = false; bump++; onChanged()
                        }
                    }, Modifier.weight(1f), enabled = !testing)
                }
                val t = test
                if (t != null && t.isNotEmpty()) {
                    t.forEach { c ->
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            BasicText(c.title, Modifier.weight(1f), style = ts(14f))
                            BasicText(if (c.ok) "ok" else "Needs attention", style = ts(13f, if (c.ok) Aura.Mint else Aura.Rose, FontWeight.Bold))
                        }
                    }
                    if (!testing) BasicText(SelfTest.summary(t), style = ts(15f, if (t.all { it.ok }) Aura.Mint else Aura.Rose, FontWeight.ExtraBold))
                }
            }
        }

        item(key = "rel-l") { SectionLabel("Reliability") }
        item(key = "rel") {
            Panel(seen, "rel") {
                Row(Modifier.fillMaxWidth()) {
                    BasicText("Last fired", Modifier.weight(1f), style = ts(15f))
                    BasicText(if (lastFired.isEmpty()) "Never" else fmtLast(lastFired, is24), style = ts(15f, Aura.Muted, FontWeight.Bold))
                }
                Row(Modifier.fillMaxWidth()) {
                    BasicText("Backup ring", Modifier.weight(1f), style = ts(15f))
                    BasicText(if (nextT > 0) fmtAt(nextT + 120_000L, is24) else "No alarm on", style = ts(15f, Aura.Muted, FontWeight.Bold))
                }
                if (missed) {
                    BasicText("The last alarm only rang because its backup fired. Check the protections above.", style = ts(13f, Aura.Rose, FontWeight.Bold))
                    SoftButton("Clear", { Store.set(ctx, "missed", "0"); bump++; onChanged() })
                }
            }
        }

        item(key = "ring-l") { SectionLabel("Ring style") }
        item(key = "ring") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    "Gentle ramp" to "Volume rises over 30 seconds",
                    "Standard" to "Full volume, steady",
                    "Max + vibrate" to "Loudest, always vibrates, no snooze",
                ).forEachIndexed { i, (t, d) -> Choice(t, d, ro == i) { Settings.setInt(ctx, "ro", i); bump++ } }
            }
        }

        item(key = "vol-l") { SectionLabel("Volume buttons while ringing") }
        item(key = "vol") {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("vol" to "Change volume", "snooze" to "Snooze", "stop" to "Dismiss", "none" to "Do nothing").forEach { (k, n) ->
                    ChipButton(n, vbtn == k) { Settings.setStr(ctx, "vbtn", k); bump++ }
                }
            }
        }
        if (vbtn == "stop") item(key = "vol-note") { BasicText("Alarms with a wake-up challenge ignore this until the challenge is solved.", Modifier.padding(start = 4.dp), style = ts(13f, Aura.Muted)) }

        item(key = "fmt-l") { SectionLabel("Time format") }
        item(key = "fmt") {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("auto" to "Phone setting (${if (DateFormat.is24HourFormat(ctx)) "24" else "12"}-hour)", "12" to "12-hour", "24" to "24-hour").forEach { (k, n) ->
                    ChipButton(n, tfmt == k) { Settings.setStr(ctx, "tfmt", k); bump++; onChanged() }
                }
            }
        }

        item(key = "bed-l") { SectionLabel("Bedtime") }
        item(key = "bed") {
            Panel(seen, "bed") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        BasicText("Wind-down reminder", style = ts(16f, weight = FontWeight.Bold))
                        BasicText("A nudge so you get your sleep before the next alarm. No sleep tracking.", style = ts(13f, Aura.Muted))
                    }
                    LiquidToggle(selected = { bed.on }, onSelect = { Settings.setBed(ctx, bed.copy(on = it)); bump++ }, backdrop = flat)
                }
                BasicText("Sleep goal", style = ts(13f, Aura.Muted, FontWeight.Bold))
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Bed.GOALS.forEach { g -> ChipButton("$g h", bed.goal == g) { Settings.setBed(ctx, bed.copy(goal = g)); bump++ } }
                }
                BasicText("Wind down", style = ts(13f, Aura.Muted, FontWeight.Bold))
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Bed.WINDS.forEach { w -> ChipButton("$w min", bed.wind == w) { Settings.setBed(ctx, bed.copy(wind = w)); bump++ } }
                }
                val at = bed.reminderAt(nextT, now)
                if (bed.on) BasicText(if (at > 0) "Next reminder at " + fmtAt(at, is24) else "No reminder waiting", style = ts(13f, Aura.Muted))
            }
        }

        item(key = "night-l") { SectionLabel("Night clock") }
        item(key = "night") {
            Panel(seen, "night") {
                BasicText("A black screen with a dim red clock that drifts slowly. The screen stays on.", style = ts(13f, Aura.Muted))
                BigButton("Open night clock", onNight, Modifier.fillMaxWidth())
            }
        }

        item(key = "bk-l") { SectionLabel("Backup") }
        item(key = "bk") {
            Panel(seen, "bk") {
                BasicText("Alarms, cities, bedtime and settings as plain text. Keep it somewhere safe.", style = ts(13f, Aura.Muted))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    SoftButton(if (copied) "Copied" else "Copy", { clip().setPrimaryClip(ClipData.newPlainText("Aura backup", Backup.exportAll(ctx))); copied = true })
                    SoftButton("Share", {
                        val send = Intent(Intent.ACTION_SEND).setType("text/plain")
                            .putExtra(Intent.EXTRA_SUBJECT, "Aura Alarm backup").putExtra(Intent.EXTRA_TEXT, Backup.exportAll(ctx))
                        try { ctx.startActivity(Intent.createChooser(send, "Share backup")) } catch (e: Exception) { }
                    })
                }
                BasicText("Restore", style = ts(13f, Aura.Muted, FontWeight.Bold))
                Field(backupText, { backupText = it }, "Paste a backup here")
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    SoftButton("Paste", { backupText = clip().primaryClip?.getItemAt(0)?.coerceToText(ctx)?.toString().orEmpty() })
                    if (parsed != null) {
                        SoftButton(if (confirmRestore) "Replace all alarms?" else "Restore", {
                            if (!confirmRestore) confirmRestore = true
                            else {
                                restoreMsg = if (Backup.restore(ctx, backupText)) "Restored ${parsed.alarms.size} alarm" + (if (parsed.alarms.size == 1) "" else "s") else "Could not restore"
                                backupText = ""; bump++; onChanged()
                            }
                        }, color = if (confirmRestore) Aura.Rose else Aura.Text)
                    }
                }
                if (backupText.isNotBlank() && parsed == null) BasicText("That is not a valid backup.", style = ts(13f, Aura.Rose, FontWeight.Bold))
                else if (parsed != null) BasicText("Backup has ${parsed.alarms.size} alarm" + (if (parsed.alarms.size == 1) "" else "s") + ". Restoring replaces your current ones.", style = ts(13f, Aura.Muted))
                restoreMsg?.let { BasicText(it, style = ts(13f, Aura.Mint, FontWeight.Bold)) }
            }
        }

        item(key = "about") {
            BasicText(
                "Aura Alarm  \u00B7  no internet permission, no analytics, no ads",
                Modifier.fillMaxWidth().padding(top = 8.dp), style = centered(ts(12f, Aura.Muted)),
            )
        }
    }
}
