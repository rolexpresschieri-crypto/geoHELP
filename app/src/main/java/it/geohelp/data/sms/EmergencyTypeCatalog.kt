package it.geohelp.data.sms

import android.content.Context
import android.content.res.Configuration
import it.geohelp.R
import java.util.Locale

/**
 * Codici stabili per statistiche (non dipendono dalla lingua dell'UI).
 * Per MAN DOWN si usa sempre [MANDOWN].
 */
object EmergencyTypeCatalog {

    data class Entry(
        val code: String,
        val labelResId: Int,
    )

    const val MANDOWN = "mandown"

    val ORDERED: List<Entry> = listOf(
        Entry("incident", R.string.emergency_incident),
        Entry("illness", R.string.emergency_illness),
        Entry("vehicle", R.string.emergency_vehicle),
        Entry("lost", R.string.emergency_lost),
        Entry("weather", R.string.emergency_weather),
        Entry("other", R.string.emergency_other),
    )

    private val byCode: Map<String, Entry> = ORDERED.associateBy { it.code }

    fun isValidManualCode(code: String?): Boolean =
        code != null && code in byCode

    fun labelForCode(context: Context, languageCode: String, code: String?): String {
        val resId = byCode[code]?.labelResId ?: return "-"
        val config = Configuration(context.resources.configuration).apply {
            setLocale(Locale(languageCode))
        }
        return context.createConfigurationContext(config).resources.getString(resId)
    }
}
