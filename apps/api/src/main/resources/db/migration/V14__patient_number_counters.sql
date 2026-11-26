-- Hasta numarası (patient_no) organizasyon bazlı, boşluksuz sayaçla üretilir.
-- Sayaç satırı UPDATE ile kilitlendiği için eşzamanlı kayıtlar aynı numarayı alamaz.
CREATE TABLE IF NOT EXISTS vet.patient_number_counters (
    organization_id uuid PRIMARY KEY REFERENCES vet.organizations(id),
    last_value      bigint NOT NULL DEFAULT 0 CHECK (last_value >= 0),
    updated_at      timestamptz NOT NULL DEFAULT now()
);

ALTER TABLE vet.patient_number_counters ENABLE ROW LEVEL SECURITY;
ALTER TABLE vet.patient_number_counters FORCE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS tenant_isolation ON vet.patient_number_counters;
CREATE POLICY tenant_isolation ON vet.patient_number_counters
    USING (organization_id = nullif(current_setting('app.organization_id', true), '')::uuid)
    WITH CHECK (organization_id = nullif(current_setting('app.organization_id', true), '')::uuid);

DROP POLICY IF EXISTS platform_admin_select ON vet.patient_number_counters;
CREATE POLICY platform_admin_select ON vet.patient_number_counters
    FOR SELECT
    TO vet_app
    USING ((SELECT vet.is_platform_admin()));
