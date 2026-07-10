# geoHELP — Guida rapida all’uso

**Versione app:** 1.2.38 · **Lingue:** italiano / inglese

geoHELP è un’app di supporto in montagna (Alta Valsusa). **Non sostituisce il 112.** In pericolo immediato chiama sempre il **Numero Unico di Emergenza**.

---

## 1. Prima di usare l’app

| Cosa fare | Perché |
|-----------|--------|
| Accetta i **permessi** richiesti (posizione, SMS se usi l’SOS) | Servono per GPS e messaggi di aiuto |
| Crea **account** e accetta i **consensi** | Obbligatori per usare il servizio |
| Attiva il **GPS** sul telefono | Per posizione e mappa |
| Verifica **rete mobile** e credito SMS | Per MAN DOWN automatico e per aprire Messaggi con l’SOS |
| (Opzionale) Inserisci **dati medici** e attiva **MAN DOWN** | Solo se ti servono |

**Consiglio:** in montagna disattiva il risparmio energetico aggressivo sul telefono, così GPS e MAN DOWN funzionano meglio.

---

## 2. Menu in basso (5 icone)

Da sinistra a destra, più il **SOS** al centro:

| Icona | Funzione |
|-------|----------|
| **Chiama 112** | Apre la chiamata al Numero Unico di Emergenza |
| **Trekking** | Posizione GPS, mappa e sentieri da percorrere |
| **SOS** (rosso, al centro) | Schermata di richiesta aiuto (invio **manuale** SMS) |
| **Impostazioni** | Lingua, consensi, profilo, dati medici, MAN DOWN |
| **Info** | Link utili (Via Lattea, meteo, webcam, DAE) |

---

## 3. Trekking (posizione e sentieri)

### Schermata **Trekking**
- Vedi **latitudine, longitudine**, precisione GPS e altitudine.
- Pulsante **APRI SU MAPPA** → mappa a schermo intero.

### Tabella sentieri
- Sentieri raggruppati per **comune** (es. Sestriere, Cesana).
- **PDF** → scheda descrittiva del sentiero.
- **TRACK** (giallo quando attivo) → apre la mappa con la traccia del sentiero.
- Secondo tap su **TRACK** → disattiva la traccia.

### Sulla mappa
| Pulsante | Cosa fa |
|----------|---------|
| **GPS / bersaglio** (basso sinistra) | Centra la mappa su di te e attiva il **seguimento** |
| **X verde/rossa** | Verde = nord dinamico (mappa ruota con la bussola). Rosso = nord fisso in alto |
| **Bussola** (alto) | Mostra direzione; tap per **calibrazione** |
| **Layer** (alto sinistra) | Tipo mappa: stradale, topografica, satellite |
| **Omino trekking** | Overlay sentieri web (opzionale, di default spento) |

**In camminata:** tieni attivo il seguimento GPS (icona fissa). Se sposti la mappa col dito, il seguimento si disattiva: tocca di nuovo il pulsante GPS per ricentrarti.

Con **TRACK** attivo vedi: traccia rossa del sentiero, linea blu verso l’inizio percorso, distanza in alto.

---

## 4. SOS — richiesta di aiuto (**invio manuale**)

> **SOS non invia** SMS da solo: prepara il messaggio e apre **Messaggi**; **devi premere tu INVIA**.

### Come funziona

1. Tocca **SOS** (rosso, al centro).
2. Compila **tipo di emergenza** e, se vuoi, **note** (es. “sciovia ghiacciata”, “dolore toracico”).
3. Tocca **INVIA SMS** (rosso).
4. Si apre **Messaggi** con testo e **tutti i destinatari attivi** (fino a **3 numeri**) già compilati.
5. **Controlla e premi INVIA** sul telefono.

I numeri sono **gestiti dal servizio geoHELP** in Supabase (non li scegli tu nell’app). L’amministratore autorizzato può attivarli/disattivarli e modificarli da **Impostazioni**.

### Cosa contiene l’SMS SOS

Messaggio compatto con intestazione **«SOS geoHELP»**, ad esempio:

- **Nome** e anno di nascita (dal profilo)
- **Tuo numero** di telefono (per richiamarti)
- **Tipo** di emergenza scelto (incidente, malessere, ecc.)
- **Note** che hai scritto
- **Posizione GPS** (latitudine, longitudine, altitudine se disponibile)
- **Dati medici** in sintesi (solo se hai dato il consenso)

Serve **profilo compilato**, **GPS** attivo e l’app **Messaggi** funzionante. Senza rete l’invio può fallire: in emergenza grave usa subito il **112**.

---

## 5. MAN DOWN — rilevamento caduta (**unico invio automatico**)

> **Solo MAN DOWN può inviare automaticamente** SMS (via servizio geoHELP, senza aprire Messaggi).

### Perché è importante

Serve quando **non puoi premere SOS**, ad esempio:

- **caduta** da malore, svenimento o infarto;
- **urto** violento in sci, bici o sentiero;
- ti trovi **solo** e resti privo di sensi o impossibilitato a usare il telefono.

In questi casi i sensori del telefono possono rilevare l’evento e avvisare i soccorsi geoHELP **senza che tu invii nulla**.

### Come funziona

1. In **Impostazioni** accetta il consenso **MAN DOWN**.
2. Nella schermata **SOS** premi **Attiva MAN DOWN** (resta in ascolto: compare una notifica).
3. Se il telefono rileva una **caduta o urto** importante:
   - parte una **sirena** e un **conto alla rovescia di 60 secondi**;
   - puoi **Annulla** se è un falso allarme;
   - se **non annulli**, l’app invia **automaticamente** SMS a **tutti i destinatari attivi** (fino a **3 numeri**) tramite il servizio geoHELP.
4. Se poi rileva **movimento** (es. scivolamento), **può inviare automaticamente** fino a **4 aggiornamenti** di posizione (traccia).

### Cosa contiene l’SMS MAN DOWN

Intestazione **«MAN DOWN geoHELP»**, con nome, telefono, **posizione GPS** (anche precisione e velocità) e sintesi **dati medici** se consentiti. Non include il tipo/note dell’SOS manuale: è un allarme automatico da caduta/urto.

### Requisiti e attenzioni

- Serve **rete** (Wi‑Fi o dati mobili) per l’invio automatico.
- GPS, notifiche e permessi devono essere concessi.
- Può scattare per **falsi allarmi** (salti, botte sullo zaino, vibrazioni): annulla entro 60 secondi se non serve aiuto.
- Disattivalo con **Disattiva MAN DOWN** quando non ti serve.

---

## 6. Dati medici (opzionale)

- Attiva il consenso in **Impostazioni** (icona ingranaggio in basso).
- Crea un **PIN** (minimo 4 cifre) per proteggere l’accesso sul telefono.
- Inserisci patologie, allergie, terapie, gruppo sanguigno, note.
- In emergenza alcune informazioni possono essere incluse negli SMS.

Il PIN protegge solo l’accesso **sul dispositivo**; i dati sono salvati in modo protetto sui server geoHELP.

---

## 7. Settaggi consigliati sul telefono

**Android — controlla che:**

- **Posizione** = Attiva, modalità **Alta precisione** (GPS + rete).
- **Permessi geoHELP** → Posizione: *Consenti sempre* (o *Solo durante l’uso* se preferisci).
- **SMS** → Consentito (per SOS e MAN DOWN).
- **Batteria** → Escludi geoHELP dall’ottimizzazione aggressiva (impostazioni batteria / app in background).
- **Notifiche** → Consentite (utili per MAN DOWN in background).

**In montagna:** tieni il telefono carico; il GPS consuma batteria.

---

## 8. Lingua

Dall’icona **bandiera** in alto puoi passare tra **italiano** e **inglese**.

---

## Ricorda

| | |
|---|---|
| **112** | Sempre la prima scelta in emergenza grave |
| **geoHELP** | Supporto privato con patrocinio Comuni e Consorzio Turismo |
| **SOS** | Invio **manuale** — tu premi INVIA in Messaggi |
| **MAN DOWN** | Unico invio **automatico** se non annulli entro 60 s |
| **Mappa / sentieri** | Aiuto all'orientamento, non sostituiscono carta e segnaletica |

*Documento per utenti finali — geoHELP · Alta Valsusa*

*by Nucleo Volontari Ass. Naz. Sanità Militare Italiana*
