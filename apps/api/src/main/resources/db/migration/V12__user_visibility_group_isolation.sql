-- app_users ve user_clinic_roles artık org genelinde görünmez: kullanıcı yalnızca kendisini ve
-- aynı klinik grubundaki kullanıcıları/rol atamalarını görür. Organizasyon yöneticisi istisnası yoktur.
-- SECURITY DEFINER fonksiyonlar V11'deki gibi superuser/BYPASSRLS sahibi gerektirir.

-- Oturum öncesi (login) e-postadan kullanıcı id'sini çözer; satırın kendisini ifşa etmez.
CREATE OR REPLACE FUNCTION vet.resolve_login_user_id(p_organization_id uuid, p_email text)
RETURNS uuid
LANGUAGE sql
STABLE
SECURITY DEFINER
SET search_path = vet, pg_temp
AS $$
    SELECT u.id
    FROM vet.app_users u
    WHERE u.organization_id = p_organization_id
      AND lower(u.email) = lower(p_email)
    LIMIT 1;
$$;

-- Hedef kullanıcının herhangi bir şubede rolü varsa ve o şube mevcut kullanıcının klinik grubundaysa true.
CREATE OR REPLACE FUNCTION vet.user_shares_clinic_group_with_current_user(p_organization_id uuid, p_user_id uuid)
RETURNS boolean
LANGUAGE sql
STABLE
SECURITY DEFINER
SET search_path = vet, pg_temp
AS $$
    SELECT EXISTS (
        SELECT 1
        FROM vet.user_clinic_roles t
        JOIN vet.clinics c
          ON c.organization_id = t.organization_id AND c.id = t.clinic_id
        WHERE t.organization_id = p_organization_id
          AND t.user_id = p_user_id
          AND vet.current_user_in_clinic_group(c.organization_id, c.clinic_group_id)
    );
$$;

-- user_clinic_roles
DROP POLICY IF EXISTS tenant_isolation ON vet.user_clinic_roles;
DROP POLICY IF EXISTS ucr_select ON vet.user_clinic_roles;
DROP POLICY IF EXISTS ucr_insert ON vet.user_clinic_roles;
DROP POLICY IF EXISTS ucr_update ON vet.user_clinic_roles;
DROP POLICY IF EXISTS ucr_delete ON vet.user_clinic_roles;

CREATE POLICY ucr_select ON vet.user_clinic_roles FOR SELECT
    USING (
        organization_id = NULLIF(current_setting('app.organization_id', true), '')::uuid
        AND (
            user_id = NULLIF(current_setting('app.user_id', true), '')::uuid
            OR vet.current_user_has_clinic_group_access(organization_id, clinic_id)
        )
    );

CREATE POLICY ucr_insert ON vet.user_clinic_roles FOR INSERT
    WITH CHECK (
        organization_id = NULLIF(current_setting('app.organization_id', true), '')::uuid
        AND vet.current_user_has_clinic_group_access(organization_id, clinic_id)
    );

CREATE POLICY ucr_update ON vet.user_clinic_roles FOR UPDATE
    USING (
        organization_id = NULLIF(current_setting('app.organization_id', true), '')::uuid
        AND vet.current_user_has_clinic_group_access(organization_id, clinic_id)
    )
    WITH CHECK (
        organization_id = NULLIF(current_setting('app.organization_id', true), '')::uuid
        AND vet.current_user_has_clinic_group_access(organization_id, clinic_id)
    );

CREATE POLICY ucr_delete ON vet.user_clinic_roles FOR DELETE
    USING (
        organization_id = NULLIF(current_setting('app.organization_id', true), '')::uuid
        AND vet.current_user_has_clinic_group_access(organization_id, clinic_id)
    );

-- app_users: yeni kullanıcı henüz rolsüz olduğundan INSERT yalnızca organizasyonla sınırlıdır
-- (satır, rol atanana kadar kimseye görünmez).
DROP POLICY IF EXISTS tenant_isolation ON vet.app_users;
DROP POLICY IF EXISTS app_users_select ON vet.app_users;
DROP POLICY IF EXISTS app_users_insert ON vet.app_users;
DROP POLICY IF EXISTS app_users_update ON vet.app_users;
DROP POLICY IF EXISTS app_users_delete ON vet.app_users;

CREATE POLICY app_users_select ON vet.app_users FOR SELECT
    USING (
        organization_id = NULLIF(current_setting('app.organization_id', true), '')::uuid
        AND (
            id = NULLIF(current_setting('app.user_id', true), '')::uuid
            OR vet.user_shares_clinic_group_with_current_user(organization_id, id)
        )
    );

CREATE POLICY app_users_insert ON vet.app_users FOR INSERT
    WITH CHECK (organization_id = NULLIF(current_setting('app.organization_id', true), '')::uuid);

CREATE POLICY app_users_update ON vet.app_users FOR UPDATE
    USING (
        organization_id = NULLIF(current_setting('app.organization_id', true), '')::uuid
        AND (
            id = NULLIF(current_setting('app.user_id', true), '')::uuid
            OR vet.user_shares_clinic_group_with_current_user(organization_id, id)
        )
    )
    WITH CHECK (organization_id = NULLIF(current_setting('app.organization_id', true), '')::uuid);

CREATE POLICY app_users_delete ON vet.app_users FOR DELETE
    USING (
        organization_id = NULLIF(current_setting('app.organization_id', true), '')::uuid
        AND (
            id = NULLIF(current_setting('app.user_id', true), '')::uuid
            OR vet.user_shares_clinic_group_with_current_user(organization_id, id)
        )
    );
