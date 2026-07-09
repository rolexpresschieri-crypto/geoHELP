# geoHELP — Twilio (MAN DOWN fallback SMS)

Guida operativa: account Twilio, Supabase Edge Function, test **curl** e test **da telefono** (app dev **1.0.64**).

**Flusso MAN DOWN (app 1.0.62+):**

1. **SMS standard (SIM)** → se ok, **fine**
2. Se SIM fallisce → **Twilio** (`send-mandown-sms`) → se ok, **fine**
3. Se Twilio fallisce o non c’è rete → **retry ogni ~20 s** finché torna Wi‑Fi/dati (notifica “invio in corso”) — **non** apre Messaggi

**Help manuale:** solo SIM / Messaggi — **nessun Twilio**.

---

## Stato integrazione (maggio 2026)

| Componente | Stato |
|------------|--------|
| Edge Function `send-mandown-sms` | Deployata |
| Secret Supabase (`TWILIO_*`) | Configurati |
| App `ManDownTwilioRepository` + `ManDownForegroundService` | Integrato (dev **1.0.64**, SMS compatto, no Messaggi) |
| Legenda SMS destinatari | `docs/SMS-FORMATO-COMPATTO.pdf` |
| Campo profilo **`user_phone`** | Obbligatorio; riga `Tel. utente: +39…` negli SMS |
| Mittente attuale consigliato | **`geoHELP`** (alfanumerico IT) |
| Mittente test (legacy) | `+18782842390` solo se `geoHELP` non ancora approvato |

---

## Fase A — Account Twilio

1. [https://www.twilio.com/try-twilio](https://www.twilio.com/try-twilio) → account + verifica telefono.
2. Console: [https://console.twilio.com](https://console.twilio.com).
3. **Account Info:** `Account SID` (`AC…`) e **Auth Token** (copia subito nel password manager).
4. **Trial / pay-as-you-go:** SMS verso destinatari **non verificati** vengono rifiutati finché:
   - il numero è in **Phone Numbers → Verified caller IDs** (verifica tipicamente via **Call**, non SMS da IT), **oppure**
   - l’account è approvato oltre i limiti trial.
5. Costo indicativo SMS Italia (mittente alfanumerico): ~ **$0,0927**/messaggio.

---

## Fase B — Mittente `geoHELP` (da fare ora)

### 1. Registra il sender su Twilio

1. Console → **Messaging** → **Senders** (o **Alphanumeric Sender IDs**).
2. Crea **`geoHELP`** (solo lettere, max 11 caratteri).
3. Paese **Italia**, categoria **alert / transazionale / emergenza**.
4. Attendi **Approved** se Twilio richiede revisione.

### 2. Aggiorna Supabase (obbligatorio)

**Project Settings → Edge Functions → Secrets:**

```text
TWILIO_SMS_FROM=geoHELP
```

(Senza spazi; maiuscole come registrato su Twilio.)

Non serve redeploy della function: al prossimo invio userà il nuovo secret.

### 3. Verifica

- Un SMS di test (curl o MAN DOWN): in **Messaging Logs** il campo **From** deve essere **`geoHELP`**, non `+1878…`.
- Sul telefono il mittente può comparire come **geoHELP** o etichetta operatore (non più solo numero US).

### Fallback numero US (solo se `geoHELP` non è ancora approvato)

```text
TWILIO_SMS_FROM=+18782842390
```

> SMS **unidirezionali**: nel testo c’è **`Tel. utente: +39…`** (`profiles.user_phone`).

---

## Fase C — Secret su Supabase (solo dashboard)

**Project Settings → Edge Functions → Secrets** (mai nel repo git):

| Secret | Esempio | Note |
|--------|---------|------|
| `TWILIO_ACCOUNT_SID` | `ACxxxxxxxx` | Obbligatorio |
| `TWILIO_AUTH_TOKEN` | `xxxxxxxx` | Obbligatorio |
| `TWILIO_SMS_FROM` | `geoHELP` oppure `+18782842390` | Mittente effettivo |

Rimuovi eventuali secret errati (es. email usata per errore al posto del token).

---

## Fase D — Deploy Edge Function

File: `supabase/functions/send-mandown-sms/index.ts`

```bash
supabase login
supabase link --project-ref <PROJECT_REF>
supabase functions deploy send-mandown-sms --project-ref <PROJECT_REF>
```

Oppure: **Edge Functions → Create/Edit** → incolla `index.ts` → Deploy.

---

## Fase E — Test senza app (curl / PowerShell)

### Prerequisiti

1. Utente geoHELP **loggato** (JWT valido).
2. Destinatario `+39…` **verificato** in Twilio (trial) oppure account non in trial.
3. `TWILIO_SMS_FROM` impostato.

### PowerShell (consigliato su Windows)

```powershell
$projectUrl = "https://iasyzvpmsxapmuehfnit.supabase.co"
$anon = "<SUPABASE_ANON_KEY_DEV>"
$email = "<email_utente_test>"
$password = "<password>"

# Login → JWT
$auth = Invoke-RestMethod -Method Post -Uri "$projectUrl/auth/v1/token?grant_type=password" `
  -Headers @{ apikey = $anon; "Content-Type" = "application/json" } `
  -Body (@{ email = $email; password = $password } | ConvertTo-Json)

$body = @{
  body = "[geoHELP TEST] MAN DOWN Twilio"
  destinations = @("+39XXXXXXXXXX")
} | ConvertTo-Json -Compress

$uri = "$projectUrl/functions/v1/send-mandown-sms"
$resp = Invoke-WebRequest -Method Post -Uri $uri -Headers @{
  Authorization = "Bearer $($auth.access_token)"
  apikey = $anon
  "Content-Type" = "application/json"
} -Body $body -SkipHttpErrorCheck

Write-Host "Status:" $resp.StatusCode
Write-Host $resp.Content
```

### Risposte attese

| Status | Significato |
|--------|-------------|
| **200** + `"ok":true,"sent":1` | SMS accettato da Twilio |
| **401** | JWT mancante/scaduto — rifare login |
| **502** | Twilio ha rifiutato — leggere `results[].error` nel JSON |

### Errore tipico (trial) — pipeline OK

```json
{
  "ok": false,
  "error": "The number +393517789490 is unverified. Trial accounts cannot send messages to unverified numbers..."
}
```

Significa: **Supabase → function → Twilio funziona**; blocca solo la policy Twilio sul destinatario.

**Cosa fare:** Verified Caller IDs → aggiungi `+39…` → completa verifica **Call**; oppure attendi approvazione account; in produzione con credito e sender IT i limiti cambiano.

---

## Fase F — Test da telefono (app 1.0.61)

APK dev: `app/build/outputs/apk/dev/debug/app-dev-debug_1.0.61.apk`  
(dopo build: `.\gradlew.bat :app:assembleDevDebug`)

### Checklist pre-test

- [ ] Profilo completo con **Tel. utente** (`+39…`, E.164).
- [ ] Login Supabase attivo (stessa sessione dell’app).
- [ ] Consenso **MAN DOWN** accettato.
- [ ] Permesso **SMS** concesso.
- [ ] Almeno un **primario** attivo in `sos_recipients` (numero di test).
- [ ] Rete dati/Wi‑Fi attiva (per fallback Twilio).
- [ ] Su trial Twilio: destinatario SOS **verificato** in console Twilio.

### Scenario 1 — Solo Twilio (SIM disabilitata o fallisce)

**Obiettivo:** verificare ramo cloud senza affidarsi alla SIM.

1. Telefono test: **modalità aereo** → riattiva solo **Wi‑Fi** (SIM off), **oppure** SIM senza credito / operatore che blocca SMS (es. alcuni profili Vivo).
2. Arma MAN DOWN, provoca evento (o usa debug se presente) → attendi fine countdown **60 s**.
3. **Logcat** (filtro `ManDown` / `ManDownTwilio`):

   ```text
   MAN DOWN SMS: modem ha rifiutato o timeout — tentativo Twilio
   MAN DOWN Twilio: ok=true sent=1 ...
   ```

4. Il destinatario deve ricevere SMS (mittente = numero US di test o `geoHELP`).
5. Testo SMS deve contenere **`Tel. utente: +39…`**.
6. Tabella `sms_events`: riga `channel=mandown`, `outcome=ok` (dopo invio riuscito).

Se Twilio fallisce temporaneamente: notifica **«invio in corso»** e retry ogni ~20 s quando torna la rete (non si apre Messaggi).

### Scenario 2 — SIM OK (controllo)

1. SIM attiva, stesso test MAN DOWN.
2. Log atteso: invio modem **ok** — **nessuna** riga `tentativo Twilio`.
3. SMS arriva dalla **SIM** del telefono (non da Twilio).

### Scenario 3 — Help manuale (no Twilio)

1. Tab **Help** → invio SOS.
2. Si apre **Messaggi**; **non** deve comparire `ManDownTwilio` in log.
3. `sms_events`: `channel=manual`, `message_kind=prepared`.

### Scenario 4 — Admin numeri SOS (opzionale)

1. Tap ripetuto su logo HELP + PIN `SOS_ADMIN_PIN` (`local.properties`).
2. Verifica lista primari/backup e flag `active`.

### Dopo test riuscito Twilio

1. Registrare sender **`geoHELP`** (Fase B).
2. Aggiornare secret: `TWILIO_SMS_FROM=geoHELP`.
3. Ripetere Scenario 1 con mittente alfanumerico.

---

## Fase G — Riferimenti app (per debug)

| File | Ruolo |
|------|--------|
| `ManDownForegroundService.kt` | SIM → Twilio → Messaggi |
| `ManDownTwilioRepository.kt` | `invoke("send-mandown-sms")` |
| `EmergencySmsText.kt` | Testo + `Tel. utente` |
| `UserPhone.kt` / profilo | Validazione E.164 |

**Help manuale:** nessuna chiamata a `ManDownTwilioRepository`.

---

## Checklist rapida

- [ ] Account Twilio + SID/Token
- [ ] Numeri test verificati (trial) o account approvato
- [ ] Secret `TWILIO_*` in Supabase (non in git)
- [ ] Function deployata
- [ ] Test PowerShell → 200 o 502 con messaggio Twilio chiaro
- [ ] `user_phone` in profilo
- [ ] APK **1.0.61** installato
- [ ] Test telefono Scenario 1 e 2
- [ ] Sender `geoHELP` per produzione

---

## Link utili

- [Twilio SMS Italy pricing](https://www.twilio.com/en-us/sms/pricing/it)
- [Supabase Edge Functions](https://supabase.com/docs/guides/functions)
- [Twilio Verified Caller IDs](https://www.twilio.com/docs/usage/tutorials/how-to-use-your-free-trial-account#verify-your-personal-phone-number)
- [Twilio Messages API](https://www.twilio.com/docs/messaging/api/message-resource)
- Riepilogo GDPR/Twilio per il legale: [`geoHELP-riepilogo-privacy-legale-v1.pdf`](./geoHELP-riepilogo-privacy-legale-v1.pdf)
