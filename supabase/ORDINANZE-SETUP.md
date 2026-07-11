# Ordinanze turistiche — setup Supabase

## Errore «relation public.profiles does not exist»

Significa che sul **progetto Supabase dove stai eseguendo la query** non è mai stato creato lo schema base geoHELP.

L’app in produzione usa un progetto dove `profiles` **esiste già** (login, profilo, SOS). Se stai sul SQL Editor del progetto **sbagliato** o su un DB **vuoto**, vedi questo errore.

### Verifica rapida

Nel SQL Editor:

```sql
select table_name
from information_schema.tables
where table_schema = 'public'
order by table_name;
```

Se **non** compare `profiles` → segui i passi sotto.

---

## Ordine corretto (progetto nuovo o incompleto)

### 1. Schema base (una sola volta)

SQL Editor → incolla tutto **`supabase/schema.sql`** → Run.

Crea: `profiles`, `medical_data`, `consents`, `sos_recipients`, RLS, trigger.

### 2. Migrazioni incrementali (in ordine numerico)

Esegui **una query per file**, nell’ordine:

| File |
|------|
| `migrations/0002_sos_recipients_anon_select.sql` |
| `migrations/0003_medical_data_plain_columns.sql` |
| `migrations/0004_profiles_birth_year_optional_last_name.sql` | (no-op se hai già `schema.sql` recente) |
| `migrations/0005_profiles_drop_last_name_and_birth_date.sql` |
| `migrations/0006_blood_group_to_medical_data.sql` |
| `migrations/0009_profiles_email.sql` |
| `migrations/0010_sms_events.sql` |
| `migrations/0011_sos_recipients_admin_manage.sql` |
| `migrations/0012_profiles_user_phone.sql` |
| `migrations/0012_sos_recipients_admin_crud.sql` |
| `migrations/0013_tourist_ordinances.sql` |

(Salta `0007` e `0008` se non fai reset dev.)

### 3. Seed ordinanze

1. **Storage** → bucket `ordinances` (creato da 0013) → carica PDF:
   - `CESANA/260628.pdf`, `260630.pdf`, `260702.pdf`
   - `SESTRIERE/260706.pdf`
2. SQL Editor → **`seed_tourist_ordinances.sql`**

---

## Progetto già usato dall’APK (consigliato)

Usa lo **stesso** progetto le cui URL/key sono in `local.properties` (`SUPABASE_URL_*` / `SUPABASE_ANON_KEY_*`).

Lì `profiles` dovrebbe già esserci: esegui **solo** `0013_tourist_ordinances.sql` + seed + upload PDF.

Se anche lì manca `profiles`, lo schema non è mai stato applicato su quel progetto → parte da **schema.sql** (step 1).
