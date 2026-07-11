# Play Store — release 1.2.45 (versionCode 66)

## File da caricare

Dopo build release prod:

```powershell
.\gradlew.bat bundleProdRelease renameReleaseArtifacts
```

| File | Percorso |
|------|----------|
| **AAB (Play)** | `app\build\outputs\bundle\prodRelease\geoHELP-1.2.45-release.aab` |
| APK test | `app\build\outputs\apk\prod\release\geoHELP-1.2.45-release.apk` |

Play Console → **Testa e rilascia** → **Produzione** (o test interno) → **Crea nuova release** → carica l’**AAB**.

---

## Dove aggiornare i testi

| Cosa | Menu Play Console |
|------|-------------------|
| Descrizione breve / lunga | **Espandi la base utenti** → **Scheda dello store** |
| Note sulla release | **Testa e rilascia** → release **1.2.45** → **Note sulla release** |
| Privacy policy URL | **Contenuti app** → **Informativa sulla privacy** |

Testi pronti: `play-store-listing.md`

---

## Note sulla release (IT)

```
Prima release pubblica / aggiornamento major geoHELP 1.2.45

• Barra navigazione: Chiama 112, Trekking, SOS, Set, Info + ordinanze turistiche (PDF)
• Sentieri e P.O.I. per comune, tracce TRACK e GO TO sulla mappa
• Ordinanze turistiche consultabili per comune
• SOS manuale con GPS e dati profilo; MAN DOWN con SMS automatici
• Profilo, consensi, dati medici opzionali con PIN
• Italiano e inglese
• Patrocinio Comune di Sestriere, Cesana Torinese, Consorzio Turismo Sestriere
```

## Release notes (EN)

```
geoHELP 1.2.45

• Bottom bar: Call 112, Trekking, SOS, Settings, Info + tourist ordinances (PDF)
• Trails and POI by municipality, TRACK and GO TO on map
• Tourist ordinances PDF by municipality
• Manual SOS with GPS; optional MAN DOWN automatic SMS
• Profile, consents, optional PIN-protected medical data
• Italian and English
• Patronage: Sestriere, Cesana Torinese, Consorzio Turismo Sestriere
```

---

## Checklist prima della pubblicazione

- [ ] Lettera autorizzazione Turismo Torino (se richiesta da Google / patrocinio)
- [ ] AAB 1.2.45 firmato caricato
- [ ] Descrizione breve e lunga IT/EN aggiornate (`play-store-listing.md`)
- [ ] URL privacy policy live
- [ ] PDF guida utente online + link in descrizione (opzionale ma consigliato)
- [ ] Screenshot aggiornati (SOS, mappa, ordinanze, Set)
- [ ] Questionario permessi (posizione, SMS) allineato a `play-store-permissions-review.md`
