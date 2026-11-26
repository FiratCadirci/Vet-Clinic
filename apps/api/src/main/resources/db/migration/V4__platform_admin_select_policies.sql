DROP POLICY IF EXISTS platform_admin_select ON vet.organizations;
CREATE POLICY platform_admin_select ON vet.organizations
    FOR SELECT
    TO vet_app
    USING ((SELECT vet.is_platform_admin()));

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
          AND c.table_name NOT IN ('platform_admin_access_log')
    LOOP
        EXECUTE format('DROP POLICY IF EXISTS platform_admin_select ON vet.%I', r.table_name);
        EXECUTE format(
            'CREATE POLICY platform_admin_select ON vet.%I FOR SELECT TO vet_app USING ((SELECT vet.is_platform_admin()))',
            r.table_name
        );
    END LOOP;
END;
$$;

