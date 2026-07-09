package it.geohelp.data.sms

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SmsEvent(
    val id: Long? = null,
    @SerialName("user_id") val userId: String,
    val channel: String,
    @SerialName("message_kind") val messageKind: String,
    @SerialName("emergency_type") val emergencyType: String,
    val outcome: String,
    @SerialName("dest_count") val destCount: Int? = null,
    @SerialName("segment_count") val segmentCount: Int? = null,
    @SerialName("trace_index") val traceIndex: Int? = null,
    @SerialName("recipient_role") val recipientRole: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
)

object SmsEventKeys {
    const val CHANNEL_MANUAL = "manual"
    const val CHANNEL_MANDOWN = "mandown"

    const val KIND_PREPARED = "prepared"
    const val KIND_INITIAL = "initial"
    const val KIND_TRACE = "trace"
    const val KIND_LOST_SIGNAL = "lost_signal"

    const val OUTCOME_OK = "ok"
    const val OUTCOME_FAILED = "failed"

    const val ROLE_PRIMARY = "primary"
    const val ROLE_BACKUP = "backup"
}
