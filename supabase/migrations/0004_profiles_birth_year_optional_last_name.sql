-- ============================================================================
-- 0004 — profiles: minimizzazione dati (privacy by design)
-- ----------------------------------------------------------------------------
-- Decisione architetturale:
--   * Si chiede solo l'ANNO di nascita (intero), non la data completa.
--   * Il cognome diventa opzionale (resta in DB ma può essere null).
--
-- La vecchia colonna birth_date resta per backward compat dello schema, ma
-- non viene più letta né scritta dall'app. Può essere droppata in futuro
-- una volta verificato che nessun consumer la usa.
--
-- Idempotente. Da eseguire nel SQL Editor di geoHELP-dev.
-- ============================================================================

alter table public.profiles
    add column if not exists birth_year int
        check (birth_year is null or (birth_year between 1900 and 2100));

-- Backfill leggero: se la colonna nuova è null e c'è una birth_date valorizzata,
-- popoliamo birth_year con l'anno corrispondente.
update public.profiles
   set birth_year = extract(year from birth_date)::int
 where birth_year is null
   and birth_date is not null;

-- Nota: NON eliminiamo birth_date per non rompere eventuali backup / dump esistenti.
-- L'app userà solo birth_year da qui in avanti.
