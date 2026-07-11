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
    val kind: TrekEntryKind = TrekEntryKind.TRAIL,
    /** Icona POI in assets, es. trek/wp/CESANA/CESANA.png */
    val wpIconAsset: String? = null,
) {
    fun hasPdf(): Boolean = kind == TrekEntryKind.TRAIL &&
        (!pdfAsset.isNullOrBlank() || !pdfUrl.isNullOrBlank())

    fun isWaypoint(): Boolean = kind == TrekEntryKind.WAYPOINT

    fun trackKey(): String = when {
        isWaypoint() -> wpIconAsset ?: "wp|${comune}|${sentieroNome}|$startLat|$startLon"
        else -> trkAsset ?: "${comune}|${sentieroNome}|${sentieroNumero}"
    }
}
