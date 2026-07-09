package it.geohelp.trek

import android.content.Context

private const val PREFS_NAME = "geohelp_prefs"
private const val KEY_ACTIVE_TRAIL = "geohelp.active_trail_key"

object TrekTrailSelectionStore {
    fun loadActiveTrailKey(context: Context): String? =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_ACTIVE_TRAIL, null)
            ?.takeIf { it.isNotBlank() }

    fun saveActiveTrailKey(context: Context, trailKey: String?) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_ACTIVE_TRAIL, trailKey)
            .apply()
    }

    fun findTrail(rows: List<TrekTrailRow>, trailKey: String?): TrekTrailRow? =
        trailKey?.let { key -> rows.firstOrNull { it.trackKey() == key } }
}
