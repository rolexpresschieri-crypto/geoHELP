package it.geohelp.data.medical

import it.geohelp.R

/**
 * Patologie selezionabili: ordine fisso (urgenza). In DB/SMS solo codici separati da virgola.
 */
object MedicalConditionCatalog {

    data class Entry(
        val code: String,
        val labelResId: Int,
    )

    /** Ordine tabella operativa — non riordinare. */
    val ORDERED: List<Entry> = listOf(
        Entry("CA", R.string.medical_cond_ca),
        Entry("PO", R.string.medical_cond_po),
        Entry("NE", R.string.medical_cond_ne),
        Entry("PA", R.string.medical_cond_pa),
        Entry("PB", R.string.medical_cond_pb),
        Entry("DIA", R.string.medical_cond_dia),
        Entry("IA", R.string.medical_cond_ia),
        Entry("IV", R.string.medical_cond_iv),
        Entry("AL", R.string.medical_cond_al),
    )

    private val validCodes: Set<String> = ORDERED.map { it.code }.toSet()

    fun encode(selected: Set<String>): String =
        ORDERED.map { it.code }.filter { selected.contains(it) }.joinToString(",")

    fun decode(stored: String?): Set<String> {
        if (stored.isNullOrBlank()) return emptySet()
        return stored.split(',')
            .map { it.trim().uppercase() }
            .filter { it in validCodes }
            .toSet()
    }

    /** Codici in ordine tabella, per riga SMS (es. PAT: CA,PO,DIA). */
    fun codesForSms(stored: String?): String {
        val selected = decode(stored)
        if (selected.isEmpty()) return ""
        return ORDERED.map { it.code }.filter { selected.contains(it) }.joinToString(",")
    }
}
