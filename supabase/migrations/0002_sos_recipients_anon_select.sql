-- ============================================================================
-- Migrazione 0002 — sos_recipients: lettura permessa anche ad anon
-- ----------------------------------------------------------------------------
-- Da eseguire UNA volta nel SQL Editor del progetto geoHELP-dev.
-- Allinea la policy alla nuova versione di schema.sql.
-- ============================================================================

drop policy if exists "sos_recipients_select_authenticated" on public.sos_recipients;
drop policy if exists "sos_recipients_select_active"        on public.sos_recipients;

create policy "sos_recipients_select_active"
    on public.sos_recipients for select
    to authenticated, anon
    using (active = true);
