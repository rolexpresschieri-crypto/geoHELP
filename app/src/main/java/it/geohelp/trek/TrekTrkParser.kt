package it.geohelp.trek

import android.content.Context
import org.osmdroid.util.GeoPoint

data class ParsedTrk(
    val title: String?,
    val points: List<GeoPoint>,
)

object TrekTrkParser {
    private val pointPattern = Regex("""T\s+A\s+([\d.]+)ºN\s+([\d.]+)ºE""")

    fun parse(content: String): ParsedTrk {
        var title: String? = null
        val points = mutableListOf<GeoPoint>()
        content.lineSequence().forEach { line ->
            when {
                line.startsWith("M  ") && title == null -> {
                    title = line.removePrefix("M  ").trim()
                }
                pointPattern.containsMatchIn(line) -> {
                    val match = pointPattern.find(line) ?: return@forEach
                    val lat = match.groupValues[1].toDoubleOrNull() ?: return@forEach
                    val lon = match.groupValues[2].toDoubleOrNull() ?: return@forEach
                    points.add(GeoPoint(lat, lon))
                }
            }
        }
        return ParsedTrk(title = title, points = points)
    }

    fun parseAsset(context: Context, assetPath: String): ParsedTrk {
        val content = context.assets.open(assetPath).bufferedReader().use { it.readText() }
        return parse(content)
    }
}
