package com.killswitch.aura.ui

import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.killswitch.aura.MathChallenge
import com.killswitch.aura.Settings
import com.killswitch.aura.ampm
import com.killswitch.aura.fmtTime
import com.killswitch.aura.glass.LiquidButton
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.shapes.Capsule
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** Rings that grow out of [center], the spot where the finger should go. */
private fun DrawScope.drawPulses(p: Float, center: Offset) {
    for (k in 0..2) {
        val q = (p + k / 3f) % 1f
        drawCircle(
            Aura.Accent.copy(alpha = .45f * (1f - q)),
            radius = size.minDimension * (.10f + .80f * q),
            center = center,
            style = Stroke(3.dp.toPx()),
        )
    }
}

/**
 * Dismissing is a drag, never a tap, so a groggy thumb cannot switch the alarm off by accident
 * (Apple shipped two big side-by-side buttons in iOS 26 and had to add a slide in 26.1).
 * TalkBack gets a plain "Dismiss alarm" action instead of the drag.
 */
@Composable
private fun SlideToDismiss(text: String, onDone: () -> Unit, modifier: Modifier = Modifier) {
    val scope = rememberCoroutineScope()
    val view = LocalView.current
    val density = LocalDensity.current
    val x = remember { Animatable(0f) }
    var track by remember { mutableFloatStateOf(0f) }
    var done by remember { mutableStateOf(false) }
    val knob = with(density) { 60.dp.toPx() }
    val inset = with(density) { 6.dp.toPx() }
    val max = (track - knob - 2 * inset).coerceAtLeast(1f)
    val p = (x.value / max).coerceIn(0f, 1f)

    fun finish() {
        if (done) return
        done = true
        view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        onDone()
    }

    Box(
        modifier.fillMaxWidth().height(72.dp).clip(Capsule()).background(Color.White.copy(alpha = .10f))
            .onSizeChanged { track = it.width.toFloat() }
            .semantics { customActions = listOf(CustomAccessibilityAction("Dismiss alarm") { finish(); true }) }
            .pointerInput(max) {
                detectHorizontalDragGestures(
                    onDragEnd = {
                        scope.launch {
                            if (!done && x.value >= max * .85f) { x.animateTo(max, tween(90)); finish() }
                            else x.animateTo(0f, spring(dampingRatio = .7f, stiffness = 500f))
                        }
                    },
                    onDragCancel = { scope.launch { x.animateTo(0f, spring(dampingRatio = .7f, stiffness = 500f)) } },
                    onHorizontalDrag = { change, dx -> change.consume(); if (!done) scope.launch { x.snapTo((x.value + dx).coerceIn(0f, max)) } },
                )
            },
        contentAlignment = Alignment.CenterStart,
    ) {
        BasicText(text, Modifier.fillMaxWidth().padding(start = 52.dp).graphicsLayer { alpha = 1f - p * 1.5f }, style = centered(ts(17f, Aura.Muted, FontWeight.Bold)))
        Box(
            Modifier.padding(6.dp).size(60.dp).graphicsLayer { translationX = x.value }.clip(CircleShape).background(Aura.Accent),
            contentAlignment = Alignment.Center,
        ) { Icon(Ic.Chevron, Aura.OnAccent, Modifier.size(26.dp)) }
    }
}

/**
 * The full-screen alarm. [challenge] 0 shows a slide to dismiss, 1 asks three sums, 2 waits for [shakes] to reach [shakeGoal]
 * (the activity feeds the sensor). Finishing a challenge calls [onDismiss].
 * Layout: the time is the one big thing, left aligned. Snooze is a separate control above the slide, never beside it.
 */
@Composable
fun RingScreen(
    label: String, challenge: Int, canSnooze: Boolean, snoozeText: String, shakes: Int, shakeGoal: Int,
    onSnooze: () -> Unit, onDismiss: () -> Unit,
) {
    val is24 = Settings.is24(LocalContext.current)
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { now = System.currentTimeMillis(); delay(1000L - now % 1000) } }
    val cal = Calendar.getInstance().apply { timeInMillis = now }
    val h = cal.get(Calendar.HOUR_OF_DAY)

    val backdrop = rememberLayerBackdrop()
    val pulse = rememberInfiniteTransition(label = "pulse")
    val p by pulse.animateFloat(0f, 1f, infiniteRepeatable(tween(3200, easing = LinearEasing), RepeatMode.Restart), label = "p")

    val math = remember(challenge) { if (challenge == 1) MathChallenge() else null }
    var prompt by remember(math) { mutableStateOf(math?.prompt ?: "") }
    var left by remember(math) { mutableIntStateOf(math?.remaining ?: 0) }
    var input by remember { mutableStateOf("") }
    var wrong by remember { mutableStateOf(false) }

    fun submit() {
        val m = math ?: return
        val ok = m.answer(input.trim().toIntOrNull())
        input = ""; wrong = !ok; prompt = m.prompt; left = m.remaining
        if (m.done) onDismiss()
    }

    Box(Modifier.fillMaxSize().imePadding()) {
        Box(Modifier.layerBackdrop(backdrop).fillMaxSize()) {
            Canvas(Modifier.fillMaxSize()) {
                drawAuraScene()
                // With a slide the rings grow from the knob, so the eye knows where to put the thumb.
                drawPulses(p, if (challenge == 0) Offset(66.dp.toPx(), size.height - 96.dp.toPx()) else Offset(size.width / 2f, size.height * .86f))
            }
        }

        Column(Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 28.dp).padding(top = 48.dp)) {
            BasicText(label, style = ts(18f, Aura.Muted, FontWeight.Bold))
            Spacer(Modifier.height(4.dp))
            BasicText(fmtTime(h, cal.get(Calendar.MINUTE), is24), style = ts(100f, weight = FontWeight.Bold, tracking = -4f))
            BasicText(
                (if (is24) "" else ampm(h) + "  \u00B7  ") + SimpleDateFormat("EEEE, MMMM d", Locale.getDefault()).format(Date(now)),
                style = ts(16f, Aura.Muted, FontWeight.Medium),
            )

            if (math != null) {
                Spacer(Modifier.height(44.dp))
                BasicText("Solve $left more to dismiss", style = ts(14f, Aura.Muted, FontWeight.Bold))
                BasicText(prompt, style = ts(52f, weight = FontWeight.ExtraBold, tracking = -1f))
                Spacer(Modifier.height(6.dp))
                Column(Modifier.width(200.dp)) {
                    BasicTextField(
                        input, { input = it.filter { c -> c.isDigit() }.take(4); wrong = false }, Modifier.fillMaxWidth(), singleLine = true,
                        textStyle = ts(34f, weight = FontWeight.Bold), cursorBrush = SolidColor(Aura.Accent),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { submit() }),
                    )
                    Spacer(Modifier.height(6.dp))
                    Box(Modifier.fillMaxWidth().height(2.dp).background(if (wrong) Aura.Rose else Aura.Accent))
                }
                if (wrong) {
                    Spacer(Modifier.height(10.dp))
                    BasicText("Not quite, here is a new one", style = ts(14f, Aura.Rose, FontWeight.Bold))
                }
            }

            if (challenge == 2) {
                Spacer(Modifier.height(44.dp))
                BasicText("Shake your phone to dismiss", style = ts(16f, Aura.Muted, FontWeight.Bold))
                Row(verticalAlignment = Alignment.Bottom) {
                    BasicText("$shakes", style = ts(72f, weight = FontWeight.ExtraBold, tracking = -2f))
                    BasicText("  of $shakeGoal", Modifier.padding(bottom = 12.dp), style = ts(18f, Aura.Muted, FontWeight.Bold))
                }
                Spacer(Modifier.height(8.dp))
                Box(Modifier.fillMaxWidth().height(4.dp).clip(Capsule()).background(Color.White.copy(alpha = .12f))) {
                    Box(Modifier.fillMaxWidth((shakes.toFloat() / shakeGoal).coerceIn(0f, 1f)).height(4.dp).clip(Capsule()).background(Aura.Accent))
                }
            }
        }

        Column(
            Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(horizontal = 24.dp).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            if (canSnooze) LiquidButton(onSnooze, backdrop, Modifier.fillMaxWidth()) { BasicText(snoozeText, style = ts(17f, weight = FontWeight.Bold)) }
            when (challenge) {
                0 -> SlideToDismiss("Slide to dismiss", onDismiss)
                1 -> LiquidButton({ submit() }, backdrop, Modifier.fillMaxWidth(), tint = Aura.Accent) {
                    BasicText("Check", style = ts(18f, Aura.OnAccent, FontWeight.ExtraBold))
                }
            }
        }
    }
}
