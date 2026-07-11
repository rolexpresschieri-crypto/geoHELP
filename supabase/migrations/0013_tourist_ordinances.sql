-- Ordinanze turistiche per comune (PDF in Storage bucket `ordinances`).
--
-- PREREQUISITO: tabella public.profiles (e resto schema geoHELP).
-- Se vedi errore "relation public.profiles does not exist":
--   1) Esegui UNA volta supabase/schema.sql nel SQL Editor
--   2) Poi le migrazioni 0002 … 0012 (in ordine)
--   3) Infine questo file 0013

do $$
begin
    if not exists (
        select 1 from information_schema.tables
        where table_schema = 'public' and table_name = 'profiles'
    ) then
        raise exception
            'Manca public.profiles. Esegui prima supabase/schema.sql, poi migrazioni 0002-0012.';
    end if;
end $$;

alter table public.profiles
    add column if not exists can_manage_ordinances boolean not null default false;

comment on column public.profiles.can_manage_ordinances is
    'Se true: CRUD ordinanze turistiche da app (Set).';

create table if not exists public.tourist_ordinances (
    id bigint generated always as identity primary key,
    comune text not null,
    title text not null,
    issued_at date not null,
    pdf_storage_path text not null,
    is_active boolean not null default true,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create index if not exists tourist_ordinances_comune_issued_idx
    on public.tourist_ordinances (comune, issued_at desc);

comment on table public.tourist_ordinances is
    'Ordinanze di interesse turistico; PDF in storage bucket ordinances/.';

alter table public.tourist_ordinances enable row level security;

-- Lettura pubblica: solo ordinanze attive.
drop policy if exists "tourist_ordinances_select_public" on public.tourist_ordinances;

create policy "tourist_ordinances_select_public"
    on public.tourist_ordinances for select
    to anon, authenticated
    using (is_active = true);

-- Admin: vede tutte le righe.
drop policy if exists "tourist_ordinances_select_admin" on public.tourist_ordinances;

create policy "tourist_ordinances_select_admin"
    on public.tourist_ordinances for select
    to authenticated
    using (
        exists (
            select 1 from public.profiles p
            where p.id = auth.uid() and p.can_manage_ordinances = true
        )
    );

drop policy if exists "tourist_ordinances_insert_admin" on public.tourist_ordinances;

create policy "tourist_ordinances_insert_admin"
    on public.tourist_ordinances for insert
    to authenticated
    with check (
        exists (
            select 1 from public.profiles p
            where p.id = auth.uid() and p.can_manage_ordinances = true
        )
    );

drop policy if exists "tourist_ordinances_update_admin" on public.tourist_ordinances;

create policy "tourist_ordinances_update_admin"
    on public.tourist_ordinances for update
    to authenticated
    using (
        exists (
            select 1 from public.profiles p
            where p.id = auth.uid() and p.can_manage_ordinances = true
        )
    )
    with check (
        exists (
            select 1 from public.profiles p
            where p.id = auth.uid() and p.can_manage_ordinances = true
        )
    );

drop policy if exists "tourist_ordinances_delete_admin" on public.tourist_ordinances;

create policy "tourist_ordinances_delete_admin"
    on public.tourist_ordinances for delete
    to authenticated
    using (
        exists (
            select 1 from public.profiles p
            where p.id = auth.uid() and p.can_manage_ordinances = true
        )
    );

-- Storage bucket (PDF pubblici in lettura).
insert into storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
values (
    'ordinances',
    'ordinances',
    true,
    10485760,
    array['application/pdf']::text[]
)
on conflict (id) do update set
    public = excluded.public,
    file_size_limit = excluded.file_size_limit,
    allowed_mime_types = excluded.allowed_mime_types;

drop policy if exists "ordinances_storage_public_read" on storage.objects;

create policy "ordinances_storage_public_read"
    on storage.objects for select
    to anon, authenticated
    using (bucket_id = 'ordinances');

drop policy if exists "ordinances_storage_admin_write" on storage.objects;

create policy "ordinances_storage_admin_write"
    on storage.objects for insert
    to authenticated
    with check (
        bucket_id = 'ordinances'
        and exists (
            select 1 from public.profiles p
            where p.id = auth.uid() and p.can_manage_ordinances = true
        )
    );

drop policy if exists "ordinances_storage_admin_update" on storage.objects;

create policy "ordinances_storage_admin_update"
    on storage.objects for update
    to authenticated
    using (
        bucket_id = 'ordinances'
        and exists (
            select 1 from public.profiles p
            where p.id = auth.uid() and p.can_manage_ordinances = true
        )
    );

drop policy if exists "ordinances_storage_admin_delete" on storage.objects;

create policy "ordinances_storage_admin_delete"
    on storage.objects for delete
    to authenticated
    using (
        bucket_id = 'ordinances'
        and exists (
            select 1 from public.profiles p
            where p.id = auth.uid() and p.can_manage_ordinances = true
        )
    );
