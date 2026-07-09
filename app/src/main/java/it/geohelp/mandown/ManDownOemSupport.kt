package it.geohelp.mandown

import android.os.Build
import java.util.Locale

/**
 * Alcuni produttori (es. Vivo, Oppo, Xiaomi) bloccano [SmsManager] da app terze in background.
 * Altri (es. Motorola, Pixel) consentono l'invio automatico diretto.
 */
object ManDownOemSupport {

    private val RESTRICTIVE = listOf(
        "vivo", "iqoo", "oppo", "realme", "oneplus", "xiaomi", "redmi", "poco",
        "huawei", "honor", "meizu", "tecno", "infinix", "itel",
    )

    fun manufacturerLabel(): String =
        "${Build.MANUFACTURER}/${Build.BRAND}"

    /** Solo log/diagnostica: elenco OEM noti come restrittivi (non blocca più l'invio diretto). */
    fun isKnownRestrictiveSmsOem(): Boolean {
        val m = Build.MANUFACTURER.lowercase(Locale.US)
        val b = Build.BRAND.lowercase(Locale.US)
        return RESTRICTIVE.any { token -> m.contains(token) || b.contains(token) }
    }
}
