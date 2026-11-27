-- Klinik kaydı (signup), personel daveti, paket (şube/kullanıcı) limitleri, hekim çalışma saati çakışma kısıtı
-- ve organizasyon iletişim bilgileri.
--
-- Oturum öncesi çalışan akışlar (kayıt, davet önizleme/kabul) V17'deki gibi SECURITY DEFINER fonksiyonlarla yapılır:
-- yeni organizasyonun klinik grubu/şubesi/rol atamaları RLS altında "henüz üye olmayan" biri tarafından yazılamaz.
-- Limit sayımları da organizasyon genelinde (RLS'in gösterdiği klinik grubuyla sınırlı kalmadan) yapılmalıdır.

-- ---------------------------------------------------------------------------
-- 1. Organizasyon (klinik) profili: iletişim bilgileri
-- ---------------------------------------------------------------------------
ALTER TABLE vet.organizations
    ADD COLUMN IF NOT EXISTS phone        varchar(40),
    ADD COLUMN IF NOT EXISTS email        varchar(254),
    ADD COLUMN IF NOT EXISTS address_line text;

-- ---------------------------------------------------------------------------
-- 2. Abonelik paketleri: tablo boşsa eski demo verisindeki paketler (aylık ve yıllık satır ayrı) eklenir.
-- ---------------------------------------------------------------------------
INSERT INTO vet.subscription_plans (code, name, billing_period, price, currency, max_branches, max_users)
SELECT v.code, v.name, v.billing_period, v.price, 'TRY', v.max_branches, v.max_users
FROM (VALUES
    ('baslangic-aylik',   'Başlangıç',   'MONTHLY',   499.00,  1,  5),
    ('baslangic-yillik',  'Başlangıç',   'YEARLY',   4990.00,  1,  5),
    ('profesyonel-aylik', 'Profesyonel', 'MONTHLY',  1299.00,  3, 15),
    ('profesyonel-yillik','Profesyonel', 'YEARLY',  12990.00,  3, 15),
    ('kurumsal-aylik',    'Kurumsal',    'MONTHLY',  2999.00, 10, 50),
    ('kurumsal-yillik',   'Kurumsal',    'YEARLY',  29990.00, 10, 50)
) AS v(code, name, billing_period, price, max_branches, max_users)
WHERE NOT EXISTS (SELECT 1 FROM vet.subscription_plans);

-- ---------------------------------------------------------------------------
-- 3. Personel daveti: ham token saklanmaz, yalnız SHA-256 özeti tutulur (7 gün geçerli, tek kullanımlık).
-- ---------------------------------------------------------------------------
CREATE TABLE vet.staff_invitations (
    id                  uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id     uuid NOT NULL REFERENCES vet.organizations(id),
    email               varchar(254) NOT NULL,
    role_id             uuid NOT NULL,
    clinic_ids          uuid[] NOT NULL CHECK (cardinality(clinic_ids) > 0),
    token_hash          varchar(64) NOT NULL,
    invited_by          uuid,
    expires_at          timestamptz NOT NULL,
    accepted_at         timestamptz,
    accepted_user_id    uuid,
    revoked_at          timestamptz,
    revoked_by          uuid,
    created_at          timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT uq_staff_invitations_org_id UNIQUE (organization_id, id),
    CONSTRAINT uq_staff_invitations_token UNIQUE (token_hash),
    FOREIGN KEY (organization_id, role_id) REFERENCES vet.roles (organization_id, id),
    FOREIGN KEY (organization_id, invited_by) REFERENCES vet.app_users (organization_id, id),
    FOREIGN KEY (organization_id, accepted_user_id) REFERENCES vet.app_users (organization_id, id),
    FOREIGN KEY (organization_id, revoked_by) REFERENCES vet.app_users (organization_id, id),
    CHECK (expires_at > created_at),
    CHECK (accepted_at IS NULL OR revoked_at IS NULL)
);
CREATE INDEX ix_staff_invitations_org_email ON vet.staff_invitations (organization_id, lower(email));

-- Davetler, en az bir şubesi çağıranın klinik grubunda olan kayıtlarla sınırlıdır (V12'deki kullanıcı görünürlüğü gibi).
ALTER TABLE vet.staff_invitations ENABLE ROW LEVEL SECURITY;
ALTER TABLE vet.staff_invitations FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_branch_isolation ON vet.staff_invitations
USING (
    organization_id = NULLIF(current_setting('app.organization_id', true), '')::uuid
    AND EXISTS (SELECT 1 FROM unnest(clinic_ids) AS c(id)
                WHERE vet.current_user_has_clinic_group_access(organization_id, c.id))
)
WITH CHECK (
    organization_id = NULLIF(current_setting('app.organization_id', true), '')::uuid
    AND EXISTS (SELECT 1 FROM unnest(clinic_ids) AS c(id)
                WHERE vet.current_user_has_clinic_group_access(organization_id, c.id))
);
CREATE POLICY platform_admin_select ON vet.staff_invitations
    FOR SELECT TO vet_app USING ((SELECT vet.is_platform_admin()));

-- ---------------------------------------------------------------------------
-- 4. FG-E1: hekim aynı gün ve saatte (hangi şubede olursa olsun) iki çalışma aralığına sahip olamaz.
-- ---------------------------------------------------------------------------
CREATE TYPE vet.time_range AS RANGE (subtype = time);

ALTER TABLE vet.veterinarian_working_hours
    ADD CONSTRAINT ex_vet_working_hours_no_overlap
    EXCLUDE USING gist (
        organization_id WITH =,
        veterinarian_id WITH =,
        day_of_week WITH =,
        vet.time_range(starts_at, ends_at, '[)') WITH &&,
        daterange(effective_from, effective_to, '[]') WITH &&
    )
    WHERE (is_active);

-- ---------------------------------------------------------------------------
-- 5. FG-K1: paket limitleri. Organizasyon satırı kilitlenerek sayılır; eşzamanlı kabul/aktivasyon/şube açma
--    işlemleri sıraya girer ve limit aşılamaz. Hiç aboneliği olmayan (ör. platformdan elle açılmış) organizasyon
--    sınırlanmaz; aboneliği olup deneme/aktif olmayan organizasyon yeni şube/kullanıcı ekleyemez.
-- ---------------------------------------------------------------------------
-- Paket limitine sayılanlar: aktif şubeler ve aktif personel (en az bir geçerli şube rol ataması olan hesap;
-- hayvan sahibi hesapları sayılmaz). Sayım organizasyon geneli olduğu için yalnız SECURITY DEFINER fonksiyonlardan çağrılır.
CREATE OR REPLACE FUNCTION vet.organization_usage(p_organization_id uuid)
RETURNS TABLE (active_branches integer, active_users integer)
LANGUAGE sql
STABLE
SET search_path = vet, pg_temp
AS $$
    SELECT (SELECT count(*)::integer FROM vet.clinics c
             WHERE c.organization_id = p_organization_id AND c.is_active),
           (SELECT count(*)::integer FROM vet.app_users u
             WHERE u.organization_id = p_organization_id
               AND u.is_active
               AND EXISTS (SELECT 1 FROM vet.user_clinic_roles ucr
                            WHERE ucr.organization_id = u.organization_id AND ucr.user_id = u.id
                              AND ucr.valid_from <= current_date
                              AND (ucr.valid_to IS NULL OR ucr.valid_to >= current_date)));
$$;

CREATE OR REPLACE FUNCTION vet.plan_limit_status(p_organization_id uuid, p_resource text)
RETURNS TABLE (allowed boolean, max_allowed integer, used integer, plan_name text, reason text)
LANGUAGE plpgsql
SET search_path = vet, pg_temp
AS $$
#variable_conflict use_column
DECLARE
    v_status   text;
    v_branches integer;
    v_users    integer;
    v_plan     text;
    v_used     integer;
    v_max      integer;
BEGIN
    IF p_resource NOT IN ('USERS', 'BRANCHES') THEN
        RAISE EXCEPTION 'Bilinmeyen limit türü: %', p_resource;
    END IF;

    PERFORM 1 FROM vet.organizations o WHERE o.id = p_organization_id FOR UPDATE;

    SELECT s.status, s.max_branches_snapshot, s.max_users_snapshot, p.name
      INTO v_status, v_branches, v_users, v_plan
      FROM vet.organization_subscriptions s
      JOIN vet.subscription_plans p ON p.id = s.plan_id
     WHERE s.organization_id = p_organization_id
       AND s.status IN ('TRIAL', 'ACTIVE', 'PAST_DUE')
     LIMIT 1;

    IF v_status IS NULL THEN
        IF EXISTS (SELECT 1 FROM vet.organization_subscriptions s WHERE s.organization_id = p_organization_id) THEN
            RETURN QUERY SELECT false, NULL::integer, NULL::integer, NULL::text, 'SUBSCRIPTION_INACTIVE'::text;
        ELSE
            RETURN QUERY SELECT true, NULL::integer, NULL::integer, NULL::text, NULL::text;
        END IF;
        RETURN;
    END IF;
    IF v_status = 'PAST_DUE' THEN
        RETURN QUERY SELECT false, NULL::integer, NULL::integer, v_plan, 'SUBSCRIPTION_INACTIVE'::text;
        RETURN;
    END IF;

    SELECT CASE WHEN p_resource = 'BRANCHES' THEN u.active_branches ELSE u.active_users END,
           CASE WHEN p_resource = 'BRANCHES' THEN v_branches ELSE v_users END
      INTO v_used, v_max
      FROM vet.organization_usage(p_organization_id) u;

    RETURN QUERY SELECT v_used < v_max, v_max, v_used, v_plan,
                        CASE WHEN v_used < v_max THEN NULL ELSE 'LIMIT_REACHED' END::text;
END;
$$;

-- Oturum içi kullanım: organizasyon her zaman doğrulanmış bağlamdan (app.organization_id) alınır.
CREATE OR REPLACE FUNCTION vet.check_plan_limit(p_resource text)
RETURNS TABLE (allowed boolean, max_allowed integer, used integer, plan_name text, reason text)
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = vet, pg_temp
AS $$
DECLARE
    v_org uuid := NULLIF(current_setting('app.organization_id', true), '')::uuid;
BEGIN
    IF v_org IS NULL THEN
        RAISE EXCEPTION 'Organizasyon bağlamı gereklidir.';
    END IF;
    RETURN QUERY SELECT * FROM vet.plan_limit_status(v_org, p_resource);
END;
$$;

-- Klinik profilindeki paket kullanımı (kilitsiz, salt okuma): aktif şube ve personel sayısı.
CREATE OR REPLACE FUNCTION vet.current_plan_usage()
RETURNS TABLE (active_branches integer, active_users integer)
LANGUAGE sql
STABLE
SECURITY DEFINER
SET search_path = vet, pg_temp
AS $$
    SELECT * FROM vet.organization_usage(NULLIF(current_setting('app.organization_id', true), '')::uuid);
$$;

REVOKE ALL ON FUNCTION vet.organization_usage(uuid) FROM PUBLIC;
REVOKE ALL ON FUNCTION vet.plan_limit_status(uuid, text) FROM PUBLIC;
REVOKE ALL ON FUNCTION vet.check_plan_limit(text) FROM PUBLIC;
REVOKE ALL ON FUNCTION vet.current_plan_usage() FROM PUBLIC;
GRANT EXECUTE ON FUNCTION vet.check_plan_limit(text) TO vet_app;
GRANT EXECUTE ON FUNCTION vet.current_plan_usage() TO vet_app;

-- ---------------------------------------------------------------------------
-- 6. FG-A1: klinik kaydı tek işlemde: organizasyon + klinik grubu + ilk şube + sistem rolleri + aşı şablonu taslakları
--    + klinik yöneticisi + deneme aboneliği. Paket kodu verilmezse en ucuz aktif aylık paket kullanılır.
-- ---------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION vet.register_clinic(
    p_organization_name text,
    p_phone             text,
    p_email             text,
    p_address           text,
    p_tax_number        text,
    p_branch_name       text,
    p_admin_name        text,
    p_admin_email       text,
    p_password_hash     text,
    p_plan_code         text,
    p_trial_days        integer
)
RETURNS TABLE (result text, organization_id uuid, organization_code integer, user_id uuid, clinic_id uuid)
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = vet, pg_temp
AS $$
#variable_conflict use_column
DECLARE
    v_plan   vet.subscription_plans%ROWTYPE;
    v_org    uuid := gen_random_uuid();
    v_code   integer;
    v_group  uuid := gen_random_uuid();
    v_clinic uuid := gen_random_uuid();
    v_user   uuid := gen_random_uuid();
    v_role   uuid;
BEGIN
    IF p_plan_code IS NOT NULL THEN
        SELECT * INTO v_plan FROM vet.subscription_plans p WHERE p.code = p_plan_code AND p.is_active;
    ELSE
        SELECT * INTO v_plan FROM vet.subscription_plans p
         WHERE p.is_active
         ORDER BY (p.billing_period = 'MONTHLY') DESC, p.price, p.code
         LIMIT 1;
    END IF;
    IF v_plan.id IS NULL THEN
        RETURN QUERY SELECT 'PLAN_NOT_FOUND'::text, NULL::uuid, NULL::integer, NULL::uuid, NULL::uuid;
        RETURN;
    END IF;

    INSERT INTO vet.organizations (id, name, legal_name, tax_number, phone, email, address_line)
    VALUES (v_org, p_organization_name, p_organization_name, p_tax_number, p_phone, p_email, p_address)
    RETURNING code INTO v_code;

    INSERT INTO vet.clinic_groups (id, organization_id, code, name)
    VALUES (v_group, v_org, 'MERKEZ', p_organization_name);

    INSERT INTO vet.clinics (id, organization_id, clinic_group_id, code, name, phone, email, address_line)
    VALUES (v_clinic, v_org, v_group, 'SUBE-1', p_branch_name, p_phone, p_email, p_address);

    PERFORM vet.seed_system_roles(v_org);
    PERFORM vet.seed_default_vaccine_protocols(v_org);

    INSERT INTO vet.app_users (id, organization_id, email, password_hash, display_name, phone, is_active, must_change_password)
    VALUES (v_user, v_org, lower(btrim(p_admin_email)), p_password_hash, p_admin_name, p_phone, true, false);

    SELECT r.id INTO v_role FROM vet.roles r WHERE r.organization_id = v_org AND r.code = 'clinic_admin';
    INSERT INTO vet.user_clinic_roles (organization_id, user_id, clinic_id, role_id, valid_from)
    VALUES (v_org, v_user, v_clinic, v_role, current_date);

    INSERT INTO vet.organization_subscriptions (
        organization_id, plan_id, status, billing_period, price_snapshot, currency,
        max_branches_snapshot, max_users_snapshot, starts_at, current_period_start, current_period_end,
        next_renewal_at, created_by, notes)
    VALUES (
        v_org, v_plan.id, 'TRIAL', v_plan.billing_period, v_plan.price, v_plan.currency,
        v_plan.max_branches, v_plan.max_users, now(), now(), now() + make_interval(days => p_trial_days),
        now() + make_interval(days => p_trial_days), v_user, 'Kayıtla başlayan deneme süresi');

    INSERT INTO vet.audit_logs (organization_id, clinic_id, actor_user_id, action, entity_type, entity_id)
    VALUES (v_org, v_clinic, v_user, 'ORGANIZATION_SIGNUP', 'organization', v_org);

    RETURN QUERY SELECT 'OK'::text, v_org, v_code, v_user, v_clinic;
END;
$$;

REVOKE ALL ON FUNCTION vet.register_clinic(text, text, text, text, text, text, text, text, text, text, integer) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION vet.register_clinic(text, text, text, text, text, text, text, text, text, text, integer) TO vet_app;

-- ---------------------------------------------------------------------------
-- 7. FG-A2: davet önizleme ve kabulü (oturum öncesi). Token özeti dışında bilgi alınmaz; parola Java'da hash'lenir.
-- ---------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION vet.find_staff_invitation(p_token_hash text)
RETURNS TABLE (invitation_id uuid, organization_name text, email text, role_code text, role_name text,
               expires_at timestamptz, status text)
LANGUAGE sql
STABLE
SECURITY DEFINER
SET search_path = vet, pg_temp
AS $$
    SELECT i.id, o.name::text, i.email::text, r.code::text, r.name::text, i.expires_at,
           CASE WHEN i.revoked_at IS NOT NULL THEN 'REVOKED'
                WHEN i.accepted_at IS NOT NULL THEN 'ACCEPTED'
                WHEN i.expires_at <= now() THEN 'EXPIRED'
                ELSE 'PENDING' END
      FROM vet.staff_invitations i
      JOIN vet.organizations o ON o.id = i.organization_id AND o.is_active
      JOIN vet.roles r ON r.organization_id = i.organization_id AND r.id = i.role_id
     WHERE i.token_hash = p_token_hash;
$$;

CREATE OR REPLACE FUNCTION vet.accept_staff_invitation(p_token_hash text, p_display_name text, p_password_hash text)
RETURNS TABLE (result text, organization_id uuid, user_id uuid, max_allowed integer)
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = vet, pg_temp
AS $$
#variable_conflict use_column
DECLARE
    v_org   uuid;
    v_inv   vet.staff_invitations%ROWTYPE;
    v_limit record;
    v_user  uuid := gen_random_uuid();
BEGIN
    SELECT i.organization_id INTO v_org
      FROM vet.staff_invitations i
      JOIN vet.organizations o ON o.id = i.organization_id AND o.is_active
     WHERE i.token_hash = p_token_hash;
    IF v_org IS NULL THEN
        RETURN QUERY SELECT 'NOT_FOUND'::text, NULL::uuid, NULL::uuid, NULL::integer;
        RETURN;
    END IF;

    -- Kilit sırası her akışta aynıdır (önce organizasyon): eşzamanlı kabuller sırayla sayılır.
    PERFORM 1 FROM vet.organizations o WHERE o.id = v_org FOR UPDATE;
    SELECT * INTO v_inv FROM vet.staff_invitations i WHERE i.token_hash = p_token_hash FOR UPDATE;

    IF v_inv.revoked_at IS NOT NULL THEN
        RETURN QUERY SELECT 'REVOKED'::text, v_org, NULL::uuid, NULL::integer; RETURN;
    ELSIF v_inv.accepted_at IS NOT NULL THEN
        RETURN QUERY SELECT 'ACCEPTED'::text, v_org, NULL::uuid, NULL::integer; RETURN;
    ELSIF v_inv.expires_at <= now() THEN
        RETURN QUERY SELECT 'EXPIRED'::text, v_org, NULL::uuid, NULL::integer; RETURN;
    END IF;

    IF EXISTS (SELECT 1 FROM vet.app_users u WHERE u.organization_id = v_org AND lower(u.email) = lower(v_inv.email)) THEN
        RETURN QUERY SELECT 'EMAIL_TAKEN'::text, v_org, NULL::uuid, NULL::integer; RETURN;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM vet.clinics c WHERE c.organization_id = v_org AND c.id = ANY (v_inv.clinic_ids) AND c.is_active) THEN
        RETURN QUERY SELECT 'NO_ACTIVE_CLINIC'::text, v_org, NULL::uuid, NULL::integer; RETURN;
    END IF;

    SELECT * INTO v_limit FROM vet.plan_limit_status(v_org, 'USERS');
    IF NOT v_limit.allowed THEN
        RETURN QUERY SELECT CASE WHEN v_limit.reason = 'SUBSCRIPTION_INACTIVE' THEN 'SUBSCRIPTION_INACTIVE' ELSE 'USER_LIMIT' END::text,
                            v_org, NULL::uuid, v_limit.max_allowed;
        RETURN;
    END IF;

    INSERT INTO vet.app_users (id, organization_id, email, password_hash, display_name, is_active, must_change_password)
    VALUES (v_user, v_org, lower(v_inv.email), p_password_hash, p_display_name, true, false);

    INSERT INTO vet.user_clinic_roles (organization_id, user_id, clinic_id, role_id, valid_from, assigned_by)
    SELECT v_org, v_user, c.id, v_inv.role_id, current_date, v_inv.invited_by
      FROM vet.clinics c
     WHERE c.organization_id = v_org AND c.id = ANY (v_inv.clinic_ids) AND c.is_active;

    UPDATE vet.staff_invitations i SET accepted_at = now(), accepted_user_id = v_user WHERE i.id = v_inv.id;

    INSERT INTO vet.audit_logs (organization_id, actor_user_id, action, entity_type, entity_id)
    VALUES (v_org, v_user, 'STAFF_INVITATION_ACCEPT', 'staff_invitation', v_inv.id);

    RETURN QUERY SELECT 'OK'::text, v_org, v_user, NULL::integer;
END;
$$;

REVOKE ALL ON FUNCTION vet.find_staff_invitation(text) FROM PUBLIC;
REVOKE ALL ON FUNCTION vet.accept_staff_invitation(text, text, text) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION vet.find_staff_invitation(text) TO vet_app;
GRANT EXECUTE ON FUNCTION vet.accept_staff_invitation(text, text, text) TO vet_app;

-- ---------------------------------------------------------------------------
-- 8. Uygulama rolü yetkileri (rol yoksa atlanır).
-- ---------------------------------------------------------------------------
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'vet_app') THEN
        GRANT SELECT, INSERT, UPDATE, DELETE ON vet.staff_invitations TO vet_app;
    END IF;
END $$;
