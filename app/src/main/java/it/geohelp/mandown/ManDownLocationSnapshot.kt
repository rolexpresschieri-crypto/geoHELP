package it.geohelp.mandown

import android.location.Location
import java.util.Locale

/** Posizione e metriche GPS per SMS MAN DOWN (aggiornate durante il countdown). */
data class ManDownLocationSnapshot(
    val latitude: String,
    val longitude: String,
    val altitude: String,
    val accuracy: String,
    val speedKmh: String,
) {
    companion object {
        val EMPTY = ManDownLocationSnapshot("---", "---", "---", "---", "---")

        fun fromLocation(loc: Location): ManDownLocationSnapshot = ManDownLocationSnapshot(
            latitude = String.format(Locale.US, "%.5f", loc.latitude),
            longitude = String.format(Locale.US, "%.5f", loc.longitude),
            altitude = if (loc.hasAltitude()) {
                String.format(Locale.US, "%.0f m", loc.altitude)
            } else {
                "---"
            },
            accuracy = if (loc.hasAccuracy()) {
                String.format(Locale.US, "%.0f m", loc.accuracy)
            } else {
                "---"
            },
            speedKmh = if (loc.hasSpeed() && loc.speed >= 0f) {
                String.format(Locale.US, "%.1f km/h", loc.speed * 3.6f)
            } else {
                "---"
            },
        )
    }
}

/** Aggiorna traccia durante countdown: posizione più recente, miglior accuracy, velocità massima. */
internal class ManDownLocationTracker {
    private var latest: Location? = null
    private var maxSpeedMs: Float = 0f
    private var bestAccuracyM: Float = Float.MAX_VALUE
    private var sawSpeed = false
    private var sawAccuracy = false

    fun reset() {
        latest = null
        maxSpeedMs = 0f
        bestAccuracyM = Float.MAX_VALUE
        sawSpeed = false
        sawAccuracy = false
    }

    fun ingest(loc: Location) {
        val prev = latest
        if (prev == null || loc.time >= prev.time) {
            latest = loc
        }
        if (loc.hasSpeed() && loc.speed >= 0f) {
            sawSpeed = true
            if (loc.speed > maxSpeedMs) maxSpeedMs = loc.speed
        }
        if (loc.hasAccuracy() && loc.accuracy > 0f) {
            sawAccuracy = true
            if (loc.accuracy < bestAccuracyM) bestAccuracyM = loc.accuracy
        }
    }

    fun peakSpeedMs(): Float = if (sawSpeed) maxSpeedMs else 0f

    fun snapshot(): ManDownLocationSnapshot {
        val loc = latest ?: return ManDownLocationSnapshot.EMPTY
        val base = ManDownLocationSnapshot.fromLocation(loc)
        val acc = if (sawAccuracy) {
            String.format(Locale.US, "%.0f m", bestAccuracyM)
        } else {
            base.accuracy
        }
        val spd = if (sawSpeed) {
            String.format(Locale.US, "%.1f km/h", maxSpeedMs * 3.6f)
        } else {
            base.speedKmh
        }
        return base.copy(accuracy = acc, speedKmh = spd)
    }

    /** Ultimo fix GPS (lat/lon/velocità istantanea) — per SMS traccia 1/4…4/4. */
    fun snapshotInstant(): ManDownLocationSnapshot {
        val loc = latest ?: return ManDownLocationSnapshot.EMPTY
        return ManDownLocationSnapshot.fromLocation(loc)
    }

    fun latestLocation(): Location? = latest
}
