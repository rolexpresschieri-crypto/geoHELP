-- Admin: inserimento e cancellazione destinatari SOS (CRUD completo da app).

drop policy if exists "sos_recipients_insert_admin" on public.sos_recipients;

create policy "sos_recipients_insert_admin"
    on public.sos_recipients for insert
    to authenticated
    with check (
        exists (
            select 1 from public.profiles p
            where p.id = auth.uid() and p.can_manage_sos_recipients = true
        )
    );

drop policy if exists "sos_recipients_delete_admin" on public.sos_recipients;

create policy "sos_recipients_delete_admin"
    on public.sos_recipients for delete
    to authenticated
    using (
        exists (
            select 1 from public.profiles p
            where p.id = auth.uid() and p.can_manage_sos_recipients = true
        )
    );
