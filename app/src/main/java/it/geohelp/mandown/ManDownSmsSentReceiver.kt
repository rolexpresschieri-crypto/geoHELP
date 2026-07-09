package it.geohelp.mandown

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/** Esito reale invio SMS dal modem (PendingIntent SENT). */
class ManDownSmsSentReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != ManDownSmsSendCoordinator.ACTION_SMS_SENT_RESULT) return
        val ok = resultCode == Activity.RESULT_OK
        Log.i("ManDownSmsSentRcvr", "SMS SENT resultCode=$resultCode ok=$ok")
        ManDownSmsSendCoordinator.onSentResult(ok)
    }
}
