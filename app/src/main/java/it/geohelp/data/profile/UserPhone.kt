package it.geohelp.data.profile

/** Normalizzazione telefono utente (default Italia +39). */
object UserPhone {

    const val IT_PREFIX = "+39"

    /** Cifre nazionali da valore salvato (es. +393471234567 → 3471234567). */
    fun nationalDigitsFromStored(stored: String?): String {
        val raw = stored?.trim().orEmpty().filter { it.isDigit() }
        if (raw.isEmpty()) return ""
        return when {
            raw.startsWith("39") && raw.length >= 11 -> raw.drop(2)
            raw.startsWith("0039") -> raw.drop(4)
            else -> raw
        }
    }

    /**
     * Converte input utente (solo cifre nazionali) in E.164 Italia.
     * Accetta cellulari 10 cifre (3…) o 9–10 cifre; rifiuta input troppo corti.
     */
    fun toE164Italy(nationalInput: String): String? {
        var digits = nationalInput.filter { it.isDigit() }
        if (digits.startsWith("39") && digits.length >= 11) digits = digits.drop(2)
        if (digits.startsWith("0")) digits = digits.drop(1)
        if (digits.length !in 9..10) return null
        return "$IT_PREFIX$digits"
    }

    fun isValidE164(value: String?): Boolean =
        toE164Italy(nationalDigitsFromStored(value)) != null ||
            (value?.trim()?.matches(Regex("^\\+39[0-9]{9,10}$")) == true)
}
