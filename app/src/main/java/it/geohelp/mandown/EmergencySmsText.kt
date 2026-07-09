package it.geohelp.mandown

import android.content.Context
import android.content.res.Configuration
import it.geohelp.R
import it.geohelp.data.sms.EmergencyTypeCatalog
import java.util.Locale

/** Testo SMS emergenza in formato compatto (meno segmenti Twilio / SIM). */
object EmergencySmsText {

    private const val MED_MAX_CHARS = 88

    fun buildHelpSmsBody(
        context: Context,
        languageCode: String,
        displayName: String,
        birthYear: Int?,
        userPhoneE164: String,
        emergencyCode: String,
        notes: String,
        latitude: String,
        longitude: String,
        altitude: String,
        hasMedicalConsent: Boolean,
        medicalSmsSummary: String,
    ): String {
        val ctx = localizedContext(context, languageCode)
        val request = EmergencyTypeCatalog.labelForCode(context, languageCode, emergencyCode)
            .uppercase(Locale.getDefault())
        val med = medicalLine(hasMedicalConsent, medicalSmsSummary)
        return listOfNotNull(
            ctx.getString(R.string.help_sms_header),
            identityLine(displayName, birthYear, userPhoneE164),
            "R:$request Nt:${notes.trim().ifBlank { "-" }}",
            formatPosition(latitude, longitude, altitude, accuracy = null, speedKmh = null),
            med,
        ).joinToString("\n")
    }

    fun buildManDownSmsBody(
        context: Context,
        languageCode: String,
        displayName: String,
        birthYear: Int?,
        latitude: String,
        longitude: String,
        altitude: String,
        accuracy: String,
        speedKmh: String,
        hasMedicalConsent: Boolean,
        medicalSmsSummary: String,
        userPhoneE164: String = "",
    ): String {
        val ctx = localizedContext(context, languageCode)
        val med = medicalLine(hasMedicalConsent, medicalSmsSummary)
        return listOfNotNull(
            ctx.getString(R.string.mandown_sms_header),
            identityLine(displayName, birthYear, userPhoneE164),
            formatPosition(latitude, longitude, altitude, accuracy, speedKmh),
            med,
        ).joinToString("\n")
    }

    fun buildManDownTrackingSmsBody(
        context: Context,
        languageCode: String,
        displayName: String,
        latitude: String,
        longitude: String,
        altitude: String,
        accuracy: String,
        speedKmh: String,
        traceIndex: Int,
        traceTotal: Int,
        bearing: String,
        distanceFromStart: String,
    ): String {
        val ctx = localizedContext(context, languageCode)
        val header = ctx.getString(R.string.mandown_sms_trace_header, traceIndex, traceTotal)
        return listOfNotNull(
            header,
            "N:${displayName.ifBlank { "-" }}",
            formatPosition(latitude, longitude, altitude, accuracy, speedKmh),
            "Dir:$bearing D:$distanceFromStart",
        ).joinToString("\n")
    }

    fun buildManDownLostSignalSmsBody(
        context: Context,
        languageCode: String,
        userPhoneE164: String = "",
        displayName: String = "",
    ): String {
        val ctx = localizedContext(context, languageCode)
        val id = if (displayName.isNotBlank() || userPhoneE164.isNotBlank()) {
            identityLine(displayName, birthYear = null, userPhoneE164)
        } else {
            null
        }
        return listOfNotNull(
            ctx.getString(R.string.mandown_sms_lost_signal_header),
            id,
            ctx.getString(R.string.mandown_sms_lost_signal_body),
        ).joinToString("\n")
    }

    private fun identityLine(
        displayName: String,
        birthYear: Int?,
        userPhoneE164: String,
    ): String {
        val born = birthYear?.let { " $it" } ?: ""
        val tel = userPhoneE164.trim().takeIf { it.isNotBlank() }?.let { " T:$it" } ?: ""
        return "N:${displayName.ifBlank { "-" }}$born$tel"
    }

    private fun formatPosition(
        latitude: String,
        longitude: String,
        altitude: String,
        accuracy: String?,
        speedKmh: String?,
    ): String {
        val altPart = compactAltitude(altitude)
        val accPart = compactMetric(accuracy, prefix = "acc")
        val speedPart = compactMetric(speedKmh, prefix = "v")
        return "Pos:$latitude,$longitude$altPart$accPart$speedPart"
    }

    private fun compactAltitude(altitude: String): String {
        val t = altitude.trim()
        if (t.isBlank() || t == "---") return ""
        val n = t.replace(" m", "", ignoreCase = true).replace("m", "", ignoreCase = true).trim()
        return if (n.isBlank() || n == "---") "" else " a$n"
    }

    private fun compactMetric(value: String?, prefix: String): String {
        val t = value?.trim().orEmpty()
        if (t.isBlank() || t == "---") return ""
        val n = t.replace(" m", "", ignoreCase = true)
            .replace(" km/h", "", ignoreCase = true)
            .replace("m", "", ignoreCase = true)
            .trim()
        return if (n.isBlank() || n == "---") "" else " $prefix$n"
    }

    private fun medicalLine(hasMedicalConsent: Boolean, medicalSmsSummary: String): String? {
        if (!hasMedicalConsent || medicalSmsSummary.isBlank()) return null
        val compact = medicalSmsSummary.trim().let { raw ->
            if (raw.length <= MED_MAX_CHARS) raw else raw.take(MED_MAX_CHARS - 1) + "…"
        }
        return "Med:$compact"
    }

    private fun localizedContext(context: Context, languageCode: String): Context {
        val locale = Locale(languageCode)
        val config = Configuration(context.resources.configuration)
        config.setLocale(locale)
        return context.createConfigurationContext(config)
    }
}
