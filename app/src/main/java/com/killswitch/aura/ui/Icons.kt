package com.killswitch.aura.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

enum class Ic { Alarm, World, Timer, Stopwatch, Setup, Plus }

/** Tiny stroke icons drawn on a canvas, so the app needs no icon library. */
@Composable
fun Icon(kind: Ic, color: Color, modifier: Modifier = Modifier.size(24.dp)) {
    Canvas(modifier) {
        val s = size.minDimension
        val sw = s * .09f
        val st = Stroke(sw, cap = StrokeCap.Round, join = StrokeJoin.Round)
        val c = center
        fun line(x1: Float, y1: Float, x2: Float, y2: Float) = drawLine(color, Offset(x1 * s, y1 * s), Offset(x2 * s, y2 * s), sw, StrokeCap.Round)
        when (kind) {
            Ic.Alarm -> {
                drawCircle(color, s * .34f, Offset(c.x, s * .56f), style = st)
                line(.5f, .56f, .5f, .40f); line(.5f, .56f, .62f, .56f)
                line(.14f, .22f, .28f, .12f); line(.86f, .22f, .72f, .12f)
            }
            Ic.World -> {
                drawCircle(color, s * .38f, c, style = st)
                drawOval(color, Offset(s * .31f, s * .12f), Size(s * .38f, s * .76f), style = st)
                line(.12f, .5f, .88f, .5f)
            }
            Ic.Timer -> {
                val p = Path().apply {
                    moveTo(s * .28f, s * .14f); lineTo(s * .72f, s * .14f); lineTo(s * .5f, s * .5f); close()
                    moveTo(s * .5f, s * .5f); lineTo(s * .28f, s * .86f); lineTo(s * .72f, s * .86f); close()
                }
                drawPath(p, color, style = st)
            }
            Ic.Stopwatch -> {
                drawCircle(color, s * .34f, Offset(c.x, s * .58f), style = st)
                line(.4f, .1f, .6f, .1f)
                line(.5f, .58f, .64f, .44f)
            }
            Ic.Setup -> {
                for (i in 0..2) line(.14f, .25f + .25f * i, .86f, .25f + .25f * i)
                val xs = listOf(.34f, .66f, .46f)
                for (i in 0..2) drawCircle(color, s * .075f, Offset(xs[i] * s, (.25f + .25f * i) * s))
            }
            Ic.Plus -> { line(.5f, .2f, .5f, .8f); line(.2f, .5f, .8f, .5f) }
        }
    }
}
