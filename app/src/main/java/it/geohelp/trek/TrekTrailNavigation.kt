package it.geohelp.trek

import android.content.Context
import android.content.Intent
import it.geohelp.MapActivity

object TrekTrailNavigation {
    fun createMapIntent(
        context: Context,
        latText: String,
        lonText: String,
        language: String,
        userDisplayName: String,
        trail: TrekTrailRow?,
    ): Intent = Intent(context, MapActivity::class.java).apply {
        putExtra(MapActivity.EXTRA_LATITUDE, latText)
        putExtra(MapActivity.EXTRA_LONGITUDE, lonText)
        putExtra(MapActivity.EXTRA_LANGUAGE, language)
        putExtra(MapActivity.EXTRA_DISPLAY_NAME, userDisplayName)
        if (trail != null) {
            putExtra(MapActivity.EXTRA_TRAIL_START_LAT, trail.startLat)
            putExtra(MapActivity.EXTRA_TRAIL_START_LON, trail.startLon)
            putExtra(
                MapActivity.EXTRA_TRAIL_LABEL,
                "${trail.sentieroNome} ${trail.sentieroNumero}".trim(),
            )
            trail.trkAsset?.let { putExtra(MapActivity.EXTRA_TRK_ASSET, it) }
        }
    }
}
