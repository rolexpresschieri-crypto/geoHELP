-- ============================================================================
-- 0004 — profiles: minimizzazione dati (privacy by design)
-- ----------------------------------------------------------------------------
-- Decisione architetturale:
--   * Si chiede solo l'ANNO di nascita (intero), non la data completa.
--   * Il cognome diventa opzionale (resta in DB ma può essere null).
--
-- NOTA: se hai già eseguito schema.sql aggiornato, birth_year esiste già e
-- birth_date non c'è: questo script è un no-op sicuro.
--
-- Idempotente. Da eseguire nel SQL Editor di geoHELP-dev.
-- ============================================================================

alter table public.profiles
    add column if not exists birth_year int
        check (birth_year is null or (birth_year between 1900 and 2100));

-- Backfill solo se esiste ancora la colonna legacy birth_date.
do $$
begin
    if exists (
        select 1 from information_schema.columns
        where table_schema = 'public' and table_name = 'profiles' and column_name = 'birth_date'
    ) then
        update public.profiles
           set birth_year = extract(year from birth_date)::int
         where birth_year is null
           and birth_date is not null;
    end if;
end $$;

-- Nota: NON eliminiamo birth_date qui (vedi migration 0005).
