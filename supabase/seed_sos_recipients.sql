-- ============================================================================
-- Seed di esempio per public.sos_recipients
-- ----------------------------------------------------------------------------
-- Sostituisci i numeri qui sotto con quelli reali presi dal Google Sheet
-- attualmente in uso, poi esegui questo file nel SQL Editor di geoHELP-dev.
-- ============================================================================

insert into public.sos_recipients (label, phone, role, active, sort_order)
values
    ('Primario 1', '+393467227139', 'primary', true, 1),
    ('Primario 2', '+393517789490', 'primary', true, 2),
    ('Backup',     '+393403317088', 'backup',  true, 10)
on conflict do nothing;
