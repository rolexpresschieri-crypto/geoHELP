-- Gestione destinatari SOS da app (solo utenti con flag admin su profiles).

alter table public.profiles
    add column if not exists can_manage_sos_recipients boolean not null default false;

comment on column public.profiles.can_manage_sos_recipients is
    'Se true: lettura di tutti sos_recipients (anche inactive) e update del campo active.';

-- Admin: vede tutte le righe (anche active = false).
drop policy if exists "sos_recipients_select_admin" on public.sos_recipients;

create policy "sos_recipients_select_admin"
    on public.sos_recipients for select
    to authenticated
    using (
        exists (
            select 1 from public.profiles p
            where p.id = auth.uid() and p.can_manage_sos_recipients = true
        )
    );

-- Admin: può aggiornare le righe (in pratica l'app modifica solo active).
drop policy if exists "sos_recipients_update_admin" on public.sos_recipients;

create policy "sos_recipients_update_admin"
    on public.sos_recipients for update
    to authenticated
    using (
        exists (
            select 1 from public.profiles p
            where p.id = auth.uid() and p.can_manage_sos_recipients = true
        )
    )
    with check (
        exists (
            select 1 from public.profiles p
            where p.id = auth.uid() and p.can_manage_sos_recipients = true
        )
    );
