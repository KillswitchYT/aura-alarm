package com.killswitch.aura.ui

import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.draw.clip
import com.kyant.shapes.Capsule
import android.media.AudioManager
import com.killswitch.aura.Vol
import kotlin.math.roundToInt
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.rememberUpdatedState
import com.killswitch.aura.BackupFile
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.text.format.DateFormat
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.isSystemInDarkTheme
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
import com.killswitch.aura.glass.LiquidSlider
import com.killswitch.aura.glass.LiquidToggle
import com.kyant.backdrop.backdrops.rememberCanvasBackdrop
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Calendar

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.graphics.SolidColor
import com.killswitch.aura.glass.LiquidButton

// ---------------------------------------------------------------------------------------------------------------------------
// Setup is a hub: a search field and a few sections of rows. Each row opens a screen of its own with a back button, like the
// settings reference. Everything that used to sit in one long list is still here, only grouped by what it is for.
// ---------------------------------------------------------------------------------------------------------------------------

private val THEMES = listOf("Match phone", "Dark", "Light")
private val RINGS = listOf(
    "Gentle ramp" to "Volume rises over 30 seconds",
    "Standard" to "Full volume, steady",
    "Max + vibrate" to "Loudest, always vibrates, no snooze",
)
private val VBTNS = listOf("vol" to "Change volume", "snooze" to "Snooze", "stop" to "Dismiss", "none" to "Do nothing")

/** One thing you can search for. [dest] is the screen it lives on ("night" opens the night clock itself). */
private class SearchEntry(val title: String, val where: String, val dest: String, val words: String, val icon: Ic)

private val INDEX = listOf(
    SearchEntry("Protection checks", "Protection", "protection", "exact alarm notifications full screen samsung battery sleeping apps do not disturb dnd sound permission on off", Ic.Shield),
    SearchEntry("Re-arm all alarms", "Protection", "protection", "fix reschedule", Ic.Shield),
    SearchEntry("Check exact-alarm access", "Protection", "protection", "fix reminders permission", Ic.Shield),
    SearchEntry("Open Samsung sleeping apps", "Protection", "protection", "fix battery never sleeping", Ic.Shield),
    SearchEntry("Raise alarm volume", "Protection", "protection", "fix loud bluetooth", Ic.Shield),
    SearchEntry("Test alarm now", "Test and status", "test", "ring sound vibration lock screen", Ic.Play),
    SearchEntry("Self-test", "Test and status", "test", "check run", Ic.Play),
    SearchEntry("Last fired", "Test and status", "test", "backup ring missed status history", Ic.Play),
    SearchEntry("Alarm volume", "Sound and ringing", "sound", "loud quiet percent slider", Ic.Volume),
    SearchEntry("Ring style", "Sound and ringing", "sound", "gentle ramp standard max vibrate", Ic.Volume),
    SearchEntry("Volume buttons while ringing", "Sound and ringing", "sound", "snooze dismiss do nothing", Ic.Volume),
    SearchEntry("Bedtime reminder", "Bedtime", "bedtime", "sleep goal wind down nudge", Ic.Sleep),
    SearchEntry("Night clock", "Opens the night clock", "night", "dim red screen bedside", Ic.Moon),
    SearchEntry("Theme", "Appearance and time", "display", "dark light match phone mode colour", Ic.Sun),
    SearchEntry("Time format", "Appearance and time", "display", "12-hour 24-hour clock", Ic.Sun),
    SearchEntry("Export or import a backup", "Backup", "backup", "restore file save json", Ic.Backup),
    SearchEntry("Privacy and version", "About", "about", "internet analytics ads frame meter", Ic.Info),
)

/** One grouped block, as in the reference: a soft lift behind the rows of one topic, rows split by hairlines. Only Setup uses them. */
@Composable
private fun Block(title: String?, content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(Aura.Card.copy(alpha = .65f)).padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        if (title != null) BasicText(title, Modifier.padding(bottom = 4.dp), style = ts(17f, weight = FontWeight.ExtraBold))
        content()
    }
}

/** A heading and one quiet line, then the block of that section. */
@Composable
private fun Section(index: Int, seen: MutableSet<Any>, title: String, sub: String, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().staggerIn(index, seen.add("setup-sec-$index")), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Column(Modifier.padding(start = 4.dp, top = 6.dp)) {
            BasicText(title, style = ts(19f, weight = FontWeight.ExtraBold))
            BasicText(sub, style = ts(13f, Aura.Muted))
        }
        Block(null, content)
    }
}

/** An icon, a name, what it is set to now, and a chevron. Opens a screen. */
@Composable
private fun NavRow(icon: Ic, title: String, value: String?, valueColor: Color, first: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().pressable(onClick).then(if (first) Modifier else Modifier.topHairline()).padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, Aura.Text, Modifier.size(22.dp))
        Spacer(Modifier.size(14.dp))
        BasicText(title, Modifier.weight(1f), style = ts(16f, weight = FontWeight.Bold))
        if (value != null) {
            BasicText(value, style = ts(14f, valueColor))
            Spacer(Modifier.size(6.dp))
        }
        Icon(Ic.Chevron, Aura.Muted, Modifier.size(20.dp))
    }
}

/** A label on the left and a value on the right. Not tappable. */
@Composable
private fun ValueRow(label: String, value: String, first: Boolean) {
    Row(Modifier.fillMaxWidth().then(if (first) Modifier else Modifier.topHairline()).padding(vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        BasicText(label, Modifier.weight(1f), style = ts(16f, weight = FontWeight.Bold))
        BasicText(value, style = ts(15f, Aura.Muted, FontWeight.Bold))
    }
}

@Composable
private fun Choice(title: String, sub: String?, selected: Boolean, first: Boolean, onClick: () -> Unit) {
    val sel by selectAnim(selected)
    Row(
        Modifier.fillMaxWidth().pressable(onClick).then(if (first) Modifier else Modifier.topHairline()).padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            BasicText(title, style = ts(16f, weight = FontWeight.Bold))
            if (sub != null) BasicText(sub, style = ts(13f, Aura.Muted))
        }
        Icon(Ic.Check, Aura.AccentText, Modifier.size(22.dp).graphicsLayer { alpha = sel; scaleX = .6f + .4f * sel; scaleY = .6f + .4f * sel })
    }
}

/** The rounded search field of the reference. */
@Composable
private fun SearchPill(value: String, onChange: (String) -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(48.dp).clip(Capsule()).background(Aura.overlay(.07f)).padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Ic.Search, Aura.Muted, Modifier.size(20.dp))
        Spacer(Modifier.size(10.dp))
        BasicTextField(
            value, onChange, Modifier.weight(1f), singleLine = true, textStyle = ts(15f),
            cursorBrush = SolidColor(Aura.Accent),
            decorationBox = { inner ->
                if (value.isEmpty()) BasicText("Search settings", style = ts(15f, Aura.Muted))
                inner()
            },
        )
        if (value.isNotEmpty()) Box(Modifier.pressable { onChange("") }.padding(4.dp)) { Icon(Ic.Close, Aura.Muted, Modifier.size(18.dp)) }
    }
}

/** Library glass, the same as the close button, pointing back. */
@Composable
private fun BackButton(onClick: () -> Unit) {
    LiquidButton(onClick, glassBackdrop(), Modifier.size(40.dp), height = 40.dp, horizontalPadding = 0.dp) {
        Icon(Ic.Chevron, Aura.Text, Modifier.size(18.dp).graphicsLayer { rotationZ = 180f })
    }
}

@Composable
private fun ScreenHeader(title: String, sub: String?, onBack: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(start = 2.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        BackButton(onBack)
        PageTitle(title, sub, Modifier.padding(start = 2.dp))
    }
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
    onFix: (Int) -> Unit, onRunFix: (String) -> Unit, onNight: () -> Unit, onChanged: () -> Unit, switchInfo: String = "", active: Boolean = true,
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
    var av by remember(rev, bump) { mutableIntStateOf(Settings.alarmPercent(ctx)) }
    var themeMode by remember(rev, bump) { mutableIntStateOf(Settings.theme(ctx)) }
    val sysDark = isSystemInDarkTheme()
    LaunchedEffect(av) { if (av != Settings.alarmPercent(ctx)) { delay(350); Settings.setInt(ctx, "av", av); onChanged() } }

    // Backups are files. The system file picker does the saving and the opening, so the app needs no storage permission.
    var fileMsg by remember { mutableStateOf<Pair<String, Boolean>?>(null) } // text, is it good news
    var importText by remember { mutableStateOf<String?>(null) }
    var confirmRestore by remember { mutableStateOf(false) }
    val parsed = remember(importText) { importText?.let { Backup.parse(it) } }
    val exportFile = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri: Uri? ->
        if (uri != null) fileMsg = if (BackupFile.write(ctx, uri, BackupFile.text(ctx))) "Saved the backup file" to true else "Could not save the file" to false
    }
    val importFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            confirmRestore = false
            val t = BackupFile.read(ctx, uri)
            importText = t
            fileMsg = if (t == null) "Could not read that file" to false else null
        }
    }

    // Moving between the hub and a screen: the old one slides a little and fades out, the new one slides in. Same rhythm as the tabs.
    val scroll = rememberScrollState()
    val prog = remember { Animatable(1f) }
    var dir by remember { mutableIntStateOf(1) }
    var leaving by remember { mutableStateOf(false) }
    var shown by rememberSaveable { mutableStateOf("") } // "" is the hub
    var query by remember { mutableStateOf("") }
    fun go(to: String, forward: Boolean) {
        if (to == shown || prog.isRunning) return
        scope.launch {
            dir = if (forward) 1 else -1
            leaving = true
            prog.animateTo(0f, tween(130))
            shown = to
            scroll.scrollTo(0)
            leaving = false
            prog.animateTo(1f, tween(250, easing = FastOutSlowInEasing))
        }
    }
    fun openScreen(dest: String) { query = ""; if (dest == "night") onNight() else go(dest, true) }
    fun goBack() { go("", false) }
    BackHandler(enabled = active && shown.isNotEmpty()) { goBack() }

    Column(
        Modifier.fillMaxSize().verticalScroll(scroll).padding(pad)
            .graphicsLayer { alpha = prog.value; translationX = (if (leaving) -dir else dir) * (1f - prog.value) * 28.dp.toPx() },
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        when (shown) {
            // ---------------------------------------------------------------- the hub
            "" -> {
                Row(Modifier.fillMaxWidth().padding(start = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        BasicText("Setup", style = ts(26f, weight = FontWeight.ExtraBold))
                        BasicText("$on of ${checks.size} protections on", style = ts(14f, if (all) Aura.Mint else Aura.Muted))
                    }
                    Icon(Ic.Check, Aura.Mint, Modifier.size(28.dp).graphicsLayer { alpha = tick; scaleX = .6f + .4f * tick; scaleY = .6f + .4f * tick })
                }
                SearchPill(query) { query = it }
                val terms = query.trim().lowercase().split(" ").filter { it.isNotEmpty() }
                if (terms.isNotEmpty()) {
                    val hits = INDEX.filter { e ->
                        val hay = (e.title + " " + e.where + " " + e.words).lowercase()
                        terms.all { hay.contains(it) }
                    }
                    if (hits.isEmpty()) NoResults("Nothing in Setup matches \"" + query.trim() + "\"")
                    else Block(null) { hits.forEachIndexed { i, e -> NavRow(e.icon, e.title, e.where, Aura.Muted, i == 0) { openScreen(e.dest) } } }
                } else {
                    Section(0, seen, "Reliability", "Make sure your alarms always ring") {
                        NavRow(Ic.Shield, "Protection", if (all) "All on" else "${checks.count { !it.ok }} need attention", if (all) Aura.Mint else Aura.Rose, true) { openScreen("protection") }
                        NavRow(Ic.Play, "Test and status", if (lastFired.isEmpty()) "Not run yet" else fmtLast(lastFired, is24), Aura.Muted, false) { openScreen("test") }
                    }
                    Section(1, seen, "Alarms", "How they ring and when to rest") {
                        NavRow(Ic.Volume, "Sound and ringing", "$av%  \u00B7  " + RINGS[ro.coerceIn(0, 2)].first, Aura.Muted, true) { openScreen("sound") }
                        NavRow(Ic.Sleep, "Bedtime", if (bed.on) "On" else "Off", Aura.Muted, false) { openScreen("bedtime") }
                        NavRow(Ic.Moon, "Night clock", null, Aura.Muted, false) { openScreen("night") }
                    }
                    Section(2, seen, "App", "Look, backup and privacy") {
                        NavRow(Ic.Sun, "Appearance and time", THEMES[themeMode.coerceIn(0, 2)], Aura.Muted, true) { openScreen("display") }
                        NavRow(Ic.Backup, "Backup", null, Aura.Muted, false) { openScreen("backup") }
                        NavRow(Ic.Info, "About", null, Aura.Muted, false) { openScreen("about") }
                    }
                }
            }

            // ---------------------------------------------------------------- protection: the checks and the fixes
            "protection" -> {
                ScreenHeader("Protection", "$on of ${checks.size} on", { goBack() })
                Block("Checks") {
                    checks.forEachIndexed { i, c ->
                        Row(Modifier.fillMaxWidth().then(if (i == 0) Modifier else Modifier.topHairline()).padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                BasicText(c.title, style = ts(16f, weight = FontWeight.Bold))
                                BasicText(c.hint, style = ts(13f, Aura.Muted))
                            }
                            if (c.ok) BasicText("On", style = ts(15f, Aura.Mint, FontWeight.Bold))
                            else Box(Modifier.pressable { onFix(i) }.padding(8.dp)) { BasicText("Fix", style = ts(15f, Aura.AccentText, FontWeight.ExtraBold)) }
                        }
                    }
                }
                Block("Fixes") {
                    Perms.FIXES.forEachIndexed { i, f ->
                        Row(
                            Modifier.fillMaxWidth().pressable { onRunFix(f.id) }.then(if (i == 0) Modifier else Modifier.topHairline()).padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                BasicText(f.title, style = ts(16f, weight = FontWeight.Bold))
                                BasicText(f.hint, style = ts(13f, Aura.Muted))
                            }
                            Icon(Ic.Chevron, Aura.Muted, Modifier.size(20.dp))
                        }
                    }
                }
            }

            // ---------------------------------------------------------------- test and status
            "test" -> {
                ScreenHeader("Test and status", "Check the ring on this phone", { goBack() })
                Block("Test it") {
                    BasicText("A real ring: sound, vibration and the lock-screen alarm. Snooze in a test lasts 10 seconds.", Modifier.padding(bottom = 12.dp), style = ts(13f, Aura.Muted))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        BigButton("Test alarm now", { AlarmReceiver.startRing(ctx, """{"l":"Test alarm","sz":5,"vb":1,"gr":0}""", true) }, Modifier.weight(1f), accent = true)
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
                        Column(Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
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
                Block("Status") {
                    ValueRow("Last fired", if (lastFired.isEmpty()) "Never" else fmtLast(lastFired, is24), true)
                    ValueRow("Backup ring", if (nextT > 0) fmtAt(nextT + 120_000L, is24) else "No alarm on", false)
                    if (missed) {
                        Column(Modifier.topHairline().padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            BasicText("The last alarm only rang because its backup fired. Check the protections.", style = ts(13f, Aura.Rose, FontWeight.Bold))
                            Row { SoftButton("Clear", { Store.set(ctx, "missed", "0"); bump++; onChanged() }) }
                        }
                    }
                }
            }

            // ---------------------------------------------------------------- sound and ringing
            "sound" -> {
                ScreenHeader("Sound and ringing", "Volume, style and the volume buttons", { goBack() })
                Block("Alarm volume") {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        BasicText("How loud it rings", Modifier.weight(1f), style = ts(16f, weight = FontWeight.Bold))
                        BasicText("$av%", style = ts(18f, if (Vol.ok(av)) Aura.Text else Aura.Rose, FontWeight.ExtraBold))
                    }
                    Spacer(Modifier.size(10.dp))
                    // The repo's slider. It reports every step while you drag; saving waits until you stop for a moment.
                    LiquidSlider(
                        value = { av.toFloat() },
                        onValueChange = { av = it.roundToInt().coerceIn(Vol.FLOOR, 100) },
                        valueRange = Vol.FLOOR.toFloat()..100f,
                        visibilityThreshold = 0.01f,
                        backdrop = glassBackdrop(),
                    )
                    Spacer(Modifier.size(10.dp))
                    BasicText(
                        if (Vol.ok(av)) "Aura sets the phone's alarm volume to full when it rings and uses this on top. The phone's own sliders do not change it."
                        else "Quiet. Under ${Vol.MIN_PERCENT}% is easy to sleep through, and the Sound check stays off.",
                        style = ts(13f, if (Vol.ok(av)) Aura.Muted else Aura.Rose),
                    )
                    Spacer(Modifier.size(12.dp))
                    Row { SoftButton("Play a test", { Settings.setInt(ctx, "av", av); AlarmReceiver.startRing(ctx, """{"l":"Test alarm","sz":5,"vb":1,"gr":0}""", true) }) }
                }
                Block("Ring style") {
                    RINGS.forEachIndexed { i, (t, d) -> Choice(t, d, ro == i, i == 0) { Settings.setInt(ctx, "ro", i); bump++ } }
                }
                Block("Volume buttons while ringing") {
                    Row(Modifier.horizontalScroll(rememberScrollState()).padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        VBTNS.forEach { (k, n) -> ChipButton(n, vbtn == k) { Settings.setStr(ctx, "vbtn", k); bump++ } }
                    }
                    if (vbtn == "stop") BasicText("Alarms with a wake-up challenge ignore this until the challenge is solved.", Modifier.padding(top = 10.dp), style = ts(13f, Aura.Muted))
                }
            }

            // ---------------------------------------------------------------- bedtime
            "bedtime" -> {
                ScreenHeader("Bedtime", "A nudge to get your sleep", { goBack() })
                Block(null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            BasicText("Wind-down reminder", style = ts(16f, weight = FontWeight.Bold))
                            BasicText("A nudge so you get your sleep before the next alarm. No sleep tracking.", style = ts(13f, Aura.Muted))
                        }
                        Spacer(Modifier.size(12.dp))
                        LiquidToggle(selected = { bed.on }, onSelect = { Settings.setBed(ctx, bed.copy(on = it)); bump++ }, backdrop = flat)
                    }
                    BasicText("Sleep goal", Modifier.padding(top = 16.dp, bottom = 8.dp), style = ts(13f, Aura.Muted, FontWeight.Bold))
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Bed.GOALS.forEach { g -> ChipButton("$g h", bed.goal == g) { Settings.setBed(ctx, bed.copy(goal = g)); bump++ } }
                    }
                    BasicText("Wind down", Modifier.padding(top = 16.dp, bottom = 8.dp), style = ts(13f, Aura.Muted, FontWeight.Bold))
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Bed.WINDS.forEach { w -> ChipButton("$w min", bed.wind == w) { Settings.setBed(ctx, bed.copy(wind = w)); bump++ } }
                    }
                    val at = bed.reminderAt(nextT, now)
                    if (bed.on) BasicText(if (at > 0) "Next reminder at " + fmtAt(at, is24) else "No reminder waiting", Modifier.padding(top = 14.dp), style = ts(13f, Aura.Muted))
                }
            }

            // ---------------------------------------------------------------- appearance and time
            "display" -> {
                ScreenHeader("Appearance and time", "Colours and the clock format", { goBack() })
                Block("Theme") {
                    THEMES.forEachIndexed { i, n ->
                        Choice(n, null, themeMode == i, i == 0) { themeMode = i; Settings.setInt(ctx, "th", i); Aura.apply(i, sysDark) }
                    }
                }
                Block("Time format") {
                    listOf(
                        "auto" to "Phone setting (${if (DateFormat.is24HourFormat(ctx)) "24" else "12"}-hour)",
                        "12" to "12-hour",
                        "24" to "24-hour",
                    ).forEachIndexed { i, (k, n) ->
                        Choice(n, null, tfmt == k, i == 0) { Settings.setStr(ctx, "tfmt", k); bump++; onChanged() }
                    }
                }
            }

            // ---------------------------------------------------------------- backup
            "backup" -> {
                ScreenHeader("Backup", "Keep your alarms safe", { goBack() })
                Block("Backup file") {
                    BasicText(
                        "Everything in one file: alarms, cities, bedtime and settings. Keep a copy in Downloads or a cloud drive so a reset phone cannot take your alarms with it.",
                        Modifier.padding(bottom = 12.dp), style = ts(13f, Aura.Muted),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        SoftButton("Export to file", { fileMsg = null; exportFile.launch(BackupFile.name(System.currentTimeMillis())) })
                        SoftButton("Import from file", { fileMsg = null; importFile.launch(arrayOf("*/*")) })
                    }
                    if (importText != null && parsed == null) BasicText("That file is not an Aura Alarm backup.", Modifier.padding(top = 12.dp), style = ts(13f, Aura.Rose, FontWeight.Bold))
                    if (parsed != null) {
                        val n = parsed.alarms.size
                        BasicText("This backup has $n alarm" + (if (n == 1) "" else "s") + ". Restoring replaces the ones you have now.", Modifier.padding(top = 12.dp, bottom = 10.dp), style = ts(13f, Aura.Muted))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                            SoftButton(if (confirmRestore) "Replace everything?" else "Restore", {
                                if (!confirmRestore) confirmRestore = true
                                else {
                                    val ok = Backup.restore(ctx, importText.orEmpty())
                                    fileMsg = (if (ok) "Restored $n alarm" + (if (n == 1) "" else "s") else "Could not restore") to ok
                                    importText = null; confirmRestore = false; bump++; onChanged()
                                }
                            }, color = if (confirmRestore) Aura.Rose else Aura.Text)
                            SoftButton("Cancel", { importText = null; confirmRestore = false })
                        }
                    }
                    fileMsg?.let { BasicText(it.first, Modifier.padding(top = 12.dp), style = ts(13f, if (it.second) Aura.Mint else Aura.Rose, FontWeight.Bold)) }
                }
            }

            // ---------------------------------------------------------------- about
            else -> {
                ScreenHeader("About", "Aura Alarm", { goBack() })
                val version = remember { try { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName ?: "" } catch (e: Exception) { "" } }
                Block(null) {
                    ValueRow("Version", version.ifEmpty { "unknown" }, true)
                    ValueRow("Internet", "No permission", false)
                    ValueRow("Analytics and ads", "None", false)
                }
                BasicText("Your alarms, cities and settings stay on this phone. Nothing is sent anywhere.", Modifier.padding(horizontal = 4.dp), style = ts(13f, Aura.Muted))
                if (switchInfo.isNotEmpty()) BasicText(switchInfo + " A smooth frame is about 17 ms. Over 50 ms you feel a stutter.", Modifier.padding(horizontal = 4.dp), style = ts(12f, Aura.Muted))
            }
        }
    }
}
