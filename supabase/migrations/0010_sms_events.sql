-- Log statistico invii SMS (senza testo messaggio): canale, tipo messaggio, tipo emergenza.

create table if not exists public.sms_events (
    id              bigint generated always as identity primary key,
    user_id         uuid not null references auth.users(id) on delete cascade,
    channel         text not null check (channel in ('manual', 'mandown')),
    message_kind    text not null check (message_kind in ('prepared', 'initial', 'trace', 'lost_signal')),
    emergency_type  text not null check (emergency_type in (
                        'incident', 'illness', 'vehicle', 'lost', 'weather', 'other', 'mandown'
                    )),
    outcome         text not null check (outcome in ('ok', 'failed')),
    dest_count      smallint,
    segment_count   smallint,
    trace_index     smallint check (trace_index is null or (trace_index between 1 and 4)),
    recipient_role  text check (recipient_role is null or recipient_role in ('primary', 'backup')),
    created_at      timestamptz not null default now()
);

create index if not exists sms_events_user_id_idx on public.sms_events (user_id);
create index if not exists sms_events_created_at_idx on public.sms_events (created_at desc);
create index if not exists sms_events_channel_idx on public.sms_events (channel);

alter table public.sms_events enable row level security;

drop policy if exists "sms_events_select_own" on public.sms_events;
drop policy if exists "sms_events_insert_own" on public.sms_events;

create policy "sms_events_select_own"
    on public.sms_events for select
    using (auth.uid() = user_id);

create policy "sms_events_insert_own"
    on public.sms_events for insert
    with check (auth.uid() = user_id);
