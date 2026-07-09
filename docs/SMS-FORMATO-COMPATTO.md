---
title: geoHELP — Formato SMS compatto (legenda operativa)
subtitle: Per destinatari SOS / volontari · App dev 1.0.64 · Maggio 2026
---

# geoHELP — Legenda SMS compatto

**Destinatari:** numeri in `sos_recipients` (primari, backup), centrale, volontari.  
**Non** va inviata agli utenti dell’app: è una guida per **chi riceve** gli SMS.

> Tutti gli SMS geoHELP usano lo stesso “dialetto” breve per ridurre costi (segmenti Twilio) e leggere più in fretto.  
> Solo la **prima riga** (header) cambia tra SOS manuale e MAN DOWN.

---

## Tipi di messaggio (prima riga)

| Header | Significato |
|--------|-------------|
| **SOS geoHELP** | Richiesta **manuale** dalla tab Help (utente ha preparato l’invio in Messaggi) |
| **MAN DOWN geoHELP** | Allarme **automatico** caduta/urto (countdown 60 s) |
| **MD TRACCIA n/4 geoHELP** | Aggiornamento posizione (movimento dopo MAN DOWN) |
| **MD NO SEG geoHELP** | Invio automatico interrotto (rete/permessi) |

Mittente Twilio in produzione: **geoHELP** (alfanumerico).

---

## Campi nel corpo (sempre uguali)

| Codice | Significato | Esempio |
|--------|-------------|---------|
| **N:** | Nome (+ anno nascita se presente) | `N:Roberto 1963` |
| **T:** | Telefono utente (richiamo) | `T:+393711500701` |
| **Pos:** | Lat, lon (+ alt, precisione, velocità se c’è) | `Pos:45.0069,7.8245 a326 acc19` |
| **R:** | Tipo richiesta (solo SOS manuale) | `R:INCIDENTE` |
| **Nt:** | Note utente (solo SOS) | `Nt:sciovia ghiacciata` |
| **Med:** | Sintesi medica (se consenso) | `Med:A+ \| ALL:... \| PAT:...` |
| **Dir:** | Direzione (solo traccia) | `Dir:NNE` |
| **D:** | Distanza da punto iniziale traccia | `D:45m` |

**Pos:** coordinate con **4 decimali**; `a` = altitudine metri; `acc` = precisione fix metri; `v` = velocità km/h (traccia).

**Med:** priorità a gruppo sanguigno e allergie; testo lungo può essere **troncato** (~88 caratteri).

---

## Esempi

### SOS manuale (Help)

```text
SOS geoHELP
N:Roberto 1963 T:+393711500701
R:INCIDENTE Nt:-
Pos:45.0069,7.8245 a326
Med:A+ | ALL:AULIN | PAT:CA,PO
```

### MAN DOWN iniziale

```text
MAN DOWN geoHELP
N:Roberto 1963 T:+393711500701
Pos:45.0069,7.8245 a326 acc19
Med:A+ | ALL:AULIN | PAT:CA,PO
```

### Traccia 2/4

```text
MD TRACCIA 2/4 geoHELP
N:Roberto
Pos:45.0100,7.8300 a328 acc18 v6
Dir:NNE D:52m
```

### Perso segnale

```text
MD NO SEG geoHELP
N:Roberto T:+393711500701
Auto interrotto. Ultima pos non aggiornata.
```

---

## Segmenti SMS (fatturazione Twilio)

- Un “SMS” sul telefono può essere **1 messaggio** ma **più segmenti** in fattura (~160 caratteri per segmento).
- Obiettivo formato compatto: **2–3 segmenti** per MAN DOWN iniziale (prima spesso 6).
- **1 invio API** = 1 riga in Twilio Logs (non confondere con i segmenti).

---

## Cosa non è in questo SMS

- Nessuna risposta al mittente geoHELP (SMS unidirezionale).
- Il testo **non** sostituisce il **112** o i canali istituzionali.

---

*Documento operativo geoHELP — distribuire al team SOS (PDF allegato).*
