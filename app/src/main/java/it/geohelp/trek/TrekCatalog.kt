package it.geohelp.trek

import android.content.Context
import java.util.Locale

object TrekCatalog {
    fun allEntries(context: Context): List<TrekTrailRow> = mergeByComune(
        trails = TrekTrailCatalog.trails(),
        waypoints = TrekWaypointLoader.load(context),
    )

    private fun mergeByComune(
        trails: List<TrekTrailRow>,
        waypoints: List<TrekTrailRow>,
    ): List<TrekTrailRow> {
        val grouped = (trails + waypoints).groupBy { it.comune }
        return grouped.keys.sortedBy { it.lowercase(Locale.getDefault()) }.flatMap { comune ->
            val items = grouped[comune].orEmpty()
            val trailRows = items
                .filter { !it.isWaypoint() }
                .sortedBy { "${it.sentieroNome} ${it.sentieroNumero}".trim().lowercase(Locale.getDefault()) }
            val waypointRows = items
                .filter { it.isWaypoint() }
                .sortedBy { it.sentieroNome.lowercase(Locale.getDefault()) }
            trailRows + waypointRows
        }
    }
}
