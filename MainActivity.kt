package com.dotdialer

import android.Manifest
import android.app.role.RoleManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.ContactsContract
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.animation.core.animateFloatAsState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {
    private val isDef = mutableStateOf(false)
    private val reload = mutableIntStateOf(0)
    private val perms = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { reload.intValue++ }
    private val roleResult = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { refreshRole() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        takeNumber(intent)
        val need = listOf(
            Manifest.permission.CALL_PHONE, Manifest.permission.READ_CONTACTS, Manifest.permission.READ_CALL_LOG,
            Manifest.permission.READ_PHONE_STATE, Manifest.permission.ANSWER_PHONE_CALLS, Manifest.permission.POST_NOTIFICATIONS
        ).filter { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }
        if (need.isNotEmpty()) perms.launch(need.toTypedArray())
        setContent { App(reload.intValue, isDef.value) { askDefault() } }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        takeNumber(intent)
    }

    override fun onResume() {
        super.onResume()
        refreshRole()
        reload.intValue++
    }

    private fun takeNumber(i: Intent?) {
        if (i?.action == Intent.ACTION_DIAL || i?.action == Intent.ACTION_VIEW) {
            i.data?.schemeSpecificPart?.let { if (it.isNotBlank()) Dial.num = it }
        }
    }

    private fun refreshRole() {
        val rm = getSystemService(RoleManager::class.java)
        isDef.value = rm.isRoleHeld(RoleManager.ROLE_DIALER)
    }

    private fun askDefault() {
        val rm = getSystemService(RoleManager::class.java)
        if (rm.isRoleAvailable(RoleManager.ROLE_DIALER) && !rm.isRoleHeld(RoleManager.ROLE_DIALER)) {
            roleResult.launch(rm.createRequestRoleIntent(RoleManager.ROLE_DIALER))
        }
    }
}

@Composable
fun App(reload: Int, isDef: Boolean, askDefault: () -> Unit) {
    val ctx = LocalContext.current
    val p = pal()
    var tab by remember { mutableIntStateOf(0) }
    val contacts = remember(reload) { loadContacts(ctx) }
    val recents = remember(reload, CallManager.tick) { loadRecents(ctx) }

    Box(Modifier.fillMaxSize()) {
        NBackground(p)
        Column(Modifier.fillMaxSize().systemBarsPadding()) {
            if (S.navTop.v) NavBar(p, tab) { tab = it }
            Box(Modifier.weight(1f).fillMaxWidth()) {
                when (tab) {
                    0 -> KeypadTab(p, contacts, isDef, askDefault)
                    1 -> RecentsTab(p, recents) { placeCall(ctx, it) }
                    2 -> ContactsTab(p, contacts) { placeCall(ctx, it) }
                    else -> StyleTab(p)
                }
            }
            if (!S.navTop.v) NavBar(p, tab) { tab = it }
        }
    }
}

@Composable
fun NavBar(p: Pal, tab: Int, onTab: (Int) -> Unit) {
    val icons = listOf(R.drawable.ic_dialpad, R.drawable.ic_history, R.drawable.ic_people, R.drawable.ic_tune)
    Row(Modifier.padding(horizontal = 16.dp, vertical = 10.dp).fillMaxWidth().glass(RoundedCornerShape(30.dp)).padding(6.dp)) {
        icons.forEachIndexed { i, ic ->
            Column(
                Modifier.weight(1f).tap { onTab(i) }.padding(vertical = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Ico(ic, if (tab == i) p.fg else p.mute)
                Box(Modifier.padding(top = 4.dp).size(5.dp).clip(CircleShape).background(if (tab == i) p.acc else Color.Transparent))
            }
        }
    }
}

// ---------------- Keypad ----------------
@Composable
fun GKey(p: Pal, label: String, sub: String, mod: Modifier, onTap: () -> Unit, onLong: (() -> Unit)? = null) {
    val ctx = LocalContext.current
    var down by remember { mutableStateOf(false) }
    val sc by animateFloatAsState(if (down && S.press.v == 0) .9f else 1f, label = "k")
    val shape = RoundedCornerShape(percent = S.round.v.toInt())
    val ring = if (down && S.press.v == 1) Modifier.border(1.5.dp, p.acc, shape) else Modifier
    Box(
        mod.graphicsLayer { scaleX = sc; scaleY = sc }.glass(shape).then(ring)
            .pointerInput(onTap, onLong) {
                detectTapGestures(
                    onPress = { down = true; Fb.key(ctx, label); tryAwaitRelease(); down = false },
                    onTap = { onTap() },
                    onLongPress = if (onLong != null) ({ _: Offset -> onLong() }) else null
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            DotText(label, p.fg, maxDot = (S.numSize.v / 7f).dp, ghost = false)
            if (S.letters.v) Tx(if (sub.isEmpty()) " " else sub, p.mute, 9.sp, spacing = 2.sp)
        }
    }
}

@Composable
fun RoundAct(p: Pal, icon: Int, onTap: () -> Unit, onLong: (() -> Unit)? = null) {
    val ctx = LocalContext.current
    Box(
        Modifier.size(56.dp).glass(CircleShape).pointerInput(onTap, onLong) {
            detectTapGestures(
                onPress = { Fb.key(ctx, "x") },
                onTap = { onTap() },
                onLongPress = if (onLong != null) ({ _: Offset -> onLong() }) else null
            )
        },
        contentAlignment = Alignment.Center
    ) { Ico(icon, p.fg, 22.dp) }
}

@Composable
fun CallBtn(onClick: () -> Unit) {
    val c = Color(S.callColor.v)
    val shape = when (S.callShape.v) { 1 -> RoundedCornerShape(50); 2 -> RoundedCornerShape(24); else -> CircleShape }
    val m = if (S.callShape.v == 1) Modifier.fillMaxWidth().height(64.dp) else Modifier.size(76.dp)
    Box(
        m.clip(shape).background(Brush.linearGradient(listOf(c.copy(alpha = .95f), c.copy(alpha = .72f))))
            .border(1.dp, Color.White.copy(alpha = .5f), shape).tap(onClick),
        contentAlignment = Alignment.Center
    ) { Ico(R.drawable.ic_call, if (c.luminance() > .5f) Color.Black else Color.White, 32.dp) }
}

@Composable
fun KeypadTab(p: Pal, contacts: List<Ct>, isDef: Boolean, askDefault: () -> Unit) {
    val ctx = LocalContext.current
    val num = Dial.num
    val d = digits(num)
    val matches = if (d.isEmpty()) emptyList() else contacts.filter { digits(it.number).contains(d) || t9(it.name).contains(d) }.distinctBy { it.name }.take(3)
    fun add(k: String) {
        if (Dial.num.length < 20) { Dial.num += k }
    }
    val rows = listOf(
        listOf("1" to "", "2" to "ABC", "3" to "DEF"), listOf("4" to "GHI", "5" to "JKL", "6" to "MNO"),
        listOf("7" to "PQRS", "8" to "TUV", "9" to "WXYZ"), listOf("*" to "", "0" to "+", "#" to "")
    )
    val al = when (S.align.v) { 0 -> Alignment.CenterStart; 2 -> Alignment.CenterEnd; else -> Alignment.Center }

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        if (!isDef) {
            Row(
                Modifier.fillMaxWidth().padding(top = 8.dp).glass(RoundedCornerShape(20.dp)).tap(askDefault).padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Tx("Make this your phone app", p.fg, 13.sp, FontWeight.Bold)
                    Tx("Needed for real incoming calls and the lock-screen popup", p.mute, 10.sp, lines = 2)
                }
                Tx("SET", p.acc, 14.sp, FontWeight.Bold)
            }
        }
        Column(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.Bottom) {
            if (S.chips.v) {
                Row(Modifier.fillMaxWidth().height(38.dp), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)) {
                    matches.forEach { c ->
                        Box(Modifier.glass(RoundedCornerShape(50)).tap { Dial.num = c.number }.padding(horizontal = 14.dp, vertical = 8.dp)) {
                            Tx(c.name, p.fg, 12.sp)
                        }
                    }
                }
            }
            Tx(
                if (matches.isNotEmpty()) matches[0].name else if (num.isEmpty()) "Type digits, or a name with the letters" else "",
                p.mute, 12.sp, mod = Modifier.fillMaxWidth().height(20.dp).padding(horizontal = 6.dp),
                align = when (S.align.v) { 0 -> androidx.compose.ui.text.style.TextAlign.Start; 2 -> androidx.compose.ui.text.style.TextAlign.End; else -> androidx.compose.ui.text.style.TextAlign.Center }
            )
            Spacer(Modifier.height(6.dp))
            DotText(fmt(num), p.fg, align = al)
            Spacer(Modifier.height(10.dp))
            if (S.glyphBar.v) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    repeat(14) { i -> Box(Modifier.weight(1f).height(4.dp).clip(RoundedCornerShape(2.dp)).background(if (i < num.length) p.acc else p.line)) }
                }
            }
            Spacer(Modifier.height(14.dp))
        }
        Column(Modifier.fillMaxWidth(S.kw.v), verticalArrangement = Arrangement.spacedBy(S.keyGap.v.dp)) {
            rows.forEach { r ->
                Row(horizontalArrangement = Arrangement.spacedBy(S.keyGap.v.dp)) {
                    r.forEach { (k, sub) ->
                        val ol: (() -> Unit)? = if (k == "0" ) ({ add("+") }) else null
                        GKey(p, k, sub, Modifier.weight(1f).aspectRatio(1f), { add(k) }, ol)
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    RoundAct(p, R.drawable.ic_person_add, { 
                        val i = Intent(ContactsContract.Intents.Insert.ACTION).apply {
                            type = ContactsContract.RawContacts.CONTENT_TYPE
                            putExtra(ContactsContract.Intents.Insert.PHONE, Dial.num)
                        }
                        try { ctx.startActivity(i) } catch (e: Exception) { }
                    })
                }
                Box(Modifier.weight(if (S.callShape.v == 1) 2.4f else 1f), contentAlignment = Alignment.Center) {
                    CallBtn { placeCall(ctx, Dial.num) }
                }
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    if (num.isNotEmpty()) RoundAct(p, R.drawable.ic_backspace, { Dial.num = Dial.num.dropLast(1) }, { Dial.num = "" })
                }
            }
        }
        Spacer(Modifier.height(6.dp))
    }
}

// ---------------- Recents ----------------
@Composable
fun SegPill(opts: List<String>, sel: Int, p: Pal, onSel: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(p.fg.copy(alpha = .08f)).padding(3.dp)) {
        opts.forEachIndexed { i, o ->
            Box(
                Modifier.weight(1f).clip(RoundedCornerShape(11.dp)).background(if (sel == i) p.acc else Color.Transparent).tap { onSel(i) }.padding(vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) { Tx(o, if (sel == i) p.onAcc else p.mute, 11.sp, FontWeight.Bold) }
        }
    }
}

@Composable
fun Avatar(s: String, p: Pal) {
    Box(Modifier.size(42.dp).clip(CircleShape).background(p.fg.copy(alpha = .1f)), contentAlignment = Alignment.Center) {
        Tx(s.ifBlank { "#" }.take(1).uppercase(), p.acc, 17.sp, FontWeight.Black)
    }
}

@Composable
fun RecentsTab(p: Pal, recents: List<Rec>, onCall: (String) -> Unit) {
    var f by remember { mutableIntStateOf(0) }
    val list = recents.filter {
        when (f) { 1 -> it.type == 2; 2 -> it.type == 1; 3 -> it.type == 3 || it.type == 5 || it.type == 6; else -> true }
    }
    val df = remember { SimpleDateFormat("d MMM · HH:mm", Locale.getDefault()) }
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Title("Recents", p)
        SegPill(listOf("All", "Out", "In", "Missed"), f, p) { f = it }
        Spacer(Modifier.height(12.dp))
        if (list.isEmpty()) {
            Tx("No calls to show.\nCalls appear here once this app handles your calls.", p.mute, 13.sp, mod = Modifier.fillMaxWidth().padding(32.dp), align = androidx.compose.ui.text.style.TextAlign.Center, lines = 3)
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                items(list) { r ->
                    val missed = r.type == 3 || r.type == 5 || r.type == 6
                    val kind = when (r.type) { 2 -> "↗ Outgoing"; 1 -> "↙ Incoming"; 4 -> "Voicemail"; else -> "↙ Missed" }
                    Row(
                        Modifier.fillMaxWidth().glass(RoundedCornerShape(22.dp)).tap { onCall(r.number) }.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Avatar(r.name.ifBlank { r.number }, p)
                        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                            Tx(r.name.ifBlank { fmt(r.number).ifBlank { "Unknown" } }, if (missed) p.acc else p.fg, 15.sp, FontWeight.Bold)
                            Tx(kind + " · " + df.format(Date(r.date)) + (if (r.dur > 0) " · " + r.dur + "s" else ""), p.mute, 11.sp)
                        }
                        Ico(R.drawable.ic_call, Color(S.callColor.v), 22.dp)
                    }
                }
            }
        }
    }
}

// ---------------- Contacts ----------------
@Composable
fun ContactsTab(p: Pal, contacts: List<Ct>, onCall: (String) -> Unit) {
    var q by remember { mutableStateOf("") }
    val ctx = LocalContext.current
    val list = contacts.filter { q.isBlank() || it.name.contains(q, true) || digits(it.number).contains(digits(q).ifEmpty { "~" }) }
        .sortedWith(compareByDescending<Ct> { it.starred }.thenBy { it.name.lowercase() })
    Column(Modifier.fillMaxSize().imePadding().padding(horizontal = 16.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) { Title("Contacts", p) }
            RoundAct(p, R.drawable.ic_person_add, {
                try { ctx.startActivity(Intent(ContactsContract.Intents.Insert.ACTION).apply { type = ContactsContract.RawContacts.CONTENT_TYPE }) } catch (e: Exception) { }
            })
        }
        BasicTextField(
            value = q, onValueChange = { q = it }, singleLine = true,
            textStyle = TextStyle(color = p.fg, fontSize = 15.sp, fontFamily = FontFamily.Monospace),
            cursorBrush = SolidColor(p.acc),
            modifier = Modifier.fillMaxWidth().glass(RoundedCornerShape(18.dp)).padding(14.dp),
            decorationBox = { inner -> if (q.isEmpty()) Tx("Search name or number", p.mute, 15.sp); inner() }
        )
        Spacer(Modifier.height(12.dp))
        if (list.isEmpty()) {
            Tx("No contacts found.\nAllow the Contacts permission if this looks wrong.", p.mute, 13.sp, mod = Modifier.fillMaxWidth().padding(32.dp), align = androidx.compose.ui.text.style.TextAlign.Center, lines = 3)
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                items(list) { c ->
                    Row(
                        Modifier.fillMaxWidth().glass(RoundedCornerShape(22.dp)).tap { onCall(c.number) }.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Avatar(c.name, p)
                        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                            Tx((if (c.starred) "★ " else "") + c.name, p.fg, 15.sp, FontWeight.Bold)
                            Tx(c.number, p.mute, 11.sp)
                        }
                        Ico(R.drawable.ic_call, Color(S.callColor.v), 22.dp)
                    }
                }
            }
        }
    }
}
