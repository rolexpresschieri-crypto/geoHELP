package it.geohelp.data.supabase

import java.util.Locale

fun normalizeOrdinanceStoragePath(input: String): String =
    input.uppercase(Locale.ROOT)

/** Path finale per Storage/URL: aggiunge `.PDF` se manca estensione; non cambia maiuscole dell'estensione. */
fun finalizeOrdinanceStoragePath(input: String): String {
    val path = input.trim().trimStart('/').uppercase(Locale.ROOT)
    if (path.isBlank()) return path
    val fileName = path.substringAfterLast('/')
    if ('.' !in fileName) {
        return "$path.PDF"
    }
    return path
}
