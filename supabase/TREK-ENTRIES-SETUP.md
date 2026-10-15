# Sentieri e P.O.I. remoti — setup Supabase

Come le ordinanze: **una sola migration**, poi carichi i file dal dashboard e pubblichi dalla app (Set).

## 1. SQL Editor (progetto produzione)

Esegui tutto il file `migrations/0014_trek_entries.sql`.

Crea:

- colonna `profiles.can_manage_trails` (true se già gestisci le ordinanze)
- tabella `trek_entries`
- bucket Storage **`trails`**

## 2. Carica i file

**Storage** → bucket **`trails`** → cartelle consigliate:

| Tipo | Percorso esempio |
|------|------------------|
| PDF sentiero | `pdf/SESTRIERE/22_nome.pdf` |
| TRK sentiero | `trk/SESTRIERE/22_nome.trk` |
| Icona P.O.I. | `wp/CESANA/fontana.png` |

## 3. Pubblica dall’app

**Set → Gestione sentieri e P.O.I.**

- **Aggiungi sentiero**: comune, nome, numero, descrizione, percorsi PDF/TRK (senza dominio)
- **Aggiungi P.O.I.**: comune, nome, descrizione, lat/lon, icona PNG opzionale

Se le coordinate del sentiero sono vuote, l’app prova a leggerle dal **primo punto del TRK**.

## 4. Flag profilo (se non vedi il pannello)

Table Editor → `profiles` → tuo utente:

`can_manage_trails` = **true**

Poi esci e rientra dall’app.
