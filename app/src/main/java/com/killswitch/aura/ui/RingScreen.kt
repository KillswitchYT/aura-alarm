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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
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
import java.util.Calendar

private fun DrawScope.drawPulses(p: Float) {
    for (k in 0..2) {
        val q = (p + k / 3f) % 1f
        drawCircle(
            Aura.Peach.copy(alpha = .55f * (1f - q)),
            radius = size.minDimension * (.12f + .95f * q),
            center = Offset(size.width / 2f, size.height * .86f),
            style = Stroke(3.dp.toPx()),
        )
    }
}

private fun centered(s: TextStyle) = s.copy(textAlign = TextAlign.Center)

/**
 * The full-screen alarm. [challenge] 0 shows Dismiss, 1 asks three sums, 2 waits for [shakes] to reach [shakeGoal]
 * (the activity feeds the sensor). Finishing a challenge calls [onDismiss].
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
            Canvas(Modifier.fillMaxSize()) { drawAuraScene(); drawPulses(p) }
        }
        Column(
            Modifier.align(Alignment.Center).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            BasicText(fmtTime(h, cal.get(Calendar.MINUTE), is24), style = ts(88f, weight = FontWeight.Bold, tracking = -3f))
            if (!is24) BasicText(ampm(h), style = ts(20f, Aura.Muted, FontWeight.Bold))
            Spacer(Modifier.height(4.dp))
            BasicText(label, style = centered(ts(26f, Aura.Peach, FontWeight.Bold)))

            if (math != null) {
                Spacer(Modifier.height(20.dp))
                BasicText("Solve $left more to dismiss", style = ts(14f, Aura.Muted, FontWeight.Bold))
                BasicText(prompt, style = ts(40f, weight = FontWeight.ExtraBold))
                Box(
                    Modifier.width(200.dp).clip(RoundedCornerShape(16.dp)).background(Color.White.copy(alpha = .10f)).padding(horizontal = 16.dp, vertical = 12.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    BasicTextField(
                        input, { input = it.filter { c -> c.isDigit() }.take(4); wrong = false }, Modifier.fillMaxWidth(), singleLine = true,
                        textStyle = centered(ts(26f, weight = FontWeight.Bold)), cursorBrush = SolidColor(Aura.Periwinkle),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { submit() }),
                    )
                }
                if (wrong) BasicText("Not quite, here is a new one", style = ts(14f, Aura.Rose, FontWeight.Bold))
            }

            if (challenge == 2) {
                Spacer(Modifier.height(20.dp))
                BasicText("Shake your phone to dismiss", style = ts(16f, weight = FontWeight.Bold))
                BasicText("$shakes of $shakeGoal", style = ts(14f, Aura.Muted, FontWeight.Bold))
                Box(Modifier.width(220.dp).height(10.dp).clip(Capsule()).background(Color.White.copy(alpha = .12f))) {
                    Box(
                        Modifier.fillMaxWidth((shakes.toFloat() / shakeGoal).coerceIn(0f, 1f)).height(10.dp).clip(Capsule())
                            .background(Brush.horizontalGradient(listOf(Aura.Periwinkle, Aura.Peach))),
                    )
                }
            }
        }

        Row(
            Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(horizontal = 20.dp, vertical = 28.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (canSnooze) LiquidButton(onSnooze, backdrop, Modifier.weight(1f)) { BasicText(snoozeText, style = ts(17f, weight = FontWeight.Bold)) }
            when (challenge) {
                0 -> LiquidButton(onDismiss, backdrop, Modifier.weight(1f), tint = Aura.Periwinkle) {
                    BasicText("Dismiss", style = ts(18f, Aura.Ink, FontWeight.ExtraBold))
                }
                1 -> LiquidButton({ submit() }, backdrop, Modifier.weight(1f), tint = Aura.Periwinkle) {
                    BasicText("Check", style = ts(18f, Aura.Ink, FontWeight.ExtraBold))
                }
            }
        }
    }
}
