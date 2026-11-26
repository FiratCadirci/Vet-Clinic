CREATE OR REPLACE FUNCTION vet.is_platform_admin()
RETURNS boolean
LANGUAGE sql
STABLE
SECURITY DEFINER
SET search_path = vet, pg_temp
AS $$
    SELECT EXISTS (
        SELECT 1
          FROM vet.platform_admins pa
         WHERE pa.id = nullif(current_setting('app.platform_admin_id', true), '')::uuid
           AND pa.is_active = true
    );
$$;

REVOKE ALL ON FUNCTION vet.is_platform_admin() FROM PUBLIC;
GRANT EXECUTE ON FUNCTION vet.is_platform_admin() TO vet_app;

