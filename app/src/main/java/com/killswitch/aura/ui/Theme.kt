package com.killswitch.aura.ui

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.killswitch.aura.R

object Aura {
    val Ink = Color(0xFF0B0D1A)
    val Ink2 = Color(0xFF15132F)
    val Indigo = Color(0xFF4A4FD6)
    val Teal = Color(0xFF2AA6C9)
    val Periwinkle = Color(0xFFBBC3FF)
    val Peach = Color(0xFFFFB59A)
    val Container = Color(0xFF303788)
    val OnContainer = Color(0xFFE0E1FF)
    val Mint = Color(0xFF6FE3A0)
    val Rose = Color(0xFFFF8A80)
    val Text = Color(0xFFE8E5F3)
    val Muted = Color(0xFFA9A8BE)
    /** Flat colours the small glass controls (toggle, buttons) refract when they sit inside a card or sheet. */
    val Card = Color(0xFF1C1F42)
    val Sheet = Color(0xFF1A1D44)
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

/** The scene behind everything: ink gradient with three soft light orbs. This is what the glass refracts. */
fun DrawScope.drawAuraScene() {
    val w = size.width
    val h = size.height
    val big = maxOf(w, h)
    drawRect(Brush.verticalGradient(listOf(Aura.Ink, Aura.Ink2)))
    fun orb(c: Color, x: Float, y: Float, r: Float, a: Float) = drawCircle(
        Brush.radialGradient(listOf(c.copy(alpha = a), Color.Transparent), center = Offset(x, y), radius = r),
        radius = r, center = Offset(x, y),
    )
    orb(Aura.Indigo, w * .10f, h * .06f, big * .75f, .55f)
    orb(Aura.Peach, w * .95f, h * 1.02f, big * .80f, .28f)
    orb(Aura.Teal, w * .50f, h * .45f, big * .45f, .16f)
}

private val EdgeLight = Brush.linearGradient(
    listOf(Color.White.copy(alpha = .34f), Color.White.copy(alpha = .04f), Color.White.copy(alpha = .02f), Aura.Peach.copy(alpha = .24f))
)

/** A frosted card for list content: translucent fill and a rim that catches light top-left with a warm glint bottom-right. */
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
