package com.killswitch.aura.ui

import android.app.TimePickerDialog
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.killswitch.aura.City
import com.killswitch.aura.Settings
import com.killswitch.aura.WorldClock
import com.killswitch.aura.ampm
import com.killswitch.aura.fmtTime
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.vibrancy
import com.kyant.shapes.Capsule
import com.kyant.shapes.RoundedRectangle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.ZoneId
import java.util.Calendar
import kotlin.math.cos
import kotlin.math.sin

private fun here(): String = ZoneId.systemDefault().id

@Composable
fun WorldTab(rev: Int, now: Long, is24: Boolean, pad: PaddingValues, seen: MutableSet<Any>, onAdd: () -> Unit) {
    val ctx = LocalContext.current
    var saved by remember { mutableStateOf<List<String>?>(null) } // null while loading
    var analog by remember { mutableStateOf(Settings.analogWorld(ctx)) }
    var convMin by remember { mutableStateOf<Int?>(null) }       // null = follow the clock
    LaunchedEffect(rev) { saved = withContext(Dispatchers.IO) { WorldClock.savedOrDefault(ctx) } }

    val cal = Calendar.getInstance().apply { timeInMillis = now }
    val nowMin = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
    val cities = saved?.mapNotNull { City.parse(it) }.orEmpty()

    LazyColumn(Modifier.fillMaxSize(), contentPadding = pad, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item(key = "head") {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PageTitle("World", "Compare times across the globe", Modifier.weight(1f))
                SoftButton(if (analog) "Digital" else "Analog", { analog = !analog; WorldClock.setAnalog(ctx, analog) })
            }
        }
        item(key = "add") { Box(Modifier.staggerIn(0, seen.add("w-add"))) { BigButton("Add city", onAdd, Modifier.fillMaxWidth(), accent = true) } }

        item(key = "here") {
            val id = here()
            Box(Modifier.staggerIn(1, seen.add("w-here"))) {
                CityCard(id.substringAfterLast('/').replace('_', ' '), "Your location  \u00B7  " + safe { WorldClock.gmtLabel(id, now) }, id, now, is24, analog, null)
            }
        }

        if (saved == null) {
            items(2, key = { "skeleton$it" }) { SkeletonCard() }
        } else if (cities.isEmpty()) {
            item(key = "empty") { EmptyState(Ic.World, "No cities yet", "Add a city to see its time next to yours.", "Add a city", onAdd) }
        } else {
            items(cities, key = { it.key }) { c ->
                val sub = listOfNotNull(
                    c.country.ifEmpty { null },
                    safe { WorldClock.dayLabel(c.zone, now) },
                    safe { WorldClock.offsetText(WorldClock.offsetMinutes(c.zone, now)) },
                ).joinToString("  \u00B7  ")
                Box(Modifier.animateItem().staggerIn(2, seen.add("w-" + c.key))) {
                    CityCard(c.name, sub, c.zone, now, is24, analog) {
                        val l = (saved ?: emptyList()).filter { it != c.key }
                        saved = l
                        WorldClock.save(ctx, l)
                    }
                }
            }
            item(key = "conv") { Converter(cities, convMin ?: nowMin, convMin == null, is24, now, { convMin = it }, { convMin = null }) }
        }
    }
}

private inline fun safe(f: () -> String): String = try { f() } catch (e: Exception) { "" }

@Composable
private fun CityCard(name: String, sub: String, zone: String?, now: Long, is24: Boolean, analog: Boolean, onRemove: (() -> Unit)?) {
    val hms = zone?.let { try { WorldClock.hms(it, now) } catch (e: Exception) { null } }
    Row(Modifier.fillMaxWidth().glassCard().padding(horizontal = 20.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            BasicText(name, style = ts(18f, weight = FontWeight.Bold))
            BasicText(if (hms == null) "Unknown time zone" else sub, style = ts(13f, if (hms == null) Aura.Rose else Aura.Muted))
        }
        if (hms != null) {
            if (analog) AnalogClock(hms.first, hms.second, hms.third)
            else Row(verticalAlignment = Alignment.Bottom) {
                BasicText(fmtTime(hms.first, hms.second, is24), style = ts(28f, weight = FontWeight.Bold, tracking = -1f))
                if (!is24) BasicText(" " + ampm(hms.first), Modifier.padding(bottom = 4.dp), style = ts(12f, Aura.Muted, FontWeight.Bold))
            }
        }
        if (onRemove != null) CloseButton(onRemove, Modifier.padding(start = 10.dp))
    }
}

@Composable
private fun AnalogClock(h: Int, m: Int, s: Int) {
    Canvas(Modifier.size(64.dp)) {
        val r = size.minDimension / 2f
        val c = center
        drawCircle(Color.White.copy(alpha = .16f), r - 1.5f.dp.toPx(), c, style = Stroke(2f.dp.toPx()))
        fun hand(deg: Float, len: Float, w: Float, col: Color) {
            val a = Math.toRadians(deg.toDouble() - 90.0)
            drawLine(col, c, Offset(c.x + (cos(a) * len * r).toFloat(), c.y + (sin(a) * len * r).toFloat()), w, StrokeCap.Round)
        }
        hand(((h % 12) + m / 60f) * 30f, .50f, 3.5f.dp.toPx(), Aura.Text)
        hand((m + s / 60f) * 6f, .74f, 2.5f.dp.toPx(), Aura.Text)
        hand(s * 6f, .82f, 1.5f.dp.toPx(), Aura.Accent)
        drawCircle(Aura.Accent, 3f.dp.toPx(), c)
    }
}

/** Pick a time on your clock and see it in every saved city. Follows the clock until you change it. */
@Composable
private fun Converter(
    cities: List<City>, minutes: Int, following: Boolean, is24: Boolean, now: Long,
    onPick: (Int) -> Unit, onNow: () -> Unit,
) {
    val ctx = LocalContext.current
    val h = minutes / 60
    val m = minutes % 60
    Column(Modifier.fillMaxWidth().glassCard().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            BasicText("Time converter", Modifier.weight(1f), style = ts(18f, weight = FontWeight.ExtraBold))
            if (!following) SoftButton("Now", onNow)
        }
        BasicText("Your time", style = ts(13f, Aura.Muted, FontWeight.Bold))
        Row(
            Modifier.pressable { TimePickerDialog(ctx, { _, hh, mm -> onPick(hh * 60 + mm) }, h, m, is24).show() }
                .clip(Capsule()).background(Color.White.copy(alpha = .10f)).padding(horizontal = 18.dp, vertical = 10.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            BasicText(fmtTime(h, m, is24), style = ts(30f, Aura.Accent, FontWeight.ExtraBold, -1f))
            if (!is24) BasicText(" " + ampm(h), Modifier.padding(bottom = 4.dp), style = ts(13f, Aura.Muted, FontWeight.Bold))
        }
        cities.forEach { c ->
            val t = try { WorldClock.converted(c.zone, h, m) } catch (e: Exception) { null }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                BasicText(c.name, Modifier.weight(1f), style = ts(16f, weight = FontWeight.Bold))
                if (t != null) {
                    BasicText(fmtTime(t.first, t.second, is24), style = ts(20f, weight = FontWeight.Bold))
                    if (!is24) BasicText(" " + ampm(t.first), style = ts(12f, Aura.Muted, FontWeight.Bold))
                } else BasicText("Unknown zone", style = ts(13f, Aura.Rose))
            }
        }
    }
}

/** Bottom sheet to search and add a city. Floats over everything, like the alarm editor. */
@Composable
fun CityPicker(backdrop: Backdrop, now: Long, onPick: (City) -> Unit, onClose: () -> Unit) {
    val ctx = LocalContext.current
    var all by remember { mutableStateOf<List<City>?>(null) } // null while the database loads
    var query by remember { mutableStateOf("") }
    val have = remember { WorldClock.savedOrDefault(ctx).toSet() }
    LaunchedEffect(Unit) { all = withContext(Dispatchers.IO) { WorldClock.cities(ctx) } }
    val hits = remember(all, query) { all?.let { WorldClock.search(it, query) } }

    val enter = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) { enter.animateTo(1f, tween(280, easing = FastOutSlowInEasing)) }
    fun leave(then: () -> Unit) { scope.launch { enter.animateTo(0f, tween(200, easing = FastOutSlowInEasing)); then() } }

    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier.fillMaxSize().graphicsLayer { alpha = enter.value }.background(Color.Black.copy(alpha = .55f))
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { leave(onClose) },
        )
        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().fillMaxHeight(.88f)
                .statusBarsPadding().navigationBarsPadding().imePadding().padding(12.dp)
                .graphicsLayer { translationY = (1f - enter.value) * 56.dp.toPx() }
                .drawBackdrop(
                    backdrop = backdrop,
                    shape = { RoundedRectangle(32f.dp) },
                    effects = { vibrancy(); blur(20f.dp.toPx()) },
                    onDrawSurface = { drawRect(Aura.Sheet.copy(alpha = .55f)) },
                )
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { }
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                BasicText("Add city", Modifier.weight(1f), style = ts(20f, weight = FontWeight.ExtraBold))
                CloseButton({ leave(onClose) })
            }
            Field(query, { query = it }, "Search city, country or region")
            when {
                hits == null -> Column(verticalArrangement = Arrangement.spacedBy(10.dp)) { SkeletonCard(2, 14); SkeletonCard(2, 14); SkeletonCard(2, 14) }
                hits.isEmpty() -> NoResults("No match. Try a country, city or region.")
                else -> LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    items(hits, key = { it.key }) { c ->
                        val added = c.key in have
                        Row(
                            Modifier.fillMaxWidth().then(if (added) Modifier else Modifier.pressable { leave { onPick(c) } })
                                .clip(RoundedCornerShape(16.dp)).padding(horizontal = 8.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                BasicText(c.name, style = ts(16f, if (added) Aura.Muted else Aura.Text, FontWeight.Bold))
                                BasicText(
                                    listOfNotNull(c.country.ifEmpty { null }, safe { WorldClock.gmtLabel(c.zone, now) }).joinToString("  \u00B7  "),
                                    style = ts(13f, Aura.Muted),
                                )
                            }
                            if (added) BasicText("Added", style = ts(13f, Aura.Mint, FontWeight.Bold))
                        }
                    }
                }
            }
        }
    }
}
