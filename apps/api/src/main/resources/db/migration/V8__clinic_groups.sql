-- Klinik grubu: Organizasyon -> Klinik grubu -> Şube (vet.clinics).
-- Aynı gruptaki şubeler müşteri/hayvan verisini paylaşır; gruplar birbirinin verisini göremez.

CREATE TABLE vet.clinic_groups (
    id                  uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id     uuid NOT NULL REFERENCES vet.organizations(id),
    code                varchar(40) NOT NULL,
    name                varchar(200) NOT NULL,
    is_active           boolean NOT NULL DEFAULT true,
    created_at          timestamptz NOT NULL DEFAULT now(),
    updated_at          timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT uq_clinic_groups_org_code UNIQUE (organization_id, code),
    CONSTRAINT uq_clinic_groups_org_id UNIQUE (organization_id, id)
);

ALTER TABLE vet.clinics ADD COLUMN clinic_group_id uuid;

INSERT INTO vet.clinic_groups (organization_id, code, name, is_active)
SELECT c.organization_id, c.code, c.name, c.is_active
FROM vet.clinics c;

UPDATE vet.clinics c
SET clinic_group_id = g.id
FROM vet.clinic_groups g
WHERE g.organization_id = c.organization_id
  AND g.code = c.code;

ALTER TABLE vet.clinics ALTER COLUMN clinic_group_id SET NOT NULL;
ALTER TABLE vet.clinics
    ADD CONSTRAINT fk_clinics_clinic_group
    FOREIGN KEY (organization_id, clinic_group_id) REFERENCES vet.clinic_groups (organization_id, id);
CREATE INDEX ix_clinics_group ON vet.clinics (organization_id, clinic_group_id);

-- Grup verilmeden eklenen şube için kendi adıyla yeni bir grup açılır.
CREATE OR REPLACE FUNCTION vet.assign_default_clinic_group()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    IF NEW.clinic_group_id IS NULL THEN
        INSERT INTO vet.clinic_groups (organization_id, code, name, is_active)
        VALUES (NEW.organization_id, NEW.code, NEW.name, NEW.is_active)
        ON CONFLICT (organization_id, code) DO UPDATE SET updated_at = now()
        RETURNING id INTO NEW.clinic_group_id;
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_clinics_assign_default_group
BEFORE INSERT ON vet.clinics
FOR EACH ROW EXECUTE FUNCTION vet.assign_default_clinic_group();

ALTER TABLE vet.clinic_groups ENABLE ROW LEVEL SECURITY;
ALTER TABLE vet.clinic_groups FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON vet.clinic_groups
USING (organization_id = NULLIF(current_setting('app.organization_id', true), '')::uuid)
WITH CHECK (organization_id = NULLIF(current_setting('app.organization_id', true), '')::uuid);

-- Kullanıcının aktif rolü olduğu herhangi bir şube, hedef şubeyle aynı klinik grubundaysa true.
CREATE OR REPLACE FUNCTION vet.current_user_has_clinic_group_access(p_organization_id uuid, p_clinic_id uuid)
RETURNS boolean
LANGUAGE plpgsql
STABLE
AS $$
DECLARE
    current_user_id uuid;
BEGIN
    current_user_id := NULLIF(current_setting('app.user_id', true), '')::uuid;
    IF current_user_id IS NULL OR p_organization_id IS NULL OR p_clinic_id IS NULL THEN
        RETURN false;
    END IF;

    RETURN EXISTS (
        SELECT 1
        FROM vet.clinics target
        JOIN vet.clinics own
          ON own.organization_id = target.organization_id
         AND own.clinic_group_id = target.clinic_group_id
        JOIN vet.user_clinic_roles ucr
          ON ucr.organization_id = own.organization_id AND ucr.clinic_id = own.id
        JOIN vet.app_users u
          ON u.organization_id = ucr.organization_id AND u.id = ucr.user_id
        WHERE target.organization_id = p_organization_id
          AND target.id = p_clinic_id
          AND ucr.user_id = current_user_id
          AND u.is_active
          AND ucr.valid_from <= CURRENT_DATE
          AND (ucr.valid_to IS NULL OR ucr.valid_to >= CURRENT_DATE)
    );
END;
$$;

-- Erişim eşlemeleri: aynı gruptaki şubeler görür, başka gruba erişim verilemez.
DROP POLICY IF EXISTS tenant_branch_isolation ON vet.client_clinic_access;
CREATE POLICY tenant_branch_isolation ON vet.client_clinic_access
USING (
    organization_id = NULLIF(current_setting('app.organization_id', true), '')::uuid
    AND vet.current_user_has_clinic_group_access(organization_id, clinic_id)
)
WITH CHECK (
    organization_id = NULLIF(current_setting('app.organization_id', true), '')::uuid
    AND vet.current_user_has_clinic_group_access(organization_id, clinic_id)
);

DROP POLICY IF EXISTS tenant_branch_isolation ON vet.animal_clinic_access;
CREATE POLICY tenant_branch_isolation ON vet.animal_clinic_access
USING (
    organization_id = NULLIF(current_setting('app.organization_id', true), '')::uuid
    AND vet.current_user_has_clinic_group_access(organization_id, clinic_id)
)
WITH CHECK (
    organization_id = NULLIF(current_setting('app.organization_id', true), '')::uuid
    AND vet.current_user_has_clinic_group_access(organization_id, clinic_id)
);

DROP POLICY IF EXISTS tenant_branch_isolation ON vet.clients;
CREATE POLICY tenant_branch_isolation ON vet.clients
USING (
    organization_id = NULLIF(current_setting('app.organization_id', true), '')::uuid
    AND (
        app_user_id = NULLIF(current_setting('app.user_id', true), '')::uuid
        OR EXISTS (
            SELECT 1 FROM vet.client_clinic_access ca
            WHERE ca.organization_id = clients.organization_id
              AND ca.client_id = clients.id
              AND ca.revoked_at IS NULL
              AND vet.current_user_has_clinic_group_access(ca.organization_id, ca.clinic_id)
        )
    )
)
WITH CHECK (organization_id = NULLIF(current_setting('app.organization_id', true), '')::uuid);

DROP POLICY IF EXISTS tenant_branch_isolation ON vet.animals;
CREATE POLICY tenant_branch_isolation ON vet.animals
USING (
    organization_id = NULLIF(current_setting('app.organization_id', true), '')::uuid
    AND (
        EXISTS (
            SELECT 1 FROM vet.animal_clinic_access aa
            WHERE aa.organization_id = animals.organization_id
              AND aa.animal_id = animals.id
              AND aa.revoked_at IS NULL
              AND vet.current_user_has_clinic_group_access(aa.organization_id, aa.clinic_id)
        )
        OR EXISTS (
            SELECT 1 FROM vet.user_animal_access ua
            WHERE ua.organization_id = animals.organization_id
              AND ua.animal_id = animals.id
              AND ua.user_id = NULLIF(current_setting('app.user_id', true), '')::uuid
              AND ua.revoked_at IS NULL
        )
    )
)
WITH CHECK (organization_id = NULLIF(current_setting('app.organization_id', true), '')::uuid);

DROP POLICY IF EXISTS owner_or_clinic_animal_access ON vet.user_animal_access;
CREATE POLICY owner_or_clinic_animal_access ON vet.user_animal_access
USING (
    organization_id = NULLIF(current_setting('app.organization_id', true), '')::uuid
    AND (
        user_id = NULLIF(current_setting('app.user_id', true), '')::uuid
        OR EXISTS (
            SELECT 1 FROM vet.animal_clinic_access aa
            WHERE aa.organization_id = user_animal_access.organization_id
              AND aa.animal_id = user_animal_access.animal_id
              AND aa.revoked_at IS NULL
              AND vet.current_user_has_clinic_group_access(aa.organization_id, aa.clinic_id)
        )
    )
)
WITH CHECK (
    organization_id = NULLIF(current_setting('app.organization_id', true), '')::uuid
    AND EXISTS (
        SELECT 1 FROM vet.animal_clinic_access aa
        WHERE aa.organization_id = user_animal_access.organization_id
          AND aa.animal_id = user_animal_access.animal_id
          AND aa.revoked_at IS NULL
          AND vet.current_user_has_clinic_group_access(aa.organization_id, aa.clinic_id)
    )
);

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'vet_app') THEN
        GRANT SELECT, INSERT, UPDATE, DELETE ON vet.clinic_groups TO vet_app;
    END IF;
END;
$$;
