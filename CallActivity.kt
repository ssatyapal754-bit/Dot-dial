package com.dotdialer

import android.os.Bundle
import android.telecom.Call
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

class CallActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        enableEdgeToEdge()
        setContent { CallScreen { finish() } }
    }
}

@Composable
fun CallScreen(close: () -> Unit) {
    val p = pal()
    val st = CallManager.state
    var pad by remember { mutableStateOf(false) }
    var typed by remember { mutableStateOf("") }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { delay(500); now = System.currentTimeMillis() } }
    LaunchedEffect(CallManager.ended) { if (CallManager.ended) { delay(900); close() } }

    val ringing = st == Call.STATE_RINGING
    val live = st == Call.STATE_ACTIVE || st == Call.STATE_HOLDING
    val secs = if (CallManager.connectedAt > 0) ((now - CallManager.connectedAt) / 1000).coerceAtLeast(0) else 0L
    val clock = "%02d:%02d".format(secs / 60, secs % 60)
    val title = CallManager.name.ifBlank { fmt(CallManager.number).ifBlank { "Unknown" } }
    val tr = rememberInfiniteTransition(label = "ring")
    val pulse = tr.animateFloat(0f, 1f, infiniteRepeatable(tween(1500)), label = "p")

    Box(Modifier.fillMaxSize()) {
        NBackground(p)
        Column(
            Modifier.fillMaxSize().systemBarsPadding().padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(28.dp))
            Box(Modifier.size(150.dp), contentAlignment = Alignment.Center) {
                Canvas(Modifier.fillMaxSize()) {
                    drawCircle(p.line, size.minDimension * .33f, style = Stroke(1.dp.toPx()))
                    if (ringing) {
                        val pu = pulse.value
                        drawCircle(p.acc.copy(alpha = 1f - pu), size.minDimension * (.33f + .17f * pu), style = Stroke(2.dp.toPx()))
                    }
                }
                Tx(title.take(1).uppercase(), p.acc, 44.sp, FontWeight.Black)
            }
            Spacer(Modifier.height(18.dp))
            Tx(title, p.fg, 26.sp, FontWeight.Bold, Modifier.fillMaxWidth(), TextAlign.Center)
            if (CallManager.name.isNotBlank()) {
                Spacer(Modifier.height(10.dp))
                DotText(fmt(CallManager.number), p.fg.copy(alpha = .7f), maxDot = 4.dp, ghost = false)
            }
            Spacer(Modifier.height(18.dp))
            if (st == Call.STATE_ACTIVE) DotText(clock, p.acc, maxDot = 7.dp)
            else Tx(
                when (st) {
                    Call.STATE_RINGING -> "incoming call"
                    Call.STATE_HOLDING -> "on hold"
                    Call.STATE_DISCONNECTED, Call.STATE_DISCONNECTING -> "call ended"
                    else -> "calling…"
                },
                p.acc, 15.sp, FontWeight.Bold, Modifier.fillMaxWidth(), TextAlign.Center
            )

            Spacer(Modifier.weight(1f))

            if (pad && live) {
                DotText(typed.takeLast(14), p.fg, maxDot = 5.dp, ghost = false)
                Spacer(Modifier.height(10.dp))
                listOf("123", "456", "789", "*0#").forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.padding(bottom = 10.dp)) {
                        row.forEach { ch ->
                            Box(
                                Modifier.size(60.dp).glass(CircleShape).tap { typed += ch; CallManager.dtmf(ch); Fb.key(CallManagerCtx.ctx(), ch.toString()) },
                                contentAlignment = Alignment.Center
                            ) { DotText(ch.toString(), p.fg, maxDot = 3.dp, ghost = false) }
                        }
                    }
                }
            }

            if (ringing) {
                Row(Modifier.fillMaxWidth().padding(bottom = 28.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                    BigBtn(Color(0xFFE5322D), true, Color.White) { CallManager.reject() }
                    BigBtn(Color(S.callColor.v), false, Color.Black) { CallManager.answer() }
                }
            } else {
                Row(Modifier.fillMaxWidth().padding(bottom = 20.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                    Toggle("Mute", CallManager.muted, p) { CallManager.toggleMute() }
                    Toggle("Speaker", CallManager.speaker, p) { CallManager.toggleSpeaker() }
                    Toggle("Hold", st == Call.STATE_HOLDING, p) { CallManager.toggleHold() }
                    Toggle("Keypad", pad, p) { pad = !pad }
                }
                Row(Modifier.fillMaxWidth().padding(bottom = 24.dp), horizontalArrangement = Arrangement.Center) {
                    BigBtn(Color(0xFFE5322D), true, Color.White) { CallManager.hangup() }
                }
            }
        }
    }
}

fun CallManager.reject() { hangup() }

@Composable
private fun BigBtn(c: Color, end: Boolean, ic: Color, onClick: () -> Unit) {
    Box(Modifier.size(76.dp).clip(CircleShape).background(c).tap(onClick), contentAlignment = Alignment.Center) {
        Ico(R.drawable.ic_call, ic, 34.dp, if (end) Modifier.rotate(135f) else Modifier)
    }
}

@Composable
private fun Toggle(label: String, on: Boolean, p: Pal, onClick: () -> Unit) {
    val m = if (on) Modifier.size(70.dp).clip(CircleShape).background(p.fg) else Modifier.size(70.dp).glass(CircleShape)
    Box(m.tap(onClick), contentAlignment = Alignment.Center) {
        Tx(label, if (on) p.bg else p.fg, 11.sp, FontWeight.Bold, align = TextAlign.Center)
    }
}

object CallManagerCtx {
    fun ctx(): android.content.Context = CallManager.service ?: Store.appCtx
}
