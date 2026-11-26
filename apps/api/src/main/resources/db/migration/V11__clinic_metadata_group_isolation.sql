-- clinic_groups ve clinics meta verisi yalnızca kullanıcının kendi klinik grubu ile sınırlanır.
-- Organizasyon içinde klinikler arası görünürlük ve "org yöneticisi" istisnası yoktur.
--
-- Not: Aşağıdaki SECURITY DEFINER fonksiyonlar clinics tablosunu RLS'e takılmadan okur
-- (aksi halde clinics policy'si kendini çağırıp sonsuz özyinelemeye girerdi).
-- Bu yüzden migration'ı çalıştıran rol superuser ya da BYPASSRLS olmalıdır.

-- Kullanıcının, verilen klinik grubundaki herhangi bir şubede aktif rolü var mı?
CREATE OR REPLACE FUNCTION vet.current_user_in_clinic_group(p_organization_id uuid, p_clinic_group_id uuid)
RETURNS boolean
LANGUAGE plpgsql
STABLE
SECURITY DEFINER
SET search_path = vet, pg_temp
AS $$
DECLARE
    current_user_id uuid;
BEGIN
    current_user_id := NULLIF(current_setting('app.user_id', true), '')::uuid;
    IF current_user_id IS NULL OR p_organization_id IS NULL OR p_clinic_group_id IS NULL THEN
        RETURN false;
    END IF;

    RETURN EXISTS (
        SELECT 1
        FROM vet.clinics own
        JOIN vet.user_clinic_roles ucr
          ON ucr.organization_id = own.organization_id AND ucr.clinic_id = own.id
        JOIN vet.app_users u
          ON u.organization_id = ucr.organization_id AND u.id = ucr.user_id
        WHERE own.organization_id = p_organization_id
          AND own.clinic_group_id = p_clinic_group_id
          AND ucr.user_id = current_user_id
          AND u.is_active
          AND ucr.valid_from <= CURRENT_DATE
          AND (ucr.valid_to IS NULL OR ucr.valid_to >= CURRENT_DATE)
    );
END;
$$;

-- Mevcut fonksiyonlar clinics okuduğu için artık RLS'ten bağımsız çalışmalıdır.
CREATE OR REPLACE FUNCTION vet.current_user_has_clinic_group_access(p_organization_id uuid, p_clinic_id uuid)
RETURNS boolean
LANGUAGE plpgsql
STABLE
SECURITY DEFINER
SET search_path = vet, pg_temp
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
        WHERE target.organization_id = p_organization_id
          AND target.id = p_clinic_id
          AND vet.current_user_in_clinic_group(target.organization_id, target.clinic_group_id)
    );
END;
$$;

CREATE OR REPLACE FUNCTION vet.clinics_in_same_group(p_organization_id uuid, p_clinic_a uuid, p_clinic_b uuid)
RETURNS boolean
LANGUAGE sql
STABLE
SECURITY DEFINER
SET search_path = vet, pg_temp
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

-- clinic_groups: yalnızca kendi grubu. INSERT policy'si yoktur (yeni grubu oluşturan henüz üye olamaz);
-- grup oluşturma uygulama rolüyle değil, ayrı bir provisioning akışıyla yapılmalıdır.
DROP POLICY IF EXISTS tenant_isolation ON vet.clinic_groups;
DROP POLICY IF EXISTS clinic_group_select ON vet.clinic_groups;
DROP POLICY IF EXISTS clinic_group_update ON vet.clinic_groups;
DROP POLICY IF EXISTS clinic_group_delete ON vet.clinic_groups;

CREATE POLICY clinic_group_select ON vet.clinic_groups
    FOR SELECT
    USING (
        organization_id = NULLIF(current_setting('app.organization_id', true), '')::uuid
        AND vet.current_user_in_clinic_group(organization_id, id)
    );

CREATE POLICY clinic_group_update ON vet.clinic_groups
    FOR UPDATE
    USING (
        organization_id = NULLIF(current_setting('app.organization_id', true), '')::uuid
        AND vet.current_user_in_clinic_group(organization_id, id)
    )
    WITH CHECK (
        organization_id = NULLIF(current_setting('app.organization_id', true), '')::uuid
        AND vet.current_user_in_clinic_group(organization_id, id)
    );

CREATE POLICY clinic_group_delete ON vet.clinic_groups
    FOR DELETE
    USING (
        organization_id = NULLIF(current_setting('app.organization_id', true), '')::uuid
        AND vet.current_user_in_clinic_group(organization_id, id)
    );

-- clinics: yalnızca kullanıcının grubundaki şubeler; kendi grubuna şube eklenebilir.
DROP POLICY IF EXISTS tenant_isolation ON vet.clinics;
DROP POLICY IF EXISTS clinic_group_scope ON vet.clinics;

CREATE POLICY clinic_group_scope ON vet.clinics
    FOR ALL
    USING (
        organization_id = NULLIF(current_setting('app.organization_id', true), '')::uuid
        AND vet.current_user_in_clinic_group(organization_id, clinic_group_id)
    )
    WITH CHECK (
        organization_id = NULLIF(current_setting('app.organization_id', true), '')::uuid
        AND vet.current_user_in_clinic_group(organization_id, clinic_group_id)
    );

-- Platform admin SELECT erişimi (clinic_groups V4'ten sonra oluştuğu için eksikti).
DROP POLICY IF EXISTS platform_admin_select ON vet.clinic_groups;
CREATE POLICY platform_admin_select ON vet.clinic_groups
    FOR SELECT
    TO vet_app
    USING ((SELECT vet.is_platform_admin()));
