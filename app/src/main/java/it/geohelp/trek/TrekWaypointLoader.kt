package it.geohelp.trek

import android.content.Context
import java.util.Locale

/**
 * Carica i P.O.I. da `assets/trek/wp/<COMUNE>/<nome>.wp` (PNG omonimo opzionale):
 * ```
 * 44.956944 6.794722
 * Nome visualizzato
 * Breve descrizione (opzionale)
 * ```
 */
object TrekWaypointLoader {
    fun load(context: Context): List<TrekTrailRow> {
        val assetManager = context.assets
        val comuneFolders = assetManager.list("trek/wp")?.toList().orEmpty()
        val rows = mutableListOf<TrekTrailRow>()

        for (folder in comuneFolders) {
            val files = assetManager.list("trek/wp/$folder")?.toList().orEmpty()
            for (file in files) {
                if (!file.endsWith(".wp", ignoreCase = true)) continue
                val baseName = file.substringBeforeLast('.')
                val wpPath = "trek/wp/$folder/$file"
                val meta = readSidecar(assetManager, wpPath) ?: continue
                val pngAsset = files.firstOrNull { it.equals("$baseName.png", ignoreCase = true) }
                    ?.let { "trek/wp/$folder/$it" }
                rows += TrekTrailRow(
                    comune = folderToComuneLabel(folder),
                    sentieroNome = meta.displayName,
                    sentieroNumero = "",
                    breveDescrizione = meta.description,
                    startLat = meta.lat,
                    startLon = meta.lon,
                    kind = TrekEntryKind.WAYPOINT,
                    wpIconAsset = pngAsset,
                )
            }
        }

        return rows.sortedWith(
            compareBy<TrekTrailRow> { it.comune.lowercase(Locale.getDefault()) }
                .thenBy { it.sentieroNome.lowercase(Locale.getDefault()) },
        )
    }

    private data class WaypointMeta(
        val lat: Double,
        val lon: Double,
        val displayName: String,
        val description: String,
    )

    private fun readSidecar(
        assetManager: android.content.res.AssetManager,
        assetPath: String,
    ): WaypointMeta? = runCatching {
        assetManager.open(assetPath).bufferedReader().use { reader ->
            val lines = reader.readLines().map { it.trim() }.filter { it.isNotEmpty() }
            if (lines.size < 2) return@runCatching null
            val coords = lines[0].split(Regex("\\s+"))
            if (coords.size < 2) return@runCatching null
            val lat = coords[0].replace(',', '.').toDouble()
            val lon = coords[1].replace(',', '.').toDouble()
            WaypointMeta(
                lat = lat,
                lon = lon,
                displayName = lines[1],
                description = lines.getOrElse(2) { "" },
            )
        }
    }.getOrNull()

    private fun folderToComuneLabel(folder: String): String {
        val normalized = folder.trim().lowercase(Locale.getDefault())
        return when (normalized) {
            "cesana" -> "Cesana"
            "sestriere" -> "Sestriere"
            else -> folder.lowercase(Locale.getDefault()).replaceFirstChar { ch ->
                if (ch.isLowerCase()) ch.titlecase(Locale.getDefault()) else ch.toString()
            }
        }
    }
}
