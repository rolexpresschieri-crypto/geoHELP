package it.geohelp.data.sms

import android.util.Log
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import it.geohelp.data.supabase.Supabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Eventi SMS per statistiche: nessun testo messaggio, solo metadati.
 */
class SmsEventsRepository {

    private fun currentUserId(): String? =
        Supabase.client.auth.currentUserOrNull()?.id

    suspend fun record(
        channel: String,
        messageKind: String,
        emergencyType: String,
        outcome: String,
        destCount: Int? = null,
        segmentCount: Int? = null,
        traceIndex: Int? = null,
        recipientRole: String? = null,
    ): Unit = withContext(Dispatchers.IO) {
        val uid = currentUserId() ?: return@withContext
        runCatching {
            Supabase.client.from("sms_events").insert(
                SmsEvent(
                    userId = uid,
                    channel = channel,
                    messageKind = messageKind,
                    emergencyType = emergencyType,
                    outcome = outcome,
                    destCount = destCount,
                    segmentCount = segmentCount,
                    traceIndex = traceIndex,
                    recipientRole = recipientRole,
                ),
            )
        }.onFailure {
            Log.w(TAG, "sms_events insert fallito: ${it.message}")
        }
    }

    companion object {
        private const val TAG = "SmsEventsRepository"
    }
}
