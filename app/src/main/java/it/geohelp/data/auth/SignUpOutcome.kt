package it.geohelp.data.auth

/** Esito registrazione email/password (Supabase può non lanciare errore se l'email esiste già). */
enum class SignUpOutcome {
    /** Sessione attiva subito (conferma email disattivata in Supabase). */
    LOGGED_IN,

    /** Nuovo account: attendere link di conferma. */
    CONFIRM_EMAIL,

    /** Email già in auth.users (identities vuote nella risposta signUp). */
    EMAIL_ALREADY_EXISTS,
}
