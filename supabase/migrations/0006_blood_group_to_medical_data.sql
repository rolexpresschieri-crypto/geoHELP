-- ============================================================================
-- 0006 — Sposta blood_group da public.profiles a public.medical_data
-- ----------------------------------------------------------------------------
-- Motivazione: il gruppo sanguigno è un dato sanitario, deve stare con gli
-- altri dati medici (patologie / allergie / terapie), non nell'anagrafica.
--
-- Operazioni (idempotenti):
--   1) Aggiunge medical_data.blood_group (text) con CHECK sui valori ammessi.
--   2) Backfilla i valori esistenti da profiles.blood_group, dove presenti.
--   3) Rimuove la colonna blood_group da profiles.
-- ============================================================================

-- 1) Nuova colonna su medical_data
alter table public.medical_data
    add column if not exists blood_group text;

alter table public.medical_data
    drop constraint if exists medical_data_blood_group_check;

alter table public.medical_data
    add constraint medical_data_blood_group_check
    check (
        blood_group is null
        or blood_group in ('A+','A-','B+','B-','AB+','AB-','0+','0-')
    );

-- 2) Backfill: solo se profiles.blood_group esiste e contiene un valore
do $$
begin
    if exists (
        select 1 from information_schema.columns
        where table_schema = 'public'
          and table_name   = 'profiles'
          and column_name  = 'blood_group'
    ) then
        -- upsert: se l'utente ha già una riga in medical_data, aggiorna blood_group;
        -- altrimenti la crea con solo questo campo valorizzato.
        insert into public.medical_data (user_id, blood_group)
        select p.id, p.blood_group
        from public.profiles p
        where p.blood_group is not null
          and p.blood_group <> ''
        on conflict (user_id) do update
            set blood_group = excluded.blood_group;
    end if;
end$$;

-- 3) Rimuovi la colonna da profiles (PII spostata)
alter table public.profiles
    drop column if exists blood_group;
