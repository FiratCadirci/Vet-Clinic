-- Muayene akışı, tedavi kalemleri, addendum, aşı kayıtları ve aşı şablonları.

-- 1) Yeni izin: klinik kayıt silme (yazma izninden ayrı).
INSERT INTO vet.permissions (code, description)
VALUES ('clinical.delete', 'Klinik kayıtları (muayene, tedavi, aşı) silme')
ON CONFLICT (code) DO NOTHING;

-- 2) Genel amaçlı organizasyon bazlı sayaç (muayene numarası vb.).
CREATE TABLE IF NOT EXISTS vet.sequence_counters (
    organization_id uuid NOT NULL REFERENCES vet.organizations(id),
    counter_key     varchar(40) NOT NULL,
    last_value      bigint NOT NULL DEFAULT 0 CHECK (last_value >= 0),
    updated_at      timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (organization_id, counter_key)
);
ALTER TABLE vet.sequence_counters ENABLE ROW LEVEL SECURITY;
ALTER TABLE vet.sequence_counters FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS tenant_isolation ON vet.sequence_counters;
CREATE POLICY tenant_isolation ON vet.sequence_counters
    USING (organization_id = nullif(current_setting('app.organization_id', true), '')::uuid)
    WITH CHECK (organization_id = nullif(current_setting('app.organization_id', true), '')::uuid);
DROP POLICY IF EXISTS platform_admin_select ON vet.sequence_counters;
CREATE POLICY platform_admin_select ON vet.sequence_counters
    FOR SELECT TO vet_app USING ((SELECT vet.is_platform_admin()));

-- 3) Muayene durumları ve kuralları.
UPDATE vet.encounters SET status = CASE status
    WHEN 'OPEN' THEN 'ADMISSION'
    WHEN 'IN_PROGRESS' THEN 'EXAMINATION'
    ELSE 'CLOSED' END
WHERE status IN ('OPEN', 'IN_PROGRESS', 'SIGNED', 'VOID');

ALTER TABLE vet.encounters DROP CONSTRAINT IF EXISTS encounters_status_check;
ALTER TABLE vet.encounters
    ADD CONSTRAINT encounters_status_check
    CHECK (status IN ('ADMISSION', 'EXAMINATION', 'DIAGNOSIS', 'PLAN', 'CLOSED'));
ALTER TABLE vet.encounters ALTER COLUMN status SET DEFAULT 'ADMISSION';
ALTER TABLE vet.encounters ADD COLUMN IF NOT EXISTS vet_notes text;

-- Bir randevuya aynı anda yalnızca bir açık muayene bağlanabilir.
CREATE UNIQUE INDEX IF NOT EXISTS uq_encounters_one_open_per_appointment
    ON vet.encounters (organization_id, appointment_id)
    WHERE appointment_id IS NOT NULL AND status <> 'CLOSED';

CREATE INDEX IF NOT EXISTS ix_encounters_animal_started
    ON vet.encounters (organization_id, animal_id, started_at DESC);

CREATE OR REPLACE FUNCTION vet.prevent_closed_encounter_change() RETURNS trigger
LANGUAGE plpgsql AS $$
BEGIN
    IF OLD.status = 'CLOSED' THEN
        RAISE EXCEPTION 'Kapatılmış muayene değiştirilemez veya silinemez; addendum ekleyin.'
            USING ERRCODE = '23514';
    END IF;
    IF TG_OP = 'DELETE' THEN
        RETURN OLD;
    END IF;
    RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS trg_encounters_closed_immutable ON vet.encounters;
CREATE TRIGGER trg_encounters_closed_immutable
    BEFORE UPDATE OR DELETE ON vet.encounters
    FOR EACH ROW EXECUTE FUNCTION vet.prevent_closed_encounter_change();

-- 4) Addendum (kapanmış muayeneye düzeltme/ek not).
CREATE TABLE IF NOT EXISTS vet.encounter_addenda (
    id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id uuid NOT NULL REFERENCES vet.organizations(id),
    encounter_id    uuid NOT NULL,
    clinic_id       uuid NOT NULL,
    note            text NOT NULL,
    created_by      uuid,
    created_at      timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT uq_encounter_addenda_org_id UNIQUE (organization_id, id),
    FOREIGN KEY (organization_id, encounter_id) REFERENCES vet.encounters (organization_id, id),
    FOREIGN KEY (organization_id, clinic_id) REFERENCES vet.clinics (organization_id, id),
    FOREIGN KEY (organization_id, created_by) REFERENCES vet.app_users (organization_id, id)
);
CREATE INDEX IF NOT EXISTS ix_encounter_addenda_encounter ON vet.encounter_addenda (organization_id, encounter_id);

-- 5) Muayeneye bağlı tedavi/işlem/ilaç kalemleri. Ürün referansı stok entegrasyonuna hazırlıktır (nullable).
CREATE TABLE IF NOT EXISTS vet.encounter_treatments (
    id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id uuid NOT NULL REFERENCES vet.organizations(id),
    encounter_id    uuid NOT NULL,
    item_type       varchar(20) NOT NULL CHECK (item_type IN ('PROCEDURE', 'MEDICATION')),
    name            varchar(200) NOT NULL,
    product_id      uuid,
    dose            numeric(14,5) CHECK (dose IS NULL OR dose > 0),
    dose_unit       varchar(40),
    route           varchar(80),
    frequency_text  varchar(150),
    duration_days   integer CHECK (duration_days IS NULL OR duration_days > 0),
    prescribed      boolean NOT NULL DEFAULT false,
    notes           text,
    performed_at    timestamptz NOT NULL DEFAULT now(),
    created_by      uuid,
    created_at      timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT uq_encounter_treatments_org_id UNIQUE (organization_id, id),
    FOREIGN KEY (organization_id, encounter_id) REFERENCES vet.encounters (organization_id, id),
    FOREIGN KEY (organization_id, product_id) REFERENCES vet.products (organization_id, id),
    FOREIGN KEY (organization_id, created_by) REFERENCES vet.app_users (organization_id, id)
);
CREATE INDEX IF NOT EXISTS ix_encounter_treatments_encounter ON vet.encounter_treatments (organization_id, encounter_id);

-- 6) Aşı kayıtları: ürün referansı nullable, ad/şube/lot/protokol bilgisi eklenir.
ALTER TABLE vet.vaccine_records ALTER COLUMN vaccine_product_id DROP NOT NULL;
ALTER TABLE vet.vaccine_records
    ADD COLUMN IF NOT EXISTS vaccine_name varchar(200),
    ADD COLUMN IF NOT EXISTS clinic_id uuid,
    ADD COLUMN IF NOT EXISTS created_by uuid,
    ADD COLUMN IF NOT EXISTS protocol_dose_id uuid,
    ADD COLUMN IF NOT EXISTS batch_number varchar(100),
    ADD COLUMN IF NOT EXISTS next_due_source varchar(20) NOT NULL DEFAULT 'NONE';

UPDATE vet.vaccine_records vr SET clinic_id = e.clinic_id
FROM vet.encounters e WHERE e.organization_id = vr.organization_id AND e.id = vr.encounter_id AND vr.clinic_id IS NULL;
UPDATE vet.vaccine_records vr SET vaccine_name = p.name
FROM vet.products p WHERE p.organization_id = vr.organization_id AND p.id = vr.vaccine_product_id AND vr.vaccine_name IS NULL;

ALTER TABLE vet.vaccine_records
    ALTER COLUMN vaccine_name SET NOT NULL,
    ALTER COLUMN clinic_id SET NOT NULL,
    ALTER COLUMN encounter_id SET NOT NULL;

ALTER TABLE vet.vaccine_records
    ADD CONSTRAINT vaccine_records_next_due_source_check CHECK (next_due_source IN ('NONE', 'TEMPLATE', 'VET_OVERRIDE')),
    ADD CONSTRAINT vaccine_records_next_due_check CHECK (next_due_at IS NULL OR next_due_at >= administered_at::date),
    ADD CONSTRAINT fk_vaccine_records_clinic FOREIGN KEY (organization_id, clinic_id) REFERENCES vet.clinics (organization_id, id),
    ADD CONSTRAINT fk_vaccine_records_created_by FOREIGN KEY (organization_id, created_by) REFERENCES vet.app_users (organization_id, id);

CREATE INDEX IF NOT EXISTS ix_vaccine_records_animal ON vet.vaccine_records (organization_id, animal_id, administered_at DESC);

-- 7) Aşı protokolleri (şablonlar): doz adı ve rapel aralığı.
ALTER TABLE vet.vaccine_protocols ADD COLUMN IF NOT EXISTS booster_interval_days integer
    CHECK (booster_interval_days IS NULL OR booster_interval_days > 0);
ALTER TABLE vet.vaccine_protocol_doses ADD COLUMN IF NOT EXISTS vaccine_name varchar(200);
ALTER TABLE vet.vaccine_protocol_doses
    ADD CONSTRAINT vaccine_protocol_doses_name_check CHECK (vaccine_name IS NOT NULL OR vaccine_product_id IS NOT NULL);

ALTER TABLE vet.vaccine_records
    ADD CONSTRAINT fk_vaccine_records_protocol_dose FOREIGN KEY (organization_id, protocol_dose_id)
        REFERENCES vet.vaccine_protocol_doses (organization_id, id);

-- Varsayılan taslak (DRAFT) kedi/köpek şablonları; klinik hekimi onaylayana kadar kullanılmaz.
CREATE OR REPLACE FUNCTION vet.seed_default_vaccine_protocols(p_organization_id uuid) RETURNS void
LANGUAGE plpgsql AS $$
DECLARE
    v_protocol uuid;
    v_species  record;
BEGIN
    FOR v_species IN SELECT id, code FROM vet.species WHERE code IN ('DOG', 'CAT') LOOP
        IF NOT EXISTS (SELECT 1 FROM vet.vaccine_protocols WHERE organization_id = p_organization_id AND code = v_species.code || '-CORE') THEN
            INSERT INTO vet.vaccine_protocols (organization_id, code, name, species_id, status, booster_interval_days, notes)
            VALUES (p_organization_id, v_species.code || '-CORE',
                    CASE v_species.code WHEN 'DOG' THEN 'Köpek karma aşı (DHPPi)' ELSE 'Kedi karma aşı (FVRCP)' END,
                    v_species.id, 'DRAFT', 365, 'Örnek şablon; kullanılmadan önce klinik hekimi tarafından doğrulanıp onaylanmalıdır.')
            RETURNING id INTO v_protocol;
            INSERT INTO vet.vaccine_protocol_doses (organization_id, protocol_id, dose_number, vaccine_name, recommended_age_days, interval_from_previous_days)
            VALUES (p_organization_id, v_protocol, 1, CASE v_species.code WHEN 'DOG' THEN 'Karma (DHPPi)' ELSE 'Karma (FVRCP)' END,
                    CASE v_species.code WHEN 'DOG' THEN 42 ELSE 56 END, NULL),
                   (p_organization_id, v_protocol, 2, CASE v_species.code WHEN 'DOG' THEN 'Karma (DHPPi)' ELSE 'Karma (FVRCP)' END, NULL, 21),
                   (p_organization_id, v_protocol, 3, CASE v_species.code WHEN 'DOG' THEN 'Karma (DHPPi)' ELSE 'Karma (FVRCP)' END, NULL, 21);
        END IF;
        IF NOT EXISTS (SELECT 1 FROM vet.vaccine_protocols WHERE organization_id = p_organization_id AND code = v_species.code || '-RABIES') THEN
            INSERT INTO vet.vaccine_protocols (organization_id, code, name, species_id, status, booster_interval_days, notes)
            VALUES (p_organization_id, v_species.code || '-RABIES', 'Kuduz aşısı', v_species.id, 'DRAFT', 365,
                    'Örnek şablon; kullanılmadan önce klinik hekimi tarafından doğrulanıp onaylanmalıdır.')
            RETURNING id INTO v_protocol;
            INSERT INTO vet.vaccine_protocol_doses (organization_id, protocol_id, dose_number, vaccine_name, recommended_age_days)
            VALUES (p_organization_id, v_protocol, 1, 'Kuduz', 90);
        END IF;
    END LOOP;
END;
$$;

SELECT vet.seed_default_vaccine_protocols(id) FROM vet.organizations;

-- 8) RLS: yeni tablolar muayene görünürlüğünü izler.
DO $$
DECLARE t text;
BEGIN
    FOREACH t IN ARRAY ARRAY['encounter_addenda', 'encounter_treatments'] LOOP
        EXECUTE format('ALTER TABLE vet.%I ENABLE ROW LEVEL SECURITY', t);
        EXECUTE format('ALTER TABLE vet.%I FORCE ROW LEVEL SECURITY', t);
        EXECUTE format('DROP POLICY IF EXISTS tenant_branch_isolation ON vet.%I', t);
        EXECUTE format($p$CREATE POLICY tenant_branch_isolation ON vet.%I
            USING (organization_id = nullif(current_setting('app.organization_id', true), '')::uuid
                   AND EXISTS (SELECT 1 FROM vet.encounters e WHERE e.organization_id = %I.organization_id AND e.id = %I.encounter_id))
            WITH CHECK (organization_id = nullif(current_setting('app.organization_id', true), '')::uuid
                   AND EXISTS (SELECT 1 FROM vet.encounters e WHERE e.organization_id = %I.organization_id AND e.id = %I.encounter_id))$p$,
            t, t, t, t, t);
        EXECUTE format('DROP POLICY IF EXISTS platform_admin_select ON vet.%I', t);
        EXECUTE format('CREATE POLICY platform_admin_select ON vet.%I FOR SELECT TO vet_app USING ((SELECT vet.is_platform_admin()))', t);
    END LOOP;
END $$;

-- 9) Uygulama rolü yetkileri (rol yoksa atlanır).
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'vet_app') THEN
        GRANT SELECT, INSERT, UPDATE, DELETE ON
            vet.patient_number_counters, vet.sequence_counters, vet.encounter_addenda, vet.encounter_treatments TO vet_app;
    END IF;
END $$;
