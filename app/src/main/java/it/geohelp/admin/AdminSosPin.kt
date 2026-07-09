package it.geohelp.admin

import it.geohelp.BuildConfig

/** Verifica PIN schermata nascosta destinatari SOS (da local.properties, non committato). */
object AdminSosPin {

    fun isConfigured(): Boolean = expectedPin().isNotEmpty()

    fun verify(input: String): Boolean {
        val expected = expectedPin()
        if (expected.isEmpty()) return false
        val a = input.trim().toCharArray()
        val b = expected.toCharArray()
        if (a.size != b.size) return false
        var diff = 0
        for (i in a.indices) {
            diff = diff or (a[i].code xor b[i].code)
        }
        return diff == 0
    }

    private fun expectedPin(): String = BuildConfig.SOS_ADMIN_PIN.trim()
}
