-- Giriş ekranındaki organizasyon combobox'ı için oturum öncesi liste. Yalnızca kod ve ad döner.
CREATE OR REPLACE FUNCTION vet.list_active_organizations()
RETURNS TABLE (
    organization_code text,
    organization_name text
)
LANGUAGE sql
STABLE
SECURITY DEFINER
SET search_path = vet, pg_temp
AS $$
    SELECT o.code::text, o.name::text
      FROM vet.organizations o
     WHERE o.is_active = true
     ORDER BY o.name, o.code;
$$;

REVOKE ALL ON FUNCTION vet.list_active_organizations() FROM PUBLIC;
GRANT EXECUTE ON FUNCTION vet.list_active_organizations() TO vet_app;
