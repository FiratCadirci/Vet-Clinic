CREATE TABLE IF NOT EXISTS vet.platform_admin_access_log (
    id                  bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    platform_admin_id   uuid NOT NULL REFERENCES vet.platform_admins(id),
    operation           varchar(10) NOT NULL CHECK (operation IN ('INSERT', 'UPDATE', 'DELETE')),
    table_name          varchar(120) NOT NULL,
    organization_id     uuid,
    row_pk              text,
    old_row             jsonb,
    new_row             jsonb,
    occurred_at         timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS ix_platform_admin_access_log_admin_time
    ON vet.platform_admin_access_log (platform_admin_id, occurred_at DESC);

CREATE INDEX IF NOT EXISTS ix_platform_admin_access_log_org_time
    ON vet.platform_admin_access_log (organization_id, occurred_at DESC);

CREATE OR REPLACE FUNCTION vet.log_platform_admin_write()
RETURNS trigger
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = vet, pg_temp
AS $$
DECLARE
    current_platform_admin_id uuid;
    old_json jsonb;
    new_json jsonb;
    current_organization_id uuid;
    current_row_pk text;
BEGIN
    IF NOT vet.is_platform_admin() THEN
        IF TG_OP = 'DELETE' THEN
            RETURN OLD;
        END IF;
        RETURN NEW;
    END IF;

    current_platform_admin_id := nullif(current_setting('app.platform_admin_id', true), '')::uuid;
    IF current_platform_admin_id IS NULL THEN
        IF TG_OP = 'DELETE' THEN
            RETURN OLD;
        END IF;
        RETURN NEW;
    END IF;

    IF TG_OP = 'DELETE' THEN
        old_json := to_jsonb(OLD);
        new_json := NULL;
    ELSE
        old_json := CASE WHEN TG_OP = 'UPDATE' THEN to_jsonb(OLD) ELSE NULL END;
        new_json := to_jsonb(NEW);
    END IF;

    current_organization_id := COALESCE(
        nullif(new_json ->> 'organization_id', '')::uuid,
        nullif(old_json ->> 'organization_id', '')::uuid
    );

    IF current_organization_id IS NULL AND TG_TABLE_NAME = 'organizations' THEN
        current_organization_id := COALESCE(
            nullif(new_json ->> 'id', '')::uuid,
            nullif(old_json ->> 'id', '')::uuid
        );
    END IF;

    current_row_pk := COALESCE(
        nullif(new_json ->> 'id', ''),
        nullif(old_json ->> 'id', '')
    );

    INSERT INTO vet.platform_admin_access_log (
        platform_admin_id,
        operation,
        table_name,
        organization_id,
        row_pk,
        old_row,
        new_row
    ) VALUES (
        current_platform_admin_id,
        TG_OP,
        TG_TABLE_NAME,
        current_organization_id,
        current_row_pk,
        old_json,
        new_json
    );

    IF TG_OP = 'DELETE' THEN
        RETURN OLD;
    END IF;
    RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS trg_platform_admin_access_log ON vet.organizations;
CREATE TRIGGER trg_platform_admin_access_log
AFTER INSERT OR UPDATE OR DELETE ON vet.organizations
FOR EACH ROW EXECUTE FUNCTION vet.log_platform_admin_write();

DO $$
DECLARE
    r record;
BEGIN
    FOR r IN
        SELECT DISTINCT c.table_name
        FROM information_schema.columns c
        JOIN information_schema.tables t
          ON t.table_schema = c.table_schema
         AND t.table_name = c.table_name
         AND t.table_type = 'BASE TABLE'
        WHERE c.table_schema = 'vet'
          AND c.column_name = 'organization_id'
          AND c.table_name <> 'platform_admin_access_log'
    LOOP
        EXECUTE format('DROP TRIGGER IF EXISTS trg_platform_admin_access_log ON vet.%I', r.table_name);
        EXECUTE format(
            'CREATE TRIGGER trg_platform_admin_access_log AFTER INSERT OR UPDATE OR DELETE ON vet.%I FOR EACH ROW EXECUTE FUNCTION vet.log_platform_admin_write()',
            r.table_name
        );
    END LOOP;
END;
$$;

