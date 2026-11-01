package com.killswitch.aura.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.kyant.shapes.Capsule

/** Shared building blocks for the tabs, so every screen speaks the same visual language. */

/** 0 to 1 and back, 200 ms, for anything that can be selected. Selection never snaps. */
@Composable
fun selectAnim(on: Boolean): State<Float> = animateFloatAsState(if (on) 1f else 0f, tween(200, easing = FastOutSlowInEasing), label = "select")

@Composable
fun PageTitle(title: String, sub: String? = null, modifier: Modifier = Modifier) {
    Column(modifier.padding(start = 4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        BasicText(title, style = ts(26f, weight = FontWeight.ExtraBold))
        if (sub != null) BasicText(sub, style = ts(14f, Aura.Muted))
    }
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) =
    BasicText(text, modifier.padding(start = 4.dp, top = 8.dp), style = ts(13f, Aura.Muted, FontWeight.Bold))

/** The quiet secondary button. The accent is kept for the one main action on a screen. */
@Composable
fun SoftButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, color: Color = Aura.Text, enabled: Boolean = true) {
    Box(
        modifier.then(if (enabled) Modifier.pressable(onClick) else Modifier).clip(Capsule())
            .background(Color.White.copy(alpha = if (enabled) .10f else .05f)).padding(horizontal = 18.dp, vertical = 11.dp),
        contentAlignment = Alignment.Center,
    ) { BasicText(text, style = ts(15f, if (enabled) color else Aura.Muted.copy(alpha = .5f), FontWeight.Bold)) }
}

/** A wide button with centred text. [accent] is the main action of the screen. */
@Composable
fun BigButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, accent: Boolean = false, enabled: Boolean = true) {
    Box(
        modifier.then(if (enabled) Modifier.pressable(onClick) else Modifier).clip(Capsule())
            .background(if (accent) Aura.Accent else Color.White.copy(alpha = if (enabled) .10f else .05f)).padding(vertical = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(text, style = ts(17f, if (accent) Aura.OnAccent else if (enabled) Aura.Text else Aura.Muted.copy(alpha = .5f), FontWeight.ExtraBold))
    }
}

@Composable
fun ChipButton(text: String, selected: Boolean = false, onClick: () -> Unit) {
    val sel by selectAnim(selected)
    Box(
        Modifier.pressable(onClick).clip(Capsule()).background(Color.White.copy(alpha = .08f + .12f * sel))
            .padding(horizontal = 14.dp, vertical = 9.dp),
    ) { BasicText(text, style = ts(14f, Aura.Text, FontWeight.Bold)) }
}

@Composable
fun CloseButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(modifier.size(36.dp).pressable(onClick).clip(CircleShape).background(Color.White.copy(alpha = .08f)), contentAlignment = Alignment.Center) {
        Icon(Ic.Close, Aura.Muted, Modifier.size(16.dp))
    }
}

@Composable
fun Field(
    value: String, onChange: (String) -> Unit, hint: String, modifier: Modifier = Modifier,
    number: Boolean = false, center: Boolean = false, onDone: () -> Unit = {},
) {
    val style: TextStyle = if (center) centered(ts(18f, weight = FontWeight.Bold)) else ts(16f)
    Box(modifier.clip(RoundedCornerShape(16.dp)).background(Color.White.copy(alpha = .07f)).padding(horizontal = 16.dp, vertical = 13.dp)) {
        BasicTextField(
            value, onChange, Modifier.fillMaxWidth(), singleLine = true, textStyle = style, cursorBrush = SolidColor(Aura.Accent),
            keyboardOptions = KeyboardOptions(keyboardType = if (number) KeyboardType.Number else KeyboardType.Text, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { onDone() }),
            decorationBox = { inner ->
                if (value.isEmpty()) BasicText(hint, Modifier.fillMaxWidth(), style = if (center) centered(ts(18f, Aura.Muted, FontWeight.Medium)) else ts(16f, Aura.Muted))
                inner()
            },
        )
    }
}

/** Empty state: says what this screen is for and offers the next step right there. */
@Composable
fun EmptyState(icon: Ic, title: String, body: String, actionText: String? = null, onAction: () -> Unit = {}) {
    Column(
        Modifier.fillMaxWidth().glassCard().padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(icon, Aura.Muted, Modifier.size(40.dp))
        BasicText(title, style = ts(18f, weight = FontWeight.Bold))
        BasicText(body, style = centered(ts(14f, Aura.Muted)))
        if (actionText != null) { Spacer(Modifier.height(4.dp)); AccentPill(actionText, onAction) }
    }
}

/** Error state: nothing is changed unless the person chooses Reset, and Reset asks twice. */
@Composable
fun FailCard(title: String, body: String, onRetry: () -> Unit, onReset: () -> Unit) {
    var confirm by remember { mutableStateOf(false) }
    Column(
        Modifier.fillMaxWidth().glassCard().padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(Ic.Alert, Aura.Rose, Modifier.size(38.dp))
        BasicText(title, style = ts(18f, weight = FontWeight.Bold))
        BasicText(body, style = centered(ts(14f, Aura.Muted)))
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            AccentPill("Try again", onRetry)
            Box(Modifier.pressable { if (confirm) onReset() else confirm = true }.padding(8.dp)) {
                BasicText(if (confirm) "Tap again to erase" else "Reset", style = ts(15f, Aura.Rose, FontWeight.Bold))
            }
        }
    }
}

/** No match: a search or filter found nothing. */
@Composable
fun NoResults(text: String) {
    Column(
        Modifier.fillMaxWidth().padding(vertical = 24.dp, horizontal = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(Ic.Search, Aura.Muted, Modifier.size(32.dp))
        BasicText(text, style = centered(ts(15f, Aura.Muted, FontWeight.Bold)))
    }
}

/** Loading: placeholder card that breathes while data is read. */
@Composable
fun SkeletonCard(lines: Int = 2, height: Int = 20) {
    val t = rememberInfiniteTransition(label = "skeleton")
    val a by t.animateFloat(.35f, .85f, infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Reverse), label = "a")
    Column(
        Modifier.fillMaxWidth().glassCard().padding(horizontal = 20.dp, vertical = height.dp).graphicsLayer { alpha = a },
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(Modifier.size(width = 130.dp, height = 26.dp).clip(RoundedCornerShape(8.dp)).background(Color.White.copy(alpha = .14f)))
        for (i in 1 until lines) Box(Modifier.size(width = 190.dp, height = 12.dp).clip(RoundedCornerShape(6.dp)).background(Color.White.copy(alpha = .10f)))
    }
}
