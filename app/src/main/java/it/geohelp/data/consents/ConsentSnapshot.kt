package it.geohelp.data.consents

/** Stato corrente dei tre consensi (ultima riga per tipo/versione su Supabase). */
data class ConsentSnapshot(
    val privacy: Boolean = false,
    val medical: Boolean = false,
    val manDown: Boolean = false,
)
