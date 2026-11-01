package com.dotdialer

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.hypot

class Pal(val dark: Boolean) {
    val bg = if (dark) Color(0xFF050505) else Color(0xFFE9E9E6)
    val fg = if (dark) Color(0xFFF4F4F2) else Color(0xFF101010)
    val mute = fg.copy(alpha = .5f)
    val line = fg.copy(alpha = .12f)
    val acc = Color(S.accent.v)
    val onAcc = if (acc.luminance() > .55f) Color.Black else Color.White
}

@Composable
fun pal(): Pal {
    val d = when (S.theme.v) { 1 -> true; 2 -> false; else -> isSystemInDarkTheme() }
    return Pal(d)
}

@Composable
fun Tx(
    s: String, c: Color, size: TextUnit = 14.sp, w: FontWeight = FontWeight.Normal,
    mod: Modifier = Modifier, align: TextAlign = TextAlign.Start,
    spacing: TextUnit = TextUnit.Unspecified, lines: Int = 1
) {
    BasicText(
        text = s, modifier = mod,
        style = TextStyle(color = c, fontSize = size, fontWeight = w, fontFamily = FontFamily.Monospace, textAlign = align, letterSpacing = spacing),
        overflow = TextOverflow.Ellipsis, maxLines = lines
    )
}

@Composable
fun Ico(id: Int, c: Color, size: Dp = 24.dp, mod: Modifier = Modifier) {
    Image(painterResource(id), null, mod.size(size), colorFilter = ColorFilter.tint(c))
}

@Composable
fun Title(s: String, p: Pal) {
    Tx(s, p.fg, 28.sp, FontWeight.Black, Modifier.padding(start = 4.dp, top = 12.dp, bottom = 12.dp))
}

fun Modifier.tap(onClick: () -> Unit): Modifier = this.pointerInput(onClick) { detectTapGestures { onClick() } }

fun Modifier.glass(shape: Shape): Modifier {
    val a = S.glass.v
    val e = S.edge.v
    val t = Color(S.tint.v)
    return this.clip(shape)
        .background(
            Brush.linearGradient(listOf(t.copy(alpha = (a * 1.7f).coerceIn(0f, 1f)), t.copy(alpha = (a * .5f).coerceIn(0f, 1f)))),
            shape
        )
        .border(
            1.dp,
            Brush.linearGradient(listOf(Color.White.copy(alpha = e), Color.White.copy(alpha = e * .12f), Color.White.copy(alpha = e * .45f))),
            shape
        )
}

// ---------- Background: blurred accent orbs + optional dot / ring pattern ----------
@Composable
fun NBackground(p: Pal) {
    val tr = rememberInfiniteTransition(label = "bg")
    val t = tr.animateFloat(0f, 1f, infiniteRepeatable(tween(16000, easing = LinearEasing), RepeatMode.Reverse), label = "t")
    Box(Modifier.fillMaxSize().background(p.bg)) {
        Box(Modifier.fillMaxSize().blur(S.blur.v.dp)) {
            Canvas(Modifier.fillMaxSize()) {
                val ph = if (S.anim.v) t.value else .5f
                val w = size.width
                val h = size.height
                val a = S.orbs.v
                fun orb(c: Color, cx: Float, cy: Float, r: Float, al: Float) {
                    drawCircle(Brush.radialGradient(listOf(c.copy(alpha = al.coerceIn(0f, 1f)), Color.Transparent), Offset(cx, cy), r), r, Offset(cx, cy))
                }
                orb(p.acc, w * (.15f + .3f * ph), h * (.30f - .1f * ph), w * .7f, .85f * a)
                orb(lerp(p.acc, Color(0xFF3B6BFF), .6f), w * (.9f - .3f * ph), h * (.78f + .06f * ph), w * .8f, .8f * a)
                orb(p.fg, w * .8f, h * .1f, w * .4f, .12f * a)
            }
        }
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val g = S.dotGap.v.dp.toPx()
            when (S.bg.v) {
                0 -> {
                    var y = g / 2
                    while (y < h) {
                        var x = g / 2
                        while (x < w) {
                            val d = hypot(x - w / 2, y - h * .35f) / (h * .6f)
                            val al = (.22f * (1f - d)).coerceIn(0f, .22f)
                            if (al > 0f) drawCircle(p.fg.copy(alpha = al), 1.3.dp.toPx(), Offset(x, y))
                            x += g
                        }
                        y += g
                    }
                }
                2 -> {
                    var r = g * 1.7f
                    while (r < h * 1.2f) {
                        drawCircle(p.fg.copy(alpha = .07f), r, Offset(w / 2, h * .32f), style = Stroke(1.dp.toPx()))
                        r += g * 1.7f
                    }
                }
                else -> {}
            }
        }
    }
}

// ---------- 5x7 dot-matrix font (the Nothing look, no font files needed) ----------
private fun g(s: String) = s.split("/")
val GLYPHS: Map<Char, List<String>> = mapOf(
    '0' to g("01110/10001/10011/10101/11001/10001/01110"),
    '1' to g("00100/01100/00100/00100/00100/00100/01110"),
    '2' to g("01110/10001/00001/00010/00100/01000/11111"),
    '3' to g("11110/00001/00001/01110/00001/00001/11110"),
    '4' to g("00010/00110/01010/10010/11111/00010/00010"),
    '5' to g("11111/10000/11110/00001/00001/10001/01110"),
    '6' to g("00110/01000/10000/11110/10001/10001/01110"),
    '7' to g("11111/00001/00010/00100/01000/01000/01000"),
    '8' to g("01110/10001/10001/01110/10001/10001/01110"),
    '9' to g("01110/10001/10001/01111/00001/00010/01100"),
    '+' to g("00000/00100/00100/11111/00100/00100/00000"),
    '*' to g("00000/10101/01110/11111/01110/10101/00000"),
    '#' to g("01010/01010/11111/01010/11111/01010/01010"),
    '-' to g("00000/00000/00000/11111/00000/00000/00000"),
    ':' to g("00000/00100/00100/00000/00100/00100/00000"),
    ' ' to g("00000/00000/00000/00000/00000/00000/00000")
)

@Composable
fun DotText(
    text: String, color: Color, modifier: Modifier = Modifier, maxDot: Dp = S.dispDot.v.dp,
    align: Alignment = Alignment.Center, ghost: Boolean = S.ghost.v
) {
    val s = if (text.isEmpty()) " " else text
    val cols = s.length * 6 - 1
    BoxWithConstraints(modifier.fillMaxWidth(), contentAlignment = align) {
        val d = minOf(maxDot, maxWidth / cols)
        Canvas(Modifier.width(d * cols).height(d * 7)) {
            val px = size.width / cols
            val r = px * S.dotSize.v
            val gl = S.glow.v
            val ghostC = color.copy(alpha = .13f)
            s.forEachIndexed { i, ch ->
                val rows = GLYPHS[ch] ?: GLYPHS[' ']!!
                for (y in 0 until 7) for (x in 0 until 5) {
                    val c = Offset((i * 6 + x + .5f) * px, (y + .5f) * px)
                    if (rows[y][x] == '1') {
                        if (gl > 0f) drawCircle(color.copy(alpha = (.26f * gl).coerceIn(0f, 1f)), r * (1.5f + gl), c)
                        drawCircle(color, r, c)
                    } else if (ghost) {
                        drawCircle(ghostC, r * .8f, c)
                    }
                }
            }
        }
    }
}

@Composable
fun GSlider(value: Float, range: ClosedFloatingPointRange<Float>, color: Color, onChange: (Float) -> Unit) {
    var w by remember { mutableFloatStateOf(1f) }
    val span = range.endInclusive - range.start
    val frac = ((value - range.start) / span).coerceIn(0f, 1f)
    Box(
        Modifier.fillMaxWidth().height(32.dp).padding(horizontal = 10.dp).onSizeChanged { w = it.width.toFloat().coerceAtLeast(1f) }
            .pointerInput(range) { detectTapGestures { o -> onChange(range.start + (o.x / w).coerceIn(0f, 1f) * span) } }
            .pointerInput(range) { detectHorizontalDragGestures { ch, _ -> ch.consume(); onChange(range.start + (ch.position.x / w).coerceIn(0f, 1f) * span) } }
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val cy = size.height / 2
            drawLine(color.copy(alpha = .22f), Offset(0f, cy), Offset(size.width, cy), 6.dp.toPx(), StrokeCap.Round)
            drawLine(color, Offset(0f, cy), Offset(size.width * frac, cy), 6.dp.toPx(), StrokeCap.Round)
            drawCircle(Color.White, 10.dp.toPx(), Offset(size.width * frac, cy))
            drawCircle(color, 5.dp.toPx(), Offset(size.width * frac, cy))
        }
    }
}
