package it.geohelp.mandown

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.telephony.SmsManager
import android.util.Log
import java.util.ArrayList

/**
 * Attende esito reale SMS ([PendingIntent] SENT), non solo assenza eccezioni.
 */
object ManDownSmsSendCoordinator {

    private const val TAG = "ManDownSmsSend"
    const val ACTION_SMS_SENT_RESULT = "it.geohelp.mandown.SMS_SENT_RESULT"

    private val mainHandler = Handler(Looper.getMainLooper())
    private var pendingResults = 0
    private var failedResults = 0
    private var onFinished: ((Boolean) -> Unit)? = null
    private var timeoutRunnable: Runnable? = null

    fun sendAndAwaitResults(
        context: Context,
        destinations: List<String>,
        parts: ArrayList<String>,
        obtainManager: () -> SmsManager,
        timeoutMs: Long = 12_000L,
        onFinished: (Boolean) -> Unit,
    ) {
        reset()
        this.onFinished = onFinished
        if (destinations.isEmpty()) {
            mainHandler.post { complete(false) }
            return
        }
        pendingResults = destinations.size
        var launched = 0
        destinations.forEachIndexed { index, dest ->
            if (sendOne(context, dest, parts, index, obtainManager)) {
                launched++
            } else {
                failedResults++
                pendingResults--
            }
        }
        if (launched == 0) {
            mainHandler.post { complete(false) }
            return
        }
        if (pendingResults <= 0) {
            mainHandler.post { complete(failedResults == 0) }
            return
        }
        val timeout = Runnable {
            Log.w(TAG, "SENT timeout — nessuna conferma modem")
            pendingResults = 0
            complete(false)
        }
        timeoutRunnable = timeout
        mainHandler.postDelayed(timeout, timeoutMs)
    }

    fun onSentResult(success: Boolean) {
        mainHandler.post {
            if (onFinished == null) return@post
            pendingResults--
            if (!success) failedResults++
            Log.i(TAG, "SENT callback success=$success pending=$pendingResults failed=$failedResults")
            if (pendingResults <= 0) {
                timeoutRunnable?.let { mainHandler.removeCallbacks(it) }
                timeoutRunnable = null
                complete(failedResults == 0)
            }
        }
    }

    private fun sendOne(
        context: Context,
        dest: String,
        parts: ArrayList<String>,
        destIndex: Int,
        obtainManager: () -> SmsManager,
    ): Boolean {
        val mgr = obtainManager()
        return if (parts.size == 1) {
            val sentIntent = pendingSent(context, destIndex, 0)
            runCatching {
                mgr.sendTextMessage(dest, null, parts[0], sentIntent, null)
            }.isSuccess
        } else {
            val sentIntents = ArrayList<PendingIntent>(parts.size)
            repeat(parts.size) { i ->
                sentIntents.add(pendingSent(context, destIndex, i))
            }
            runCatching {
                mgr.sendMultipartTextMessage(dest, null, parts, sentIntents, null)
            }.isSuccess
        }
    }

    private fun pendingSent(context: Context, destIndex: Int, partIndex: Int): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            9300 + destIndex * 16 + partIndex,
            Intent(ACTION_SMS_SENT_RESULT).setPackage(context.packageName),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private fun complete(allOk: Boolean) {
        val cb = onFinished ?: return
        reset()
        cb.invoke(allOk)
    }

    private fun reset() {
        timeoutRunnable?.let { mainHandler.removeCallbacks(it) }
        timeoutRunnable = null
        onFinished = null
        pendingResults = 0
        failedResults = 0
    }
}
