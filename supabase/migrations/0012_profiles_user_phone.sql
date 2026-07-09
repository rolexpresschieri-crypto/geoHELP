-- Telefono dell'utente in difficoltà (richiamata soccorso / riga negli SMS).

alter table public.profiles
    add column if not exists user_phone text;

comment on column public.profiles.user_phone is
    'Cellulare utente in formato E.164 (es. +39347...), obbligatorio per profilo completo.';
