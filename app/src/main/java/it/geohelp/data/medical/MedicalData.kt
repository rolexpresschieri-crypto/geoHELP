package it.geohelp.data.medical

/**
 * Modello in-memory dei dati medici letti/scritti su Supabase (colonne strutturate).
 * Il salvataggio avviene in chiaro a livello DB (vedi MedicalDataRepository).
 */
data class MedicalData(
    val conditions: String = "",
    val pacemaker: Boolean = false,
    /** Gruppo sanguigno: uno di A+, A-, B+, B-, AB+, AB-, 0+, 0- oppure null. */
    val bloodGroup: String? = null,
    val allergies: String = "",
    val therapies: String = "",
    val notes: String = "",
) {
    /** Sintesi compatta per le SMS di emergenza. Vuota se nessun campo è valorizzato. */
    fun toSmsSummary(): String = buildList {
        if (pacemaker) add("PACEMAKER")
        if (!bloodGroup.isNullOrBlank()) add("GRP: ${bloodGroup}")
        val pat = MedicalConditionCatalog.codesForSms(conditions)
        if (pat.isNotBlank()) add("PAT: $pat")
        if (allergies.isNotBlank()) add("ALL: ${allergies.trim()}")
        if (therapies.isNotBlank()) add("TER: ${therapies.trim()}")
        if (notes.isNotBlank()) add("NOT: ${notes.trim()}")
    }.joinToString(" | ")

    companion object {
        /** Valori ammessi per il gruppo sanguigno (in convenzione italiana: 0 al posto di O). */
        val BLOOD_GROUPS = listOf("A+", "A-", "B+", "B-", "AB+", "AB-", "0+", "0-")
        val EMPTY = MedicalData()
    }
}
