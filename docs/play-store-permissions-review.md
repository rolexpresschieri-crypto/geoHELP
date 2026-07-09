# Istruzioni per revisori Google Play — geoHELP (it.geohelp)

Usa questo testo nel **Modulo dichiarazione SMS** e nelle **note per il revisore** (release 1.1.13+).

## Foreground service (Play Console — OBBLIGATORIO)

Menu: **Contenuti app** → **Permessi per servizi in primo piano** / **Foreground service permissions**

| Campo | Valore corretto |
|-------|-----------------|
| Tipo FGS | **Location** (Posizione) — **NON** Health / Health - Other |
| Caso d'uso | MAN DOWN: servizio avviato dall'utente con «Attiva MAN DOWN», notifica persistente «MAN DOWN attivo» con pulsante Disattiva, GPS per SMS emergenza |
| Video (se richiesto) | Registrare: Attiva MAN DOWN → notifica visibile → Disattiva dalla notifica |

**Rimuovere** ogni dichiarazione **Health - Other** dalla Console (il manifest usa solo `foregroundServiceType="location"`).

## SMS — video consigliato (quasi obbligatorio dopo rifiuto)

Google non ha verificato la funzione HELP. Caricare video YouTube **non in elenco** (30–60 s):

1. Login con account test
2. Tab HELP → chip tipo emergenza (es. Infortunio) → **Invia SMS primario**
3. Dialog «Accetto e continua» → app **Messaggi** con testo [GeoHELP] + GPS
4. (Opz.) Attiva MAN DOWN → notifica «MAN DOWN attivo»

Incollare il link nel modulo **SMS → Istruzioni per l'esame** (se c'è campo video) o in **Accesso app**.

## Account di test

Fornire in Play Console (se richiesto):
- Email e password di un account registrato nell’app
- Profilo completato (nome, anno di nascita, telefono)

## Prominent Disclosure (posizione / SMS)

**Importante per il revisore:** alla schermata di login **non** compare alcuna richiesta di permesso posizione o SMS. I permessi sensibili compaiono **solo** nella tab HELP, **dopo** un’azione esplicita dell’utente:

1. **Posizione GPS (tab Posizione):** l’utente deve toccare **Consenti posizione GPS** → dialog in-app con titolo «Accesso alla posizione GPS», testo su raccolta/uso dati, pulsante rosso **Accetto e continua** → **solo allora** il dialog di sistema Android.
2. **SMS / notifiche / posizione in background (MAN DOWN):** l’utente deve toccare **Attiva MAN DOWN** → sequenza di dialog in-app (SMS, notifiche, GPS, eventuale «Posizione anche a schermo spento») ciascuno con **Accetto e continua** → dialog di sistema corrispondente.

## Funzionalità core: SMS di emergenza con GPS

### A) HELP manuale (funzione principale visibile)

1. Accedi all’app con account di test.
2. Tab **HELP** (prima icona in basso).
3. Compila profilo se richiesto (nome, anno, telefono).
4. Seleziona un **tipo di emergenza** (es. “Infortunio”).
5. Tocca il pulsante rosso **Invia SMS primario**.
6. Leggi il **dialog in-app** (privacy + posizione GPS) e tocca **Accetta**.
7. Si apre l’app **Messaggi** del telefono con SMS precompilato contenente:
   - tag `[GeoHELP]`
   - tipo emergenza
   - **coordinate GPS**
   - nome e telefono utente
8. L’utente invia manualmente dall’app Messaggi (rete SIM).

### B) MAN DOWN (SMS automatici — opzionale)

1. Durante onboarding accettare consenso **MAN DOWN**.
2. Nella tab HELP scorrere fino alla sezione **MAN DOWN**.
3. Toccare **Attiva MAN DOWN** (azione esplicita dell’utente).
4. Confermare i **dialog di disclosure** in-app (pulsante **Accetto e continua**), poi concedere:
   - SMS
   - Notifiche (Android 13+)
   - Posizione (e «sempre» se richiesto per allarme a schermo spento)
5. Compare **notifica permanente** «MAN DOWN attivo».
6. Per test senza urto reale: il revisore può verificare la notifica e la disattivazione con **Disattiva MAN DOWN**.

## Dichiarazioni Play Console da aggiornare

| Permesso | Dichiarazione |
|----------|----------------|
| **SMS** | Physical safety / emergency alerts — invio SMS di emergenza con posizione |
| **Posizione** | Funzionalità core HELP + MAN DOWN |
| **Foreground service** | Tipo **Location** (non Health) — MAN DOWN con notifica visibile |
| **Notifiche** | Conto alla rovescia e stato allarme MAN DOWN |

## Video consigliato (30–60 s)

Registrare schermata che mostra:
1. Login
2. Tab HELP → tipo emergenza → Invia SMS → dialog disclosure → app Messaggi con testo GPS
3. Attiva MAN DOWN → disclosure → notifica «MAN DOWN attivo»

Caricare su YouTube (non in elenco) e incollare link nel modulo SMS.
