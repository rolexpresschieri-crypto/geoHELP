-- Dev/test: azzera testo libero legacy in conditions prima del formato codici (CA,PO,...).
-- L'app salva solo abbreviazioni separate da virgola.

update public.medical_data
set conditions = null
where conditions is not null
  and trim(conditions) <> '';
