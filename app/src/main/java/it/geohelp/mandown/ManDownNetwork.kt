package it.geohelp.mandown

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities

/** Rete utilizzabile per invio Twilio (Wi‑Fi o dati). */
object ManDownNetwork {

    fun hasValidatedInternet(context: Context): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return false
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }
}
