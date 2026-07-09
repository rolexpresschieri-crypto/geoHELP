-- ============================================================================
-- 0003 — medical_data: passaggio a colonne strutturate "in chiaro"
-- ----------------------------------------------------------------------------
-- Decisione architetturale: i dati medici NON sono più cifrati lato client
-- con il PIN. Restano protetti da:
--   * RLS (ogni utente vede solo la propria riga; auth.uid() = user_id)
--   * Trasporto HTTPS verso Supabase
--   * Consenso esplicito alla "liberatoria" (consents.medical_data accepted)
--
-- Il team che amministra il progetto Supabase (service_role) può consultare
-- le righe in chiaro per finalità di soccorso/supporto, come da liberatoria.
-- Il PIN resta solo come lock locale sull'app per impedire l'accesso al
-- form medico da chi prende in mano il telefono dell'utente.
--
-- Da eseguire nel SQL Editor del progetto geoHELP-dev. Idempotente.
-- ============================================================================

alter table public.medical_data
    add column if not exists conditions text,
    add column if not exists pacemaker  boolean not null default false,
    add column if not exists allergies  text,
    add column if not exists therapies  text,
    add column if not exists notes      text;

-- Il vecchio blob cifrato resta come colonna opzionale, ma non viene più
-- né letto né scritto dall'app. Può essere droppato dopo aver verificato
-- che nessuna riga in produzione contenga dati ancora cifrati con il vecchio
-- schema. Per ora lo lasciamo nullable (era già nullable nello schema base).

-- Nota RLS: le policy esistenti su medical_data (select/insert/update/delete
-- proprie) coprono già le nuove colonne; non serve modificarle.
