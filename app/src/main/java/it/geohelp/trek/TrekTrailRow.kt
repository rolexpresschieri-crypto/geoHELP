package it.geohelp.trek

data class TrekTrailRow(
    val comune: String,
    val sentieroNome: String,
    val sentieroNumero: String,
    val breveDescrizione: String,
    /** Percorso in assets, es. trek/pdf/SESTRIERE/5_sentiero_marmotte.pdf */
    val pdfAsset: String? = null,
    /** URL web opzionale (alternativa a pdfAsset). */
    val pdfUrl: String? = null,
    /** Percorso .trk in assets, es. trek/trk/SESTRIERE/5_sentiero_marmotte.trk */
    val trkAsset: String? = null,
    val startLat: Double,
    val startLon: Double,
) {
    fun hasPdf(): Boolean = !pdfAsset.isNullOrBlank() || !pdfUrl.isNullOrBlank()

    fun trackKey(): String = trkAsset ?: "${comune}|${sentieroNome}|${sentieroNumero}"
}
