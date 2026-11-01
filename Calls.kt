package com.dotdialer

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Person
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.ContactsContract
import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.InCallService
import android.telecom.VideoProfile
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** The system binds this service when this app is the default phone app. */
class DialerService : InCallService() {
    override fun onCallAdded(call: Call) {
        super.onCallAdded(call)
        CallManager.add(this, call)
    }

    override fun onCallRemoved(call: Call) {
        super.onCallRemoved(call)
        CallManager.remove(this, call)
    }

    @Suppress("OVERRIDE_DEPRECATION")
    override fun onCallAudioStateChanged(audioState: CallAudioState) {
        CallManager.speaker = audioState.route == CallAudioState.ROUTE_SPEAKER
        CallManager.muted = audioState.isMuted
    }
}

class CallReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        when (i.action) {
            "ANSWER" -> CallManager.answer()
            "DECLINE", "HANGUP" -> CallManager.hangup()
        }
    }
}

object CallManager {
    var current by mutableStateOf<Call?>(null)
    var state by mutableIntStateOf(Call.STATE_DISCONNECTED)
    var connectedAt by mutableStateOf(0L)
    var muted by mutableStateOf(false)
    var speaker by mutableStateOf(false)
    var name by mutableStateOf("")
    var number by mutableStateOf("")
    var ended by mutableStateOf(false)
    var tick by mutableIntStateOf(0)
    var service: InCallService? = null

    private const val NID = 4242
    private val calls = ArrayList<Call>()

    private val cb = object : Call.Callback() {
        override fun onStateChanged(call: Call, s: Int) {
            if (call !== current) return
            state = s
            val svc = service ?: return
            if (s == Call.STATE_ACTIVE) {
                if (connectedAt == 0L) connectedAt = System.currentTimeMillis()
                notifyOngoing(svc)
                openUi(svc)
            } else if (s != Call.STATE_RINGING) {
                if (s == Call.STATE_HOLDING) notifyOngoing(svc)
            }
        }

        override fun onDetailsChanged(call: Call, details: Call.Details) {
            if (call === current) info(call)
        }
    }

    fun add(svc: InCallService, call: Call) {
        service = svc
        calls.add(call)
        call.registerCallback(cb)
        current = call
        ended = false
        connectedAt = 0L
        info(call)
        val s = call.state
        state = s
        if (s == Call.STATE_ACTIVE) connectedAt = System.currentTimeMillis()
        if (s == Call.STATE_RINGING) {
            val nm = svc.getSystemService(NotificationManager::class.java)
            if (nm.areNotificationsEnabled()) notifyIncoming(svc) else openUi(svc)
        } else {
            openUi(svc)
        }
    }

    fun remove(svc: InCallService, call: Call) {
        call.unregisterCallback(cb)
        calls.remove(call)
        svc.getSystemService(NotificationManager::class.java).cancel(NID)
        tick++
        if (call === current) {
            val n = calls.lastOrNull()
            if (n != null) {
                current = n
                state = n.state
                info(n)
            } else {
                state = Call.STATE_DISCONNECTED
                ended = true
            }
        }
    }

    private fun info(call: Call) {
        number = call.details.handle?.schemeSpecificPart ?: ""
        val dn = call.details.callerDisplayName
        name = if (!dn.isNullOrBlank()) dn else (service?.let { lookup(it, number) } ?: "")
    }

    private fun lookup(c: Context, num: String): String {
        if (num.isBlank() || !has(c, Manifest.permission.READ_CONTACTS)) return ""
        try {
            val uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(num))
            c.contentResolver.query(uri, arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME), null, null, null)?.use {
                if (it.moveToFirst()) return it.getString(0) ?: ""
            }
        } catch (e: Exception) { }
        return ""
    }

    private fun label() = name.ifBlank { fmt(number).ifBlank { "Unknown" } }

    private fun openUi(c: Context) {
        try {
            c.startActivity(Intent(c, CallActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: Exception) { }
    }

    private fun channel(c: Context, id: String, title: String, imp: Int): NotificationManager {
        val nm = c.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(id, title, imp).apply {
            setSound(null, null)
            enableVibration(false)
        })
        return nm
    }

    private fun pi(c: Context, code: Int, action: String): PendingIntent =
        PendingIntent.getBroadcast(c, code, Intent(c, CallReceiver::class.java).setAction(action), PendingIntent.FLAG_IMMUTABLE)

    private fun uiIntent(c: Context): PendingIntent =
        PendingIntent.getActivity(c, 0, Intent(c, CallActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

    private fun notifyIncoming(c: Context) {
        val nm = channel(c, "incoming", "Incoming calls", NotificationManager.IMPORTANCE_HIGH)
        val person = Person.Builder().setName(label()).setImportant(true).build()
        val n = Notification.Builder(c, "incoming")
            .setSmallIcon(R.drawable.ic_call)
            .setContentTitle(label())
            .setContentText("Incoming call")
            .setCategory(Notification.CATEGORY_CALL)
            .setOngoing(true)
            .setContentIntent(uiIntent(c))
            .setFullScreenIntent(uiIntent(c), true)
            .setStyle(Notification.CallStyle.forIncomingCall(person, pi(c, 2, "DECLINE"), pi(c, 1, "ANSWER")))
            .build()
        nm.notify(NID, n)
    }

    private fun notifyOngoing(c: Context) {
        val nm = channel(c, "ongoing", "Ongoing call", NotificationManager.IMPORTANCE_LOW)
        val hang = Notification.Action.Builder(Icon.createWithResource(c, R.drawable.ic_call), "Hang up", pi(c, 3, "HANGUP")).build()
        val n = Notification.Builder(c, "ongoing")
            .setSmallIcon(R.drawable.ic_call)
            .setContentTitle(label())
            .setContentText(if (state == Call.STATE_HOLDING) "On hold" else "Ongoing call")
            .setCategory(Notification.CATEGORY_CALL)
            .setOngoing(true)
            .setUsesChronometer(true)
            .setWhen(if (connectedAt > 0) connectedAt else System.currentTimeMillis())
            .setContentIntent(uiIntent(c))
            .addAction(hang)
            .build()
        nm.notify(NID, n)
    }

    // ---- actions ----
    fun answer() { current?.answer(VideoProfile.STATE_AUDIO_ONLY) }
    fun hangup() {
        val c = current ?: return
        if (state == Call.STATE_RINGING) c.reject(false, null) else c.disconnect()
    }
    fun toggleMute() { service?.setMuted(!muted) }
    fun toggleSpeaker() {
        service?.setAudioRoute(if (speaker) CallAudioState.ROUTE_EARPIECE else CallAudioState.ROUTE_SPEAKER)
    }
    fun toggleHold() {
        val c = current ?: return
        if (state == Call.STATE_HOLDING) c.unhold() else c.hold()
    }
    fun dtmf(ch: Char) {
        val c = current ?: return
        c.playDtmfTone(ch)
        Handler(Looper.getMainLooper()).postDelayed({ c.stopDtmfTone() }, 150)
    }
}
