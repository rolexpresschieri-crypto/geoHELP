# Testi scheda Play Store — geoHELP

Aggiornati per release **1.2.45** (giugno 2026).  
Patrocinio **Comune di Sestriere**, **Comune di Cesana Torinese**, **Consorzio Turismo Sestriere**.

Play Console → **Espandi la base utenti** → **Scheda dello store** → incolla nelle lingue IT / EN.

Note di rilascio per la prima pubblicazione / aggiornamento: vedi `play-store-release-1.2.45.md`.

---

## Descrizione breve (max 80 caratteri)

**Italiano (it-IT)** — 78 caratteri  
`SOS, sentieri, ordinanze PDF. GPS e MAN DOWN. Patrocinio Alta Valsusa.`

**English (en-GB)** — 77 caratteri  
`SOS, trails, PDF ordinances. GPS & MAN DOWN. Upper Susa Valley patronage.`

---

## Descrizione completa (max 4000 caratteri)

### Italiano

```
geoHELP è un’app privata di supporto in montagna e outdoor (Alta Valsusa: Sestriere, Cesana Torinese e territorio). Non sostituisce il 112 né i soccorsi pubblici: in caso di pericolo immediato chiama sempre il Numero Unico di Emergenza.

Sviluppata con il patrocinio del Comune di Sestriere, del Comune di Cesana Torinese e del Consorzio Turismo Sestriere (loghi e link in app).

EMERGENZA
• Pulsante Chiama 112 dalla barra in basso
• SOS: prepara SMS con GPS, tipo emergenza, note e dati medici (se consentiti); invio manuale da app Messaggi
• MAN DOWN (opzionale): rilevamento caduta/urto, sirena, conto alla rovescia 60 s annullabile, SMS automatici e traccia posizione

TREKKING E MAPPA
• Coordinate GPS, altitudine, precisione
• Sentieri da percorrere per comune (PDF scheda, traccia TRACK sulla mappa)
• Punti di interesse (P.O.I.) con GO TO e navigazione sulla mappa
• Mappa a schermo intero: layer, bussola, seguimento GPS

ORDINANZE TURISTICHE
• Striscia ORDINANZE: elenco PDF per comune (limitazioni, info utili)

PROFILO E SALUTE
• Account, consensi privacy, profilo (nome, telefono, contatto familiare)
• Dati medici opzionali protetti da PIN locale

INFO E LINGUE
• Link utili: Via Lattea, meteo, webcam, DAE
• Italiano e inglese

GUIDA UTENTE (PDF)
https://geohelp-mauve.vercel.app/guida/guida-utente.pdf

Requisiti: Android, GPS, rete mobile, SMS per SOS/MAN DOWN.

geoHELP non è un ente pubblico e non sostituisce il servizio di emergenza 112.
```

### English

```
geoHELP is a private support app for mountain and outdoor use (Upper Susa Valley: Sestriere, Cesana Torinese and area). It does not replace 112 or public rescue: in immediate danger always call the emergency number.

Developed with the patronage of the Municipality of Sestriere, the Municipality of Cesana Torinese and Consorzio Turismo Sestriere (logos and links in the app).

EMERGENCY
• Call 112 from the bottom bar
• SOS: prepares an SMS with GPS, emergency type, notes and medical summary (if consented); you send manually from Messages
• MAN DOWN (optional): fall/impact detection, siren, 60 s cancellable countdown, automatic SMS and position updates

TREKKING & MAP
• GPS coordinates, altitude, accuracy
• Trails by municipality (PDF sheet, TRACK on map)
• Points of interest (POI) with GO TO on the map
• Full-screen map: layers, compass, GPS follow mode

TOURIST ORDINANCES
• ORDINANCES tab: PDF list by municipality

PROFILE & HEALTH
• Account, privacy consents, profile (name, phone, family contact)
• Optional medical data protected by a local PIN

INFO & LANGUAGES
• Useful links: Via Lattea, weather, webcams, AED map
• Italian and English

USER GUIDE (PDF)
https://geohelp-mauve.vercel.app/guida/guida-utente.pdf

Requirements: Android, GPS, mobile network, SMS for SOS/MAN DOWN.

geoHELP is not a public authority and does not replace the 112 emergency service.
```

---

## Guida utente e Play Store — cosa si può fare

| Metodo | Consigliato? | Note |
|--------|--------------|------|
| **Incollare tutta la guida** nella descrizione lunga | No | Max **4000 caratteri**, solo testo; diventa illeggibile |
| **Link al PDF** nella descrizione lunga | **Sì** | Google lo consente; usa URL **https** pubblico |
| **Campo «Sito web»** in Play Console | **Sì** | Può puntare alla home o a una pagina «Guida» |
| **Allegare PDF** nella scheda Store | No | Play non ha upload manuale utente nella listing |
| **Guida dentro l’app** (tab Info / link) | Opzionale | Utile in futuro; non obbligatorio per la pubblicazione |

### File guida nel repo

| File | Uso |
|------|-----|
| `docs/geohelp/guida-utente.pdf` | PDF da pubblicare online |
| `docs/geohelp/geoHELP_guida-utente.pdf` | Copia con nome alternativo |
| `docs/geohelp/guida-utente.md` | Sorgente; rigenera con `node scripts/gen-user-guide-pdf.mjs` |

### Pubblicare il PDF (prima dello Store)

1. Carica `guida-utente.pdf` su **Vercel** / **GitHub Pages** (es. accanto a privacy).
2. Verifica che il link si apra **senza login** da browser mobile.
3. Sostituisci `[INSERISCI-URL-PUBBLICO]` nella descrizione sopra.
4. (Opzionale) Stesso URL nel campo **Sito web** o **Email di contatto** / pagina supporto.

Esempio struttura consigliata sul sito:

```
https://geohelp-mauve.vercel.app/
https://geohelp-mauve.vercel.app/privacy/
https://geohelp-mauve.vercel.app/guida/guida-utente.pdf
```

---

## Privacy e contatti (campi separati in Play Console)

| Campo | Esempio |
|-------|---------|
| **Informativa sulla privacy** | URL pagina privacy (obbligatorio) |
| **Sito web** | Home geoHELP o pagina guida |
| **Email assistenza** | rronco23@gmail.com o PEC |

Non confondere **descrizione Store** (marketing + funzioni) con **guida operativa** (PDF): meglio descrizione sintetica + link «Guida completa (PDF)».
