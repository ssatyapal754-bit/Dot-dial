package com.dotdialer

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun StyleTab(p: Pal) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
        Title("Style", p)
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Nothing red", "Mono mist", "Glyph ice", "Lime terminal", "Sunset").forEach { n ->
                Box(Modifier.glass(RoundedCornerShape(50)).tap { applyPreset(n) }.padding(horizontal = 14.dp, vertical = 9.dp)) { Tx(n, p.fg, 12.sp) }
            }
        }
        val pct = { v: Float -> "%.0f%%".format(v * 100) }
        val px = { v: Float -> "%.0f".format(v) }

        Group("Look", p, true) {
            Seg("Theme", listOf("Auto", "Dark", "Light"), S.theme, p)
            Swatches("Accent colour", S.accent, listOf(0xFFFF2A2A, 0xFFFF8A3D, 0xFFFFD400, 0xFFB6FF3B, 0xFF4DB8FF, 0xFFC084FF), p)
            val hsv = FloatArray(3).also { android.graphics.Color.colorToHSV(S.accent.v, it) }
            Row(Modifier.padding(top = 4.dp)) { Tx("Accent hue", p.mute, 12.sp) }
            GSlider(hsv[0], 0f..360f, p.acc) { S.accent.set(android.graphics.Color.HSVToColor(floatArrayOf(it, .85f, 1f))) }
            Seg("Background", listOf("Dots", "Orbs", "Rings", "Plain"), S.bg, p)
            Slide("Orb strength", S.orbs, 0f..1f, p, pct)
            Slide("Orb softness", S.blur, 0f..100f, p, px)
            Slide("Dot / ring spacing", S.dotGap, 12f..50f, p, px)
            Toggle("Animate background", S.anim, p)
        }
        Group("Glass", p, true) {
            Slide("Frost", S.glass, 0f..0.4f, p, pct)
            Slide("Edge highlight", S.edge, 0f..0.8f, p, pct)
            Swatches("Glass tint", S.tint, listOf(0xFFFFFFFF, 0xFFFFD2B0, 0xFFB8E0FF, 0xFFD8C4FF, 0xFFB6FF3B), p)
        }
        Group("Keys", p, false) {
            Slide("Corner roundness", S.round, 4f..50f, p, px)
            Slide("Keypad width", S.kw, 0.6f..1f, p, pct)
            Slide("Key gap", S.keyGap, 4f..28f, p, px)
            Slide("Number size", S.numSize, 20f..44f, p, px)
            Toggle("Show letters", S.letters, p)
            Seg("Press effect", listOf("Squish", "Glow", "None"), S.press, p)
        }
        Group("Display", p, false) {
            Slide("Dot size", S.dispDot, 4f..11f, p, px)
            Slide("Dot roundness", S.dotSize, 0.2f..0.5f, p, { "%.2f".format(it) })
            Slide("Glow", S.glow, 0f..1f, p, pct)
            Toggle("Ghost dots", S.ghost, p)
            Seg("Align", listOf("Left", "Centre", "Right"), S.align, p)
            Toggle("Glyph light bar", S.glyphBar, p)
            Toggle("Contact suggestions", S.chips, p)
            Toggle("Group digits", S.fmt, p)
        }
        Group("Call key", p, false) {
            Seg("Shape", listOf("Round", "Pill", "Square"), S.callShape, p)
            Swatches("Colour", S.callColor, listOf(0xFF7CFFB2, 0xFFFFFFFF, 0xFFFF2A2A, 0xFF4DB8FF, 0xFFFFD400), p)
        }
        Group("Feedback and layout", p, false) {
            Toggle("Haptics", S.haptics, p)
            Slide("Haptic strength (ms)", S.hapMs, 5f..40f, p, px)
            Seg("Key sound", listOf("Off", "DTMF"), S.sound, p)
            Toggle("Tab bar at top", S.navTop, p)
        }
        Box(Modifier.padding(vertical = 8.dp).glass(RoundedCornerShape(16.dp)).tap { S.all.forEach { it.reset() } }.padding(horizontal = 18.dp, vertical = 12.dp)) {
            Tx("Reset everything", p.acc, 13.sp, FontWeight.Bold)
        }
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun Group(title: String, p: Pal, start: Boolean, content: @Composable () -> Unit) {
    var open by remember { mutableStateOf(start) }
    Column(Modifier.fillMaxWidth().padding(bottom = 10.dp).glass(RoundedCornerShape(24.dp)).padding(horizontal = 16.dp)) {
        Row(Modifier.fillMaxWidth().tap { open = !open }.padding(vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Tx(title, p.fg, 16.sp, FontWeight.Black, Modifier.weight(1f))
            Tx(if (open) "–" else "+", p.acc, 18.sp, FontWeight.Bold)
        }
        if (open) {
            content()
            Spacer(Modifier.height(10.dp))
        }
    }
}

@Composable
private fun Slide(label: String, f: FloatP, range: ClosedFloatingPointRange<Float>, p: Pal, show: (Float) -> String) {
    Column(Modifier.padding(vertical = 4.dp)) {
        Row(Modifier.fillMaxWidth()) {
            Tx(label, p.fg, 13.sp, mod = Modifier.weight(1f))
            Tx(show(f.v), p.mute, 12.sp)
        }
        GSlider(f.v, range, p.acc) { f.set(it) }
    }
}

@Composable
private fun Seg(label: String, opts: List<String>, f: IntP, p: Pal) {
    Column(Modifier.padding(vertical = 6.dp)) {
        Tx(label, p.fg, 13.sp)
        Spacer(Modifier.height(6.dp))
        SegPill(opts, f.v, p) { f.set(it) }
    }
}

@Composable
private fun Toggle(label: String, f: BoolP, p: Pal) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Tx(label, p.fg, 13.sp, mod = Modifier.weight(1f))
        Box(
            Modifier.size(width = 46.dp, height = 26.dp).clip(RoundedCornerShape(50))
                .background(if (f.v) p.acc else p.fg.copy(alpha = .15f)).tap { f.set(!f.v) }
        ) {
            Box(Modifier.offset(x = if (f.v) 23.dp else 3.dp, y = 3.dp).size(20.dp).clip(CircleShape).background(if (f.v) p.onAcc else p.fg))
        }
    }
}

@Composable
private fun Swatches(label: String, f: IntP, colors: List<Long>, p: Pal) {
    Column(Modifier.padding(vertical = 6.dp)) {
        Tx(label, p.fg, 13.sp)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            colors.forEach { c ->
                val col = Color(c.toInt())
                val sel = f.v == c.toInt()
                Box(
                    Modifier.size(30.dp).clip(CircleShape).background(col)
                        .border(if (sel) 2.5.dp else 1.dp, if (sel) p.fg else p.line, CircleShape).tap { f.set(c.toInt()) }
                )
            }
        }
    }
}
