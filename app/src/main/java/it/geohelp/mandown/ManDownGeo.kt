package it.geohelp.mandown

import android.location.Location
import java.util.Locale
import kotlin.math.roundToInt

internal object ManDownGeo {

    fun distanceMeters(from: Location, to: Location): Float = from.distanceTo(to)

    /** Direzione dello spostamento rispetto al punto iniziale (es. NE, 045°). */
    fun formatBearing(from: Location, to: Location): String {
        val deg = ((from.bearingTo(to) + 360f) % 360f).roundToInt()
        val card = when {
            deg < 23 || deg >= 338 -> "N"
            deg < 68 -> "NE"
            deg < 113 -> "E"
            deg < 158 -> "SE"
            deg < 203 -> "S"
            deg < 248 -> "SW"
            deg < 293 -> "W"
            else -> "NW"
        }
        return "$card (${String.format(Locale.US, "%03d", deg)}°)"
    }

    fun formatDistanceM(meters: Float): String =
        String.format(Locale.US, "%.0f m", meters)
}
