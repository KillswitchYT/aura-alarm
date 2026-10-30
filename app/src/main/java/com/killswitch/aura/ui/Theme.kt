package com.killswitch.aura.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.killswitch.aura.R
import com.kyant.shapes.Capsule
import kotlinx.coroutines.delay

/** One accent colour, used for the main action and live state only. Everything else is neutral. */
object Aura {
    val Ink = Color(0xFF090B10)
    val Lift = Color(0xFF1A2030)
    val Lift2 = Color(0xFF121722)
    val Accent = Color(0xFFFFB07A)
    val OnAccent = Color(0xFF1C1208)
    val Text = Color(0xFFECEEF3)
    val Muted = Color(0xFF9AA0AE)
    val Selected = Color.White.copy(alpha = .20f)
    val Mint = Color(0xFF6FE3A0)
    val Rose = Color(0xFFFF8A80)
    /** Flat colours the small glass controls (toggle, buttons) refract when they sit inside a card or sheet. */
    val Card = Color(0xFF181D2A)
    val Sheet = Color(0xFF151A26)
}

val Jakarta = FontFamily(
    Font(R.font.plus_jakarta_sans_medium, FontWeight.Medium),
    Font(R.font.plus_jakarta_sans_bold, FontWeight.Bold),
    Font(R.font.plus_jakarta_sans_extrabold, FontWeight.ExtraBold),
)

/** Text style shorthand. Tabular numerals keep the clock digits from jiggling as they tick. */
fun ts(size: Float, color: Color = Aura.Text, weight: FontWeight = FontWeight.Medium, tracking: Float = 0f) = TextStyle(
    color = color, fontSize = size.sp, fontWeight = weight, fontFamily = Jakarta,
    letterSpacing = tracking.sp, fontFeatureSettings = "tnum",
)

fun centered(s: TextStyle) = s.copy(textAlign = TextAlign.Center)

/** The scene behind everything, which the glass refracts. One cool graphite hue, only lighter and darker. */
fun DrawScope.drawAuraScene() {
    val w = size.width
    val h = size.height
    val big = maxOf(w, h)
    drawRect(Aura.Ink)
    fun lift(c: Color, x: Float, y: Float, r: Float, a: Float) = drawCircle(
        Brush.radialGradient(listOf(c.copy(alpha = a), Color.Transparent), center = Offset(x, y), radius = r),
        radius = r, center = Offset(x, y),
    )
    lift(Aura.Lift, w * .15f, h * .08f, big * .80f, .95f)
    lift(Aura.Lift2, w * .90f, h * .75f, big * .70f, .90f)
}

private val EdgeLight = Brush.linearGradient(
    listOf(Color.White.copy(alpha = .30f), Color.White.copy(alpha = .05f), Color.White.copy(alpha = .03f), Color.White.copy(alpha = .12f))
)

/** A frosted card for list content: translucent fill and a thin rim that catches light from the top-left. */
fun Modifier.glassCard(shape: Shape = RoundedCornerShape(28.dp)): Modifier = this
    .clip(shape)
    .background(Brush.linearGradient(listOf(Color.White.copy(alpha = .10f), Color.White.copy(alpha = .04f))))
    .border(1.dp, EdgeLight, shape)

@Composable
fun rememberTick(): () -> Unit {
    val haptic = LocalHapticFeedback.current
    return { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove) }
}

/** Tap target with a spring press-scale and a haptic tick. No ripple, the scale is the feedback. */
@Composable
fun Modifier.pressable(onClick: () -> Unit): Modifier {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) .97f else 1f, spring(stiffness = Spring.StiffnessMedium), label = "press")
    val tick = rememberTick()
    return this
        .graphicsLayer { scaleX = scale; scaleY = scale }
        .clickable(interactionSource = source, indication = null) { tick(); onClick() }
}

/** Fade and rise in, [index] steps after the first. Pass play = false to skip it (already shown once). 260 ms, 55 ms apart. */
@Composable
fun Modifier.staggerIn(index: Int, play: Boolean = true): Modifier {
    val p = remember { Animatable(if (play) 0f else 1f) }
    LaunchedEffect(Unit) {
        if (play) { delay(index * 55L); p.animateTo(1f, tween(260, easing = FastOutSlowInEasing)) }
    }
    return this.graphicsLayer { alpha = p.value; translationY = (1f - p.value) * 24.dp.toPx() }
}

/** The one filled button style in the app. */
@Composable
fun AccentPill(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(modifier.pressable(onClick).clip(Capsule()).background(Aura.Accent).padding(horizontal = 22.dp, vertical = 13.dp)) {
        BasicText(text, style = ts(15f, Aura.OnAccent, FontWeight.ExtraBold))
    }
}
