# geoHELP — Supabase setup

Questa cartella contiene gli SQL e le istruzioni per preparare il progetto Supabase di **geoHELP**.

> Attenzione: per ora creiamo **solo l'ambiente di sviluppo** (`geoHELP-dev`). Il progetto **prod** verrà creato più avanti, quando saremo pronti a rilasciare la versione cloud su Google Play.

## 1. Creazione progetto su Supabase

1. Vai su https://supabase.com e accedi.
2. Nella tua **organizzazione** crea un nuovo progetto:
   - **Name**: `geoHELP-dev`
   - **Database password**: generala forte, salvala nel password manager.
   - **Region**: `West EU (Ireland)` (per coerenza con T.O.C. e per restare in UE → GDPR).
   - **Pricing plan**: Free per ora.
3. Attendi la creazione (1-2 minuti).

## 2. Configurazione Auth

Dal menu **Authentication → Providers**:

- **Email** → abilitato (default). Per dev puoi disattivare la conferma email (`Confirm email = off`) per fare test rapidi; in prod la riattiveremo.
- **Google** → abilitalo (serve un OAuth client su Google Cloud Console — lo configuriamo quando integriamo l'app Android).
- **Facebook** → abilitalo dopo (Meta richiede passaggi extra e revisione).

Per ora basta abilitare **Email + password**: gli OAuth li attiviamo al passaggio successivo.

In **Authentication → URL Configuration** (progetto usato dall'APK, di solito **prod**):

| Campo | Valore |
|-------|--------|
| **Site URL** | `it.geohelp://login-callback` (oppure l'URL web della privacy, se preferisci) |
| **Redirect URLs** | `it.geohelp://login-callback` (e, se usi il bridge web, anche l'URL HTTPS di `docs/privacy/auth-callback.html`) |

Senza questa voce, **Password dimenticata?** risponde con errore «recupero password non configurato» anche se l'app è corretta.

Il deeplink è già dichiarato in `AndroidManifest.xml` (`scheme=it.geohelp`, `host=login-callback`).

## 3. Esecuzione dello schema

1. Apri il progetto su Supabase.
2. Vai su **SQL Editor → New query**.
3. Copia e incolla il contenuto di [`schema.sql`](./schema.sql).
4. Premi **Run**.

Dovresti vedere "Success. No rows returned."

Cosa è stato creato:

| Tabella           | Scopo                                                        |
|-------------------|--------------------------------------------------------------|
| `profiles`        | Anagrafica base utente (in chiaro, una riga per auth.users). |
| `medical_data`    | Dati sanitari, salvati **cifrati lato client** (blob JSON).  |
| `consents`        | Storico consensi (privacy, medical_data, man_down).          |
| `sos_recipients`  | Numeri SMS per SOS (sostituisce il Google Sheet pubblico).   |

Tutte le tabelle hanno **RLS attiva**:

- `profiles` / `medical_data` / `consents` → ogni utente vede solo i propri dati.
- `sos_recipients` → lettura righe `active=true` (anche anon); aggiornamento `active` solo per utenti con `profiles.can_manage_sos_recipients = true` (schermata admin nascosta in app).

Trigger automatici:

- `updated_at` aggiornato a ogni UPDATE.
- Alla creazione di un nuovo utente in `auth.users`, viene creata automaticamente la sua riga in `profiles`.

## 4. Popolamento iniziale numeri SOS

1. Apri [`seed_sos_recipients.sql`](./seed_sos_recipients.sql).
2. **Sostituisci i numeri di esempio** con quelli reali del Google Sheet attuale.
3. Esegui il file nel SQL Editor.

In futuro, per modificare i numeri ti basta andare in **Table editor → sos_recipients** e modificare le righe direttamente dalla dashboard.

### Admin app (tap logo HELP + PIN)

1. Esegui la migrazione [`migrations/0011_sos_recipients_admin_manage.sql`](./migrations/0011_sos_recipients_admin_manage.sql).
2. Per i tre account amministratori, in **profiles** imposta `can_manage_sos_recipients = true`.
3. In `local.properties` (non committato): `SOS_ADMIN_PIN=<password condivisa>` e ricompila l’APK.
4. Nell’app: tab HELP → tap sul **logo geoHELP** → PIN → elenco primari/backup con checkbox (active su Supabase).

## 5. Recupero chiavi per l'app Android

Dal menu **Project Settings → API**:

- **Project URL** → ti serve.
- **anon public key** → ti serve.

Queste due stringhe verranno messe nell'app Android in `gradle.properties` (locale, non committato), come:

```properties
SUPABASE_URL_DEBUG=https://<tuo-id>.supabase.co
SUPABASE_ANON_KEY_DEBUG=eyJ...
SUPABASE_URL_RELEASE=
SUPABASE_ANON_KEY_RELEASE=
```

Le `RELEASE` restano vuote finché non creiamo il progetto prod.

> **Mai committare** la `service_role` key: dà accesso pieno, ignora RLS, è da usare solo da backend lato server, mai dal client mobile.

## 6. Note di sicurezza

- I dati di `medical_data` sono cifrati con **AES-GCM** lato client usando una chiave derivata dal **PIN** scelto dall'utente (PBKDF2 / salt per utente). L'admin Supabase **non** può leggere il contenuto in chiaro.
- I numeri in `sos_recipients` non sono cifrati: sono indirizzi operativi (come prima erano in chiaro nello Sheet pubblico).
- RLS è abilitata su tutte le tabelle: anche se la `anon_key` venisse intercettata, un utente non potrebbe leggere/modificare dati di altri utenti.

## 7. Reset dev (ripartire senza account vecchi)

Se il login non funziona più con account creati in prova, puoi **azzerare tutti gli utenti** e i dati collegati lasciando i numeri SOS.

1. Supabase → **SQL Editor** → nuova query.
2. Incolla ed esegui [`migrations/0008_dev_reset_users_keep_sos_recipients.sql`](./migrations/0008_dev_reset_users_keep_sos_recipients.sql).
3. In dashboard **Authentication → Providers → Email**: per i test imposta **Confirm email = OFF** (altrimenti dopo la registrazione non entri finché non confermi la mail).
4. Sull’app: **Esci** oppure **Usa un altro account**, poi **Registrati** con email e password nuove.

| Cosa viene cancellato | Cosa resta |
|-----------------------|------------|
| `auth.users` (tutti gli account) | `public.sos_recipients` |
| `profiles`, `medical_data`, `consents` | Trigger e policy invariate |

> Solo ambiente **dev**. Non usare in produzione.

## 8. Cosa NON facciamo qui

- Niente progetto `geoHELP-prod`: per ora.
- **MAN DOWN fallback SMS:** Edge Function `send-mandown-sms` + Twilio — guida completa in [`docs/TWILIO-SETUP.md`](../docs/TWILIO-SETUP.md).
- Niente Storage: per ora nessun file utente da caricare.

## 9. Roadmap dei prossimi passi

1. **Punto 1 (in corso)** — Fondazioni:
   - [x] Schema + RLS + trigger.
   - [ ] Creazione progetto `geoHELP-dev` su Supabase.
   - [ ] Esecuzione `schema.sql`.
   - [ ] Popolamento `sos_recipients` con numeri reali.
   - [ ] Setup `gradle.properties` + flavor `dev` nell'app Android.
   - [ ] Schermate auth + onboarding consensi + profilo + dati medici (con PIN).
   - [ ] Sostituzione lettura CSV Google Sheet con query a `sos_recipients`.
2. **Punto 2** — MAN DOWN (sensori + countdown + invio SMS automatico).
3. **Punto 3** — Push FCM (avvisi).
