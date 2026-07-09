-- ============================================================================
-- profiles.email — email di login (copia da auth.users)
-- ============================================================================

alter table public.profiles
    add column if not exists email text;

comment on column public.profiles.email is
    'Email account (auth.users). Copiata alla registrazione e aggiornabile dall''app.';

-- Allinea profili esistenti
update public.profiles p
set email = u.email
from auth.users u
where p.id = u.id
  and (p.email is distinct from u.email);

-- Nuovi utenti: email inserita al signup
create or replace function public.handle_new_user()
returns trigger
language plpgsql
security definer
set search_path = public
as $$
begin
    insert into public.profiles (id, preferred_language, email)
    values (new.id, 'it', new.email)
    on conflict (id) do update
        set email = excluded.email
        where public.profiles.email is distinct from excluded.email;
    return new;
end;
$$;
