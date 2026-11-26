CREATE UNIQUE INDEX IF NOT EXISTS ux_organizations_code_lower
    ON vet.organizations (lower(code));

CREATE OR REPLACE FUNCTION vet.resolve_organization_by_code(p_code text)
RETURNS uuid
LANGUAGE sql
STABLE
SECURITY DEFINER
SET search_path = vet, pg_temp
AS $$
    SELECT o.id
      FROM vet.organizations o
     WHERE lower(o.code) = lower(btrim(p_code))
       AND o.is_active = true
     LIMIT 1;
$$;

REVOKE ALL ON FUNCTION vet.resolve_organization_by_code(text) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION vet.resolve_organization_by_code(text) TO vet_app;

