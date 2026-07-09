-- ============================================================================
-- DEV ONLY — reset completo utenti, dati collegati e sessioni Auth.
-- NON eseguire in produzione.
--
-- Mantiene intatta: public.sos_recipients
-- Rimuove: auth.users (+ cascade su profiles, medical_data, consents)
-- ============================================================================

-- Dipendenze Auth (ordine: token → sessioni → identità → utenti)
delete from auth.refresh_tokens;
delete from auth.sessions;
delete from auth.identities;

-- Utenti Auth: CASCADE su public.profiles, public.medical_data, public.consents
delete from auth.users;

-- Pulizia di sicurezza se restassero righe orfane (FK verso auth.users)
truncate table public.consents restart identity;
truncate table public.medical_data;
truncate table public.profiles;

-- public.sos_recipients: volutamente NON toccata
