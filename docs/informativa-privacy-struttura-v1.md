# Informativa privacy geoHELP — struttura bozza (v1)

**Versione documento:** `v1` (allineata a `ConsentKeys.CURRENT_VERSION` nell’app)  
**Data:** [DA INSERIRE]  
**Lingue:** italiano (primaria) + inglese (consigliata, come l’app)

**Riepilogo PDF per il legale:** [`geoHELP-riepilogo-privacy-legale-v1.pdf`](./geoHELP-riepilogo-privacy-legale-v1.pdf)  
**Legenda SMS per destinatari SOS (PDF esterno):** [`SMS-FORMATO-COMPATTO.pdf`](./SMS-FORMATO-COMPATTO.pdf)

> Bozza operativa per il legale. Non è consulenza legale.  
> I testi tra virgolette «…» riprendono o espandono le stringhe in `app/src/main/res/values/strings.xml` (`consents_*`, `profile_*`, `mandown_*`).

---

## Avvertenze per il redattore (gap app ↔ testi attuali)

| Punto | Stato | Nota |
|-------|--------|------|
| Cognome in consenso | **Risolto** (v1.0.42+) | `consents_privacy_body` allineato a nome + anno di nascita |
| Disclaimer SMS | **Risolto** | `privacy_disclaimer` cita Supabase (UE); link a HTML in `assets/privacy/` |
| Link informativa | **Risolto** | Consensi + dialogo pre-SMS → `PrivacyPolicyScreen` |
| Numeri SOS | Da citare in § 6 | Tabella `sos_recipients` |
| Conservazione GPS | Da definire | § 8 — retention con il legale |
| Titolare | Da compilare | § 1 in HTML `[…]` e in questo doc |

---

## 0. Intestazione

**Titolo:** Informativa sul trattamento dei dati personali — app **geoHELP**  
**Sottotitolo (equivalente `consents_subtitle`):** Prima di usare il servizio, leggi questa informativa. I consensi richiesti in app si riferiscono alle sezioni indicate sotto.

---

## 1. Titolare del trattamento e contatti

**Paragrafi da compilare:**

- Identità del **Titolare**: [Ragione sociale / Associazione], [indirizzo], [P.IVA/C.F.].
- **Contatto privacy:** [email privacy@…] — [eventuale PEC].
- **Responsabile della Protezione dei Dati (RPD/DPO):** [se nominato, altrimenti «non previsto» con motivazione].

*Non presente in strings; obbligatorio art. 13 GDPR.*

---

## 2. Ambito e natura del servizio

**Paragrafi:**

- geoHELP è un’app per **richiedere assistenza in emergenza** tramite preparazione di messaggi SMS e, se attivata, invio automatico dopo rilevamento caduta/urto (**MAN DOWN**).
- Il servizio **non sostituisce** i numeri di emergenza pubblici (es. 112) né garantisce l’intervento dei soccorsi.
- L’uso richiede **account** (email e password) e, per le funzioni descritte, **permessi** del dispositivo (posizione, SMS, sensori, notifiche).

*Contesto per `auth_subtitle`, `mandown_section_hint` (se usato in UI).*

---

## 3. Trattamento dati personali (consenso obbligatorio)

**Allineamento UI:** `consents_privacy_label` = «Trattamento dati personali» · `consents_privacy_required` = «(obbligatorio)»

### 3.1 Testo equivalente al checkbox in app (`consents_privacy_body` — da adattare)

> L’utente, per usare geoHELP, deve poter prestare consenso al trattamento dei dati personali sotto indicati per le finalità del servizio, come descritto in questa informativa. **Senza questo consenso non è possibile usare l’app** (`consents_error_privacy_required`).

### 3.2 Categorie di dati (espansione del consenso privacy)

| Dato | Quando | Dove | Finalità |
|------|--------|------|----------|
| **Email** | Registrazione / accesso | Auth Supabase + colonna `profiles.email` | Account, recupero password, contatto |
| **Password** | Registrazione | Auth Supabase (hash) | Autenticazione |
| **Nome** (`profile_first_name`) | Onboarding / modifica profilo | `profiles` | Identificazione nelle richieste di aiuto |
| **Anno di nascita** (`profile_birth_year`) | Onboarding (obbligatorio per profilo completo) | `profiles` | Contesto demografico in emergenza (SMS) |
| **Sesso** (opzionale, `profile_gender`) | Profilo | `profiles` | Opzionale |
| **Telefono utente** (`user_phone`, obbligatorio, E.164) | Profilo / onboarding | `profiles` | Riga **Tel. utente** in SMS emergenza; contatto per i soccorsi |
| **Telefono contatto familiare** (opzionale) | Profilo | `profiles` | [Definire se usato solo in app o anche in SMS] |
| **Posizione GPS** | Invio SOS manuale; MAN DOWN / tracciamento | Messaggio SMS; sensori in locale | Georeferenziazione della richiesta |
| **Contenuto richiesta** | SOS manuale (tipo emergenza, note) | SMS | Comunicazione ai destinatari |
| **Lingua preferita** | Profilo / impostazioni | `profiles` / preferenze app | Localizzazione UI |
| **Storico consensi** | Ogni accetta/revoca | `consents` (versione, data, tipo) | Prova del consenso (`consents_manage_subtitle`) |
| **Log eventi SMS** (statistiche) | Preparazione SMS manuale; invii MAN DOWN | `sms_events` (canale, tipo messaggio, codice tipo emergenza, esito, conteggi — **senza testo SMS**) | Uso del servizio, qualità invii |

**Nota redazionale:** allineare il testo del checkbox rimuovendo «cognome» se non raccolto. Per `sms_events`: indicare base giuridica (es. legittimo interesse / finalità di servizio) e retention in § 10.

### 3.3 Base giuridica

- **Consenso** (art. 6.1.a GDPR) per l’uso dell’app e per i trattamenti collegati al SOS descritti in § 3 — coerente con il checkbox privacy.
- [Il legale valuti se parte del profilo possa basarsi su **esecuzione di un contratto** art. 6.1.b per l’account.]

### 3.4 Modalità di trattamento

- Dati su infrastruttura cloud **Supabase** (regione UE: Ireland, come da setup progetto).
- Trasporto **HTTPS**; accesso ai dati dell’utente limitato da **RLS** (Row Level Security) per `profiles`, `medical_data`, `consents`, `sms_events`.
- **SMS:** formato **compatto** (legenda PDF team SOS). SOS manuale via **Messaggi**; MAN DOWN: SIM, poi **Twilio** (mittente `geoHELP`), retry con rete senza Messaggi automatico.

### 3.5 Revoca del consenso privacy

- Da app: **Consensi e privacy** → revoca trattamento dati (`consents_revoke_privacy_title` / `consents_revoke_privacy_msg`): senza consenso **non è possibile continuare** a usare geoHELP; i dati vanno gestiti secondo § 10 (cancellazione/limitazione).

---

## 4. Dati medici (consenso opzionale)

**Allineamento UI:** `consents_medical_label` = «Dati medici» · `consents_medical_optional` = «(opzionale)»

### 4.1 Testo equivalente al checkbox (`consents_medical_body`)

Espandere per l’informativa mantenendo lo stesso significato:

1. **Categorie:** patologie (codici), allergie, terapie, pacemaker, gruppo sanguigno, note (`medical_data`).
2. **Salvataggio** sul backend geoHELP, strutturato, con **RLS** e **HTTPS** (come in stringa).
3. **Accesso del team** che amministra il servizio per **soccorso, supporto e verifica** (come in stringa).
4. **Inclusione sintetica nelle SMS** di emergenza (fino a limiti di lunghezza messaggio; vedi limiti tecnici app).
5. **PIN sul dispositivo** per aprire il modulo medico — lock locale, **non** cifratura del contenuto sul server (trasparenza).
6. **Revoca** in qualsiasi momento da **Consensi e privacy** (`consents_manage_subtitle`); revoca → niente accesso modulo (`medical_disabled_no_consent`).

### 4.2 Base giuridica

- **Consenso esplicito** art. 9.2.a GDPR (categorie particolari — dati relativi alla salute).

### 4.3 Assenza di consenso

- L’app resta utilizzabile per SOS e profilo; **non** si salvano né si mostrano dati medici sul server per quell’utente.

---

## 5. Funzione MAN DOWN — caduta / urto (consenso opzionale)

**Allineamento UI:** `consents_man_down_label` = «Funzione MAN DOWN (caduta / urto)» · `consents_man_down_optional` = «(opzionale)»

### 5.1 Testo equivalente al checkbox (`consents_man_down_body`)

1. **Sensori di movimento** (accelerometro, ecc.) per rilevare possibili **cadute o urti violenti**.
2. **Conto alla rovescia 60 secondi**, **annullabile** dall’utente, prima dell’invio.
3. **SMS automatici** ai **numeri primari** configurati, con **posizione GPS** e **dati essenziali** (inclusa eventuale sintesi medica se consenso medico attivo).
4. **Falsi allarmi** possibili (come in stringa).
5. **Permesso SMS** e altri permessi Android necessari (notifiche, attività in background dove applicabile).
6. **Disattivazione / revoca** da app in qualsiasi momento (`consents_manage_*`); revoca → disattivazione monitoraggio.

### 5.2 Gate dedicato (`mandown_gate_intro`)

Per utenti che avevano già accettato la privacy in passato: il consenso MAN DOWN è **registrato separatamente**; si può **rifiutare** e usare l’app **senza** MAN DOWN.

### 5.3 Base giuridica

- **Consenso** art. 6.1.a per sensori, SMS automatici e geolocalizzazione legata a MAN DOWN.
- [Il legale valuti menzione **decisioni automatizzate** art. 22 se il rilevamento è considerato profilazione/significativo.]

### 5.4 Comportamento post-urto (trasparenza tecnica, opzionale in informativa)

- Verifica movimento; eventuale **tracciamento** con ulteriori SMS a intervalli (descrivere se volete massima trasparenza).
- **Fallback cloud:** se la SIM non invia, il testo (con GPS e Tel. utente) può essere inviato tramite **Twilio**; in caso di ulteriore fallimento, apertura app Messaggi con messaggio precompilato.

---

## 6. Destinatari e responsabili del trattamento

**Paragrafi:**

| Destinatario | Ruolo GDPR | Cosa tratta |
|--------------|------------|-------------|
| **Supabase Inc.** (hosting UE) | Responsabile (con DPA) | DB, Auth, storage dati utente |
| **Destinatari SMS** (numeri in `sos_recipients` / backup) | Titolari autonomi o destinatari esterni | Contenuto SMS, posizione |
| **Personale / volontari** che amministrano geoHELP | Incaricati / autorizzati sotto il Titolare | Accesso dashboard, supporto |
| **Provider di connettività / SMS (SIM)** | Sub-responsabili del gestore telefonico | Trasporto SMS dal telefono dell’utente |
| **Twilio Inc.** (USA) | Responsabile / sub-responsabile [da qualificare] | Invio SMS **solo** come fallback MAN DOWN se la SIM fallisce; non usato per SOS manuale |

- Elenco **sub-responsabili** Supabase: riferimento al DPA standard Supabase.
- **Twilio:** indicare sede, finalità (invio SMS di emergenza), dati trattati (numeri destinatari, testo messaggio, mittente), **trasferimento extra-SEE** e garanzie (SCC / DPA Twilio) — **[DA COMPILARE col legale]**.

---

## 7. Trasferimenti extra-UE

- Indicare se i sub-fornitori Supabase prevedono trasferimenti fuori SEE e le **garanzie** (SCC, ecc.) — da verificare sul DPA Supabase aggiornato.

---

## 8. Periodo di conservazione

**[DA DEFINIRE CON IL LEGALE — suggerimenti tecnici]**

| Dato | Conservazione suggerita |
|------|-------------------------|
| Account e profilo | Fino a cancellazione account + [X] giorni backup |
| Dati medici | Fino a revoca consenso medico o cancellazione account |
| Storico `consents` | [X] anni per difesa diritti / obblighi (immutabile per design) |
| Log Auth Supabase | Secondo policy Supabase |
| Posizione GPS | Non persistita su DB oltre quanto in SMS — chiarire |

---

## 9. Sicurezza

- Misure: HTTPS, RLS, PIN locale modulo medico, credenziali cifrate (EncryptedSharedPreferences per «Ricordami» / PIN).
- **Limitazione:** dati medici in chiaro sul DB lato server, leggibili da amministratori autorizzati — come dichiarato nel consenso medico.

---

## 10. Diritti dell’interessato (art. 15–22 GDPR)

**Paragrafi standard + collegamento all’app:**

- Accesso, rettifica, cancellazione, limitazione, portabilità, opposizione (ove applicabile), **revoca del consenso** senza pregiudicare liceità precedente.
- **Come esercitare:** [email privacy@…]; in app: modifica profilo, **Consensi e privacy** per revoche.
- **Reclamo** al Garante per la protezione dei dati personali (www.garanteprivacy.it).
- **Cancellazione account:** [procedura — da implementare/documentare: richiesta a … con tempi …].

---

## 11. Obbligo o facoltà di conferire i dati

- Email/password: **necessari** per l’account.
- Nome e anno di nascita: **necessari** per profilo completo e uso pieno (`profile_error_required`, `profile_error_birth_year`).
- Dati medici e MAN DOWN: **facoltativi** (checkbox opzionali).

---

## 12. Minori

- [Definire età minima, es. 16 anni o consenso genitore se < 14 — il legale decida in base al target geoHELP.]

---

## 13. Modifiche all’informativa e ai consensi

- Versione informativa: **`v1`** registrata in app su ogni riga `consents.version`.
- In caso di modifiche sostanziali: nuova versione (`v2`), nuova accettazione in app (`ConsentsScreen`), storico precedente conservato.

---

## 14. Riepilogo consensi in app (tabella per l’utente)

| ID consenso (`consent_type`) | Label in app | Obbligatorio | Sezione informativa |
|------------------------------|--------------|--------------|---------------------|
| `privacy` | Trattamento dati personali | Sì | § 3 |
| `medical_data` | Dati medici | No | § 4 |
| `man_down` | MAN DOWN | No | § 5 |

**Gestione:** schermata «Consensi e privacy» (`consents_manage_title`) — ogni modifica = **nuova riga** sul server (`consents_manage_subtitle`).

---

## 15. Allegati consigliati (non in app oggi)

- **Termini e condizioni d’uso** (limitazione responsabilità servizio).
- **Cookie / tecnologie** (solo se aggiungete sito web o analytics).
- **Registro dei trattamenti** (documento interno, non pubblico).

---

## Appendice A — Testi UI da riusare verbatim (IT)

### `consents_privacy_body`
Acconsento al trattamento dei dati personali (email, nome, anno di nascita, dati opzionali di profilo, posizione GPS al momento dell'invio della richiesta di aiuto) per le finalità del servizio geoHELP, come da informativa privacy. Senza questo consenso non è possibile usare l'app.

### `consents_medical_body`
[testo completo in strings.xml righe 124]

### `consents_man_down_body`
[testo completo in strings.xml righe 127]

### `profile_subtitle`
Per privacy chiediamo solo il nome e l'anno di nascita.

---

## Appendice B — Checklist prima della pubblicazione

- [ ] Titolare e contatti compilati (§ 1)
- [ ] Allineato cognome / campi profilo (§ 3)
- [ ] Rimosso o aggiornato `privacy_disclaimer` legacy in app
- [ ] DPA Supabase firmato
- [ ] Retention definita (§ 8)
- [ ] Procedura cancellazione account (§ 10)
- [ ] PDF/HTML pubblicato e linkato da `ConsentsScreen`
- [ ] Versione `v1` coerente con DB e app
