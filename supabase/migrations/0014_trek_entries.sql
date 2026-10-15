-- Catalogo sentieri / P.O.I. remoto (PDF, TRK, icone WP in Storage bucket `trails`).
--
-- PREREQUISITO: public.profiles (schema geoHELP).
-- Dopo questo file: Storage → bucket `trails` (creato qui) → carica PDF/TRK/PNG.

do $$
begin
    if not exists (
        select 1 from information_schema.tables
        where table_schema = 'public' and table_name = 'profiles'
    ) then
        raise exception
            'Manca public.profiles. Esegui prima supabase/schema.sql, poi le migrazioni precedenti.';
    end if;
end $$;

alter table public.profiles
    add column if not exists can_manage_trails boolean not null default false;

comment on column public.profiles.can_manage_trails is
    'Se true: CRUD sentieri e P.O.I. da app (Set).';

-- Chi già gestisce le ordinanze può gestire anche i sentieri.
update public.profiles
set can_manage_trails = true
where can_manage_ordinances = true
  and can_manage_trails = false;

create table if not exists public.trek_entries (
    id bigint generated always as identity primary key,
    kind text not null check (kind in ('trail', 'waypoint')),
    comune text not null,
    name text not null,
    number text not null default '',
    description text not null default '',
    start_lat double precision not null,
    start_lon double precision not null,
    pdf_storage_path text,
    trk_storage_path text,
    icon_storage_path text,
    is_active boolean not null default true,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create index if not exists trek_entries_comune_kind_idx
    on public.trek_entries (comune, kind, name);

comment on table public.trek_entries is
    'Sentieri (PDF+TRK) e P.O.I./waypoint; file in storage bucket trails/.';

alter table public.trek_entries enable row level security;

drop policy if exists "trek_entries_select_public" on public.trek_entries;
create policy "trek_entries_select_public"
    on public.trek_entries for select
    to anon, authenticated
    using (is_active = true);

drop policy if exists "trek_entries_select_admin" on public.trek_entries;
create policy "trek_entries_select_admin"
    on public.trek_entries for select
    to authenticated
    using (
        exists (
            select 1 from public.profiles p
            where p.id = auth.uid() and p.can_manage_trails = true
        )
    );

drop policy if exists "trek_entries_insert_admin" on public.trek_entries;
create policy "trek_entries_insert_admin"
    on public.trek_entries for insert
    to authenticated
    with check (
        exists (
            select 1 from public.profiles p
            where p.id = auth.uid() and p.can_manage_trails = true
        )
    );

drop policy if exists "trek_entries_update_admin" on public.trek_entries;
create policy "trek_entries_update_admin"
    on public.trek_entries for update
    to authenticated
    using (
        exists (
            select 1 from public.profiles p
            where p.id = auth.uid() and p.can_manage_trails = true
        )
    )
    with check (
        exists (
            select 1 from public.profiles p
            where p.id = auth.uid() and p.can_manage_trails = true
        )
    );

drop policy if exists "trek_entries_delete_admin" on public.trek_entries;
create policy "trek_entries_delete_admin"
    on public.trek_entries for delete
    to authenticated
    using (
        exists (
            select 1 from public.profiles p
            where p.id = auth.uid() and p.can_manage_trails = true
        )
    );

insert into storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
values (
    'trails',
    'trails',
    true,
    20971520,
    array[
        'application/pdf',
        'application/octet-stream',
        'text/plain',
        'image/png',
        'image/jpeg'
    ]::text[]
)
on conflict (id) do update set
    public = excluded.public,
    file_size_limit = excluded.file_size_limit,
    allowed_mime_types = excluded.allowed_mime_types;

drop policy if exists "trails_storage_public_read" on storage.objects;
create policy "trails_storage_public_read"
    on storage.objects for select
    to anon, authenticated
    using (bucket_id = 'trails');

drop policy if exists "trails_storage_admin_write" on storage.objects;
create policy "trails_storage_admin_write"
    on storage.objects for insert
    to authenticated
    with check (
        bucket_id = 'trails'
        and exists (
            select 1 from public.profiles p
            where p.id = auth.uid() and p.can_manage_trails = true
        )
    );

drop policy if exists "trails_storage_admin_update" on storage.objects;
create policy "trails_storage_admin_update"
    on storage.objects for update
    to authenticated
    using (
        bucket_id = 'trails'
        and exists (
            select 1 from public.profiles p
            where p.id = auth.uid() and p.can_manage_trails = true
        )
    );

drop policy if exists "trails_storage_admin_delete" on storage.objects;
create policy "trails_storage_admin_delete"
    on storage.objects for delete
    to authenticated
    using (
        bucket_id = 'trails'
        and exists (
            select 1 from public.profiles p
            where p.id = auth.uid() and p.can_manage_trails = true
        )
    );
