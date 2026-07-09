-- ============================================================================
-- 0005 — profiles: rimozione definitiva di last_name e birth_date
-- ----------------------------------------------------------------------------
-- Privacy by design: niente cognome, niente data completa (solo birth_year).
--
-- 1) Azzera i valori legacy (se le colonne esistono ancora).
-- 2) Drop delle colonne: così in Studio non vedi più date complete né cognomi.
--
-- Idempotente e rieseguibile: il blocco DO controlla information_schema prima
-- degli UPDATE; i DROP usano IF EXISTS.
--
-- Da eseguire nel SQL Editor di geoHELP-dev (dopo aver installato l'app che
-- non legge più quelle colonne, es. app-dev-debug_1.0.14+).
-- ============================================================================

do $$
begin
    if exists (
        select 1 from information_schema.columns
        where table_schema = 'public' and table_name = 'profiles' and column_name = 'last_name'
    ) then
        update public.profiles set last_name = null where last_name is not null;
    end if;

    if exists (
        select 1 from information_schema.columns
        where table_schema = 'public' and table_name = 'profiles' and column_name = 'birth_date'
    ) then
        update public.profiles set birth_date = null where birth_date is not null;
    end if;
end $$;

alter table public.profiles drop column if exists last_name;
alter table public.profiles drop column if exists birth_date;
