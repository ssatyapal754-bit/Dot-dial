package com.dotdialer

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioManager
import android.media.ToneGenerator
import android.net.Uri
import android.os.Bundle
import android.os.VibrationEffect
import android.os.VibratorManager
import android.provider.CallLog
import android.provider.CallLog.Calls
import android.provider.ContactsContract
import android.provider.ContactsContract.CommonDataKinds.Phone
import android.telecom.TelecomManager
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

object Dial { var num by mutableStateOf("") }

data class Ct(val name: String, val number: String, val starred: Boolean)
data class Rec(val number: String, val name: String, val type: Int, val date: Long, val dur: Long)

fun digits(s: String) = s.filter { it.isDigit() }

fun t9(s: String): String {
    val m = "22233344455566677778889999"
    return s.lowercase().filter { it in 'a'..'z' }.map { m[it - 'a'] }.joinToString("")
}

fun fmt(n: String): String {
    if (!S.fmt.v) return n
    val a = Regex("^(\\+\\d{1,3})(\\d{5})(\\d{1,5})$").find(n)
    if (a != null) return a.groupValues[1] + " " + a.groupValues[2] + " " + a.groupValues[3]
    val b = Regex("^(\\d{5})(\\d{1,5})$").find(n)
    return if (b != null) b.groupValues[1] + " " + b.groupValues[2] else n
}

fun has(c: Context, p: String) = c.checkSelfPermission(p) == PackageManager.PERMISSION_GRANTED

fun loadContacts(c: Context): List<Ct> {
    if (!has(c, Manifest.permission.READ_CONTACTS)) return emptyList()
    val out = ArrayList<Ct>()
    val seen = HashSet<String>()
    try {
        c.contentResolver.query(
            Phone.CONTENT_URI, arrayOf(Phone.DISPLAY_NAME, Phone.NUMBER, Phone.STARRED), null, null,
            Phone.DISPLAY_NAME + " COLLATE NOCASE ASC"
        )?.use { cur ->
            while (cur.moveToNext()) {
                val name = cur.getString(0) ?: ""
                val num = cur.getString(1) ?: ""
                if (name.isNotBlank() && seen.add(name + digits(num))) out.add(Ct(name, num, cur.getInt(2) == 1))
            }
        }
    } catch (e: Exception) { }
    return out
}

fun loadRecents(c: Context): List<Rec> {
    if (!has(c, Manifest.permission.READ_CALL_LOG)) return emptyList()
    val out = ArrayList<Rec>()
    try {
        c.contentResolver.query(
            Calls.CONTENT_URI, arrayOf(Calls.NUMBER, Calls.CACHED_NAME, Calls.TYPE, Calls.DATE, Calls.DURATION),
            null, null, Calls.DATE + " DESC"
        )?.use { cur ->
            var n = 0
            while (cur.moveToNext() && n < 150) {
                out.add(Rec(cur.getString(0) ?: "", cur.getString(1) ?: "", cur.getInt(2), cur.getLong(3), cur.getLong(4)))
                n++
            }
        }
    } catch (e: Exception) { }
    return out
}

fun placeCall(c: Context, n: String) {
    val num = n.trim()
    if (num.isEmpty()) return
    if (!has(c, Manifest.permission.CALL_PHONE)) {
        Toast.makeText(c, "Allow the Phone permission to place calls", Toast.LENGTH_SHORT).show()
        return
    }
    try {
        c.getSystemService(TelecomManager::class.java).placeCall(Uri.fromParts("tel", num, null), Bundle())
        Dial.num = ""
    } catch (e: Exception) {
        Toast.makeText(c, "Couldn't place the call", Toast.LENGTH_SHORT).show()
    }
}

object Fb {
    private var tg: ToneGenerator? = null
    private val codes = intArrayOf(
        ToneGenerator.TONE_DTMF_0, ToneGenerator.TONE_DTMF_1, ToneGenerator.TONE_DTMF_2, ToneGenerator.TONE_DTMF_3,
        ToneGenerator.TONE_DTMF_4, ToneGenerator.TONE_DTMF_5, ToneGenerator.TONE_DTMF_6, ToneGenerator.TONE_DTMF_7,
        ToneGenerator.TONE_DTMF_8, ToneGenerator.TONE_DTMF_9
    )

    fun key(c: Context, k: String) {
        if (S.haptics.v) buzz(c, S.hapMs.v.toLong())
        if (S.sound.v == 1) tone(k)
    }

    fun buzz(c: Context, ms: Long) {
        try {
            c.getSystemService(VibratorManager::class.java).defaultVibrator
                .vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE))
        } catch (e: Exception) { }
    }

    private fun tone(k: String) {
        try {
            val t = tg ?: ToneGenerator(AudioManager.STREAM_DTMF, 60).also { tg = it }
            val code = when {
                k == "*" -> ToneGenerator.TONE_DTMF_S
                k == "#" -> ToneGenerator.TONE_DTMF_P
                k.length == 1 && k[0].isDigit() -> codes[k[0] - '0']
                else -> return
            }
            t.startTone(code, 120)
        } catch (e: Exception) { }
    }
}
