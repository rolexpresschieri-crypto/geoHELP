-- Esegui nel SQL Editor Supabase (prod) dopo migration 0013.
-- 1) Carica i PDF nel bucket Storage `ordinances` (es. CESANA/260628.pdf).
-- 2) Abilita admin ordinanze per il tuo account.
-- 3) Inserisci le righe iniziali (modifica i titoli se serve).

update public.profiles
set can_manage_ordinances = true
where email = 'rronco23@gmail.com';

insert into public.tourist_ordinances (comune, title, issued_at, pdf_storage_path, is_active)
values
    ('Cesana', 'Ordinanza 28/06/2026', '2026-06-28', 'CESANA/260628.pdf', true),
    ('Cesana', 'Ordinanza 30/06/2026', '2026-06-30', 'CESANA/260630.pdf', true),
    ('Cesana', 'Ordinanza 02/07/2026', '2026-07-02', 'CESANA/260702.pdf', true),
    ('Sestriere', 'Ordinanza 06/07/2026', '2026-07-06', 'SESTRIERE/260706.pdf', true);
