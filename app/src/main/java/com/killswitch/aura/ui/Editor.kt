package com.killswitch.aura.ui

import android.app.TimePickerDialog
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.graphics.graphicsLayer
import kotlinx.coroutines.launch
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.killswitch.aura.Alarm
import com.killswitch.aura.ampm
import com.killswitch.aura.fmtTime
import com.killswitch.aura.glass.LiquidButton
import com.killswitch.aura.glass.LiquidToggle
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.rememberCanvasBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.vibrancy
import com.kyant.shapes.Capsule
import com.kyant.shapes.RoundedRectangle

@Composable
private fun Section(text: String) = BasicText(text, style = ts(13f, Aura.Muted, FontWeight.Bold))

@Composable
private fun Chip(text: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.pressable(onClick).clip(Capsule())
            .background(if (selected) Aura.Selected else Color.White.copy(alpha = .08f))
            .padding(horizontal = 14.dp, vertical = 9.dp),
    ) { BasicText(text, style = ts(14f, if (selected) Aura.Text else Aura.Text, FontWeight.Bold)) }
}

@Composable
private fun DayDot(letter: String, on: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.size(38.dp).pressable(onClick).clip(CircleShape)
            .background(if (on) Aura.Selected else Color.White.copy(alpha = .08f)),
        contentAlignment = Alignment.Center,
    ) { BasicText(letter, style = ts(14f, if (on) Aura.Text else Aura.Muted, FontWeight.Bold)) }
}

@Composable
private fun ToggleRow(text: String, value: Boolean, backdrop: Backdrop, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        BasicText(text, Modifier.weight(1f), style = ts(16f))
        LiquidToggle(selected = { value }, onSelect = onChange, backdrop = backdrop)
    }
}

/** Bottom sheet for creating or editing one alarm. Floats over the page and blurs what is behind it. */
@Composable
fun Editor(
    backdrop: Backdrop, start: Alarm, isNew: Boolean, is24: Boolean,
    onSave: (Alarm) -> Unit, onDelete: () -> Unit, onTest: (Alarm) -> Unit, onClose: () -> Unit,
) {
    val ctx = LocalContext.current
    var time by remember { mutableStateOf(start.time) }
    var label by remember { mutableStateOf(start.label) }
    var days by remember { mutableStateOf(start.days) }
    var date by remember { mutableStateOf(start.date) }
    fun pickDate() {
        val c = java.util.Calendar.getInstance()
        if (date.isNotEmpty()) try { val p = date.split("-").map { it.toInt() }; c.set(p[0], p[1] - 1, p[2]) } catch (e: Exception) { }
        android.app.DatePickerDialog(
            ctx, { _, y, mo, d -> date = "%d-%02d-%02d".format(y, mo + 1, d); days = emptySet() },
            c.get(java.util.Calendar.YEAR), c.get(java.util.Calendar.MONTH), c.get(java.util.Calendar.DAY_OF_MONTH),
        ).also { it.datePicker.minDate = System.currentTimeMillis() - 1000L }.show()
    }
    var sound by remember { mutableStateOf(start.sound) }
    var soundName by remember { mutableStateOf(start.soundName) }
    var vibrate by remember { mutableStateOf(start.vibrate) }
    var snooze by remember { mutableIntStateOf(start.snooze) }
    var gradual by remember { mutableStateOf(start.gradual) }
    var challenge by remember { mutableIntStateOf(start.challenge) }
    var skipping by remember { mutableStateOf(start.skip > System.currentTimeMillis()) }
    val flat = rememberCanvasBackdrop { drawRect(Aura.Sheet) }

    // Slides up in 280 ms and back down in 200 ms, with the scrim fading alongside.
    val enter = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) { enter.animateTo(1f, tween(280, easing = FastOutSlowInEasing)) }
    fun leave(then: () -> Unit) { scope.launch { enter.animateTo(0f, tween(200, easing = FastOutSlowInEasing)); then() } }

    val pickSound = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        @Suppress("DEPRECATION")
        val u = r.data?.getParcelableExtra<Uri>(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
        if (u != null) {
            sound = u.toString()
            soundName = try { RingtoneManager.getRingtone(ctx, u)?.getTitle(ctx) ?: "" } catch (e: Exception) { "" }
        }
    }

    fun pickTime() {
        TimePickerDialog(ctx, { _, h, m -> time = "%02d:%02d".format(h, m) },
            time.substringBefore(':').toInt(), time.substringAfter(':').toInt(), is24).show()
    }

    fun build(): Alarm {
        val a = start.copy(
            time = time, label = label.trim(), on = true, days = days, date = if (days.isEmpty()) date else "",
            sound = sound, soundName = if (sound.isEmpty() || sound.startsWith("raw:")) "" else soundName, vibrate = vibrate, snooze = snooze, gradual = gradual, challenge = challenge, skip = 0,
        )
        return if (skipping && days.isNotEmpty()) a.copy(skip = a.next().coerceAtLeast(0)) else a
    }

    val sounds = buildList {
        add("" to "Default"); add("raw:1" to "Classic beep"); add("raw:2" to "Rising chime"); add("raw:3" to "Soft bells")
        if (sound.isNotEmpty() && !sound.startsWith("raw:")) add(sound to soundName.ifEmpty { "Phone sound" })
    }
    val h = time.substringBefore(':').toInt()

    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier.fillMaxSize().graphicsLayer { alpha = enter.value }.background(Color.Black.copy(alpha = .55f))
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { leave(onClose) },
        )
        Column(
            Modifier.align(Alignment.BottomCenter).statusBarsPadding().navigationBarsPadding().imePadding().padding(12.dp)
                .graphicsLayer { translationY = (1f - enter.value) * 56.dp.toPx() }
                .drawBackdrop(
                    backdrop = backdrop,
                    shape = { RoundedRectangle(32f.dp) },
                    effects = { vibrancy(); blur(20f.dp.toPx()) },
                    onDrawSurface = { drawRect(Aura.Sheet.copy(alpha = .55f)) },
                )
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { }
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                BasicText(if (isNew) "New alarm" else "Edit alarm", Modifier.weight(1f), style = ts(20f, weight = FontWeight.ExtraBold))
                Row(Modifier.pressable { pickTime() }.clip(Capsule()).background(Color.White.copy(alpha = .10f)).padding(horizontal = 18.dp, vertical = 10.dp), verticalAlignment = Alignment.Bottom) {
                    BasicText(fmtTime(h, time.substringAfter(':').toInt(), is24), style = ts(34f, Aura.Accent, FontWeight.ExtraBold, -1f))
                    if (!is24) BasicText(" " + ampm(h), Modifier.padding(bottom = 5.dp), style = ts(14f, Aura.Muted, FontWeight.Bold))
                }
            }

            Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color.White.copy(alpha = .07f)).padding(horizontal = 16.dp, vertical = 13.dp)) {
                BasicTextField(
                    label, { label = it }, Modifier.fillMaxWidth(), singleLine = true, textStyle = ts(16f),
                    cursorBrush = SolidColor(Aura.Accent),
                    decorationBox = { inner -> if (label.isEmpty()) BasicText("Label (optional)", style = ts(16f, Aura.Muted)); inner() },
                )
            }

            Section("Repeat")
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                listOf("S", "M", "T", "W", "T", "F", "S").forEachIndexed { d, l ->
                    DayDot(l, d in days) { days = if (d in days) days - d else days + d; date = "" }
                }
            }
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Chip(if (date.isEmpty()) "Pick a date" else "One-off on $date", date.isNotEmpty()) { pickDate() }
                if (date.isNotEmpty()) Chip("Clear date", false) { date = "" }
            }

            Section("Snooze")
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(0, 1, 5, 10, 15).forEach { m -> Chip(if (m == 0) "Off" else "$m min", snooze == m) { snooze = m } }
            }

            Section("Wake-up challenge")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("None", "Math", "Shake").forEachIndexed { i, n -> Chip(n, challenge == i) { challenge = i } }
            }

            Section("Sound")
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                sounds.forEach { (v, n) -> Chip(n, sound == v) { sound = v } }
                Chip("Pick from phone", false) {
                    pickSound.launch(Intent(RingtoneManager.ACTION_RINGTONE_PICKER)
                        .putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM)
                        .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false))
                }
            }

            ToggleRow("Vibrate", vibrate, flat) { vibrate = it }
            ToggleRow("Fade in volume", gradual, flat) { gradual = it }
            if (!isNew && days.isNotEmpty()) Chip("Skip the next ring", skipping) { skipping = !skipping }

            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (!isNew) Row(Modifier.pressable { leave(onDelete) }.padding(8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Ic.Trash, Aura.Rose, Modifier.size(18.dp))
                    BasicText("Delete", style = ts(15f, Aura.Rose, FontWeight.Bold))
                }
                Spacer(Modifier.weight(1f))
                LiquidButton({ onTest(build()) }, flat) {
                    Icon(Ic.Play, Aura.Text, Modifier.size(18.dp))
                    BasicText("Test", style = ts(16f, weight = FontWeight.Bold))
                }
                LiquidButton({ leave { onSave(build()) } }, flat, tint = Aura.Accent) { BasicText("Save", style = ts(16f, Aura.OnAccent, FontWeight.ExtraBold)) }
            }
        }
    }
}
