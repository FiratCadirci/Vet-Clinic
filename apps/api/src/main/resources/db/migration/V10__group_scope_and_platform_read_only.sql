-- Platform admin yalnızca okuyabilir; yazma policy'leri kaldırılır.
DO $$
DECLARE
    r record;
BEGIN
    FOR r IN
        SELECT tablename, policyname
        FROM pg_policies
        WHERE schemaname = 'vet'
          AND policyname IN ('platform_admin_insert', 'platform_admin_update', 'platform_admin_delete')
    LOOP
        EXECUTE format('DROP POLICY %I ON vet.%I', r.policyname, r.tablename);
    END LOOP;
END;
$$;

-- İki şubenin aynı klinik grubunda olup olmadığını döner.
CREATE OR REPLACE FUNCTION vet.clinics_in_same_group(p_organization_id uuid, p_clinic_a uuid, p_clinic_b uuid)
RETURNS boolean
LANGUAGE sql
STABLE
AS $$
    SELECT EXISTS (
        SELECT 1
        FROM vet.clinics a
        JOIN vet.clinics b
          ON b.organization_id = a.organization_id
         AND b.clinic_group_id = a.clinic_group_id
        WHERE a.organization_id = p_organization_id
          AND a.id = p_clinic_a
          AND b.id = p_clinic_b
    );
$$;

-- Operasyonel tablolar (randevu, muayene, fatura vb.) şube yerine klinik grubu kapsamında çalışır.
CREATE OR REPLACE FUNCTION vet.rewrite_clinic_scope(p_expr text)
RETURNS text
LANGUAGE sql
IMMUTABLE
AS $$
    SELECT regexp_replace(
        replace(p_expr, 'current_user_has_clinic_access', 'current_user_has_clinic_group_access'),
        '\((aa|ca)\.clinic_id = (\w+)\.clinic_id\)',
        'vet.clinics_in_same_group(\1.organization_id, \1.clinic_id, \2.clinic_id)',
        'g'
    );
$$;

DO $$
DECLARE
    p record;
    new_qual text;
    new_check text;
    stmt text;
BEGIN
    FOR p IN
        SELECT tablename, policyname, qual, with_check
        FROM pg_policies
        WHERE schemaname = 'vet'
          AND policyname NOT LIKE 'platform_admin_%'
    LOOP
        new_qual := vet.rewrite_clinic_scope(p.qual);
        new_check := vet.rewrite_clinic_scope(p.with_check);
        IF new_qual IS DISTINCT FROM p.qual OR new_check IS DISTINCT FROM p.with_check THEN
            stmt := format('ALTER POLICY %I ON vet.%I', p.policyname, p.tablename);
            IF p.qual IS NOT NULL THEN
                stmt := stmt || format(' USING (%s)', new_qual);
            END IF;
            IF p.with_check IS NOT NULL THEN
                stmt := stmt || format(' WITH CHECK (%s)', new_check);
            END IF;
            EXECUTE stmt;
        END IF;
    END LOOP;
END;
$$;

DROP FUNCTION vet.rewrite_clinic_scope(text);
