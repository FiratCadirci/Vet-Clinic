BEGIN;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'vet_app') THEN
        CREATE ROLE vet_app WITH
            LOGIN
            NOSUPERUSER
            NOBYPASSRLS
            NOCREATEDB
            NOCREATEROLE
            NOINHERIT
            CONNECTION LIMIT -1
            PASSWORD 'Ozge2965';
    ELSE
        ALTER ROLE vet_app WITH LOGIN NOSUPERUSER NOBYPASSRLS PASSWORD 'Ozge2965';
    END IF;
END $$;

GRANT CONNECT ON DATABASE "Veterinery" TO vet_app;
GRANT USAGE ON SCHEMA vet TO vet_app;
GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA vet TO vet_app;
GRANT USAGE, SELECT, UPDATE ON ALL SEQUENCES IN SCHEMA vet TO vet_app;
GRANT EXECUTE ON ALL FUNCTIONS IN SCHEMA vet TO vet_app;

ALTER DEFAULT PRIVILEGES IN SCHEMA vet
    GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO vet_app;

ALTER DEFAULT PRIVILEGES IN SCHEMA vet
    GRANT USAGE, SELECT, UPDATE ON SEQUENCES TO vet_app;

ALTER DEFAULT PRIVILEGES IN SCHEMA vet
    GRANT EXECUTE ON FUNCTIONS TO vet_app;

INSERT INTO vet.organizations (id, code, name, legal_name, default_currency, timezone_name, is_active)
SELECT '11111111-1111-4111-8111-111111111111'::uuid,
       'ORG',
       'Demo Vet Clinic',
       'Demo Vet Clinic Ltd.',
       'TRY',
       'Europe/Istanbul',
       true
WHERE NOT EXISTS (
    SELECT 1 FROM vet.organizations WHERE code = 'ORG'
);

INSERT INTO vet.clinic_groups (id, organization_id, code, name, is_active)
SELECT g.id::uuid, o.id, g.code, g.name, true
FROM vet.organizations o
CROSS JOIN (VALUES
    ('99999999-9999-4999-8999-999999999991', 'MAIN', 'Merkez Klinik Grubu'),
    ('99999999-9999-4999-8999-999999999992', 'OTHER', 'Diğer Klinik Grubu')
) AS g(id, code, name)
WHERE o.code = 'ORG'
  AND NOT EXISTS (SELECT 1 FROM vet.clinic_groups x WHERE x.organization_id = o.id AND x.code = g.code);

INSERT INTO vet.clinics (id, organization_id, clinic_group_id, code, name, phone, email, country_code, city, district, address_line, timezone_name, is_active)
SELECT '22222222-2222-4222-8222-222222222222'::uuid,
       o.id,
       (SELECT g.id FROM vet.clinic_groups g WHERE g.organization_id = o.id AND g.code = 'MAIN'),
       'MAIN',
       'Merkez Klinik',
       '+905551234567',
       'main@demo-vet.local',
       'TR',
       'İstanbul',
       'Kadıköy',
       'Koşuyolu Mah. Örnek Sok. No:1',
       'Europe/Istanbul',
       true
FROM vet.organizations o
WHERE o.code = 'ORG'
  AND NOT EXISTS (
      SELECT 1 FROM vet.clinics c WHERE c.organization_id = o.id AND c.code = 'MAIN'
  );

INSERT INTO vet.roles (id, organization_id, code, name, description, is_system_role)
SELECT '33333333-3333-4333-8333-333333333333'::uuid, o.id, 'clinic_admin', 'Klinik Yöneticisi', 'Şube yöneticisi rolü', true
FROM vet.organizations o
WHERE o.code = 'ORG'
  AND NOT EXISTS (
      SELECT 1 FROM vet.roles r WHERE r.organization_id = o.id AND r.code = 'clinic_admin'
  );

INSERT INTO vet.app_users (id, organization_id, email, password_hash, display_name, phone, is_active, mfa_enabled)
SELECT '44444444-4444-4444-8444-444444444444'::uuid,
       o.id,
       'vet@test.com',
       crypt('secret', gen_salt('bf', 10)),
       'Vet Demo User',
       '+905550000001',
       true,
       false
FROM vet.organizations o
WHERE o.code = 'ORG'
  AND NOT EXISTS (
      SELECT 1 FROM vet.app_users u WHERE u.organization_id = o.id AND lower(u.email) = lower('vet@test.com')
  );

INSERT INTO vet.user_clinic_roles (id, organization_id, user_id, clinic_id, role_id, valid_from, valid_to)
SELECT '55555555-5555-4555-8555-555555555555'::uuid,
       o.id,
       u.id,
       c.id,
       r.id,
       CURRENT_DATE,
       NULL
FROM vet.organizations o
JOIN vet.app_users u ON u.organization_id = o.id AND lower(u.email) = lower('vet@test.com')
JOIN vet.clinics c ON c.organization_id = o.id AND c.code = 'MAIN'
JOIN vet.roles r ON r.organization_id = o.id AND r.code = 'clinic_admin'
WHERE o.code = 'ORG'
  AND NOT EXISTS (
      SELECT 1
      FROM vet.user_clinic_roles ucr
      WHERE ucr.organization_id = o.id
        AND ucr.user_id = u.id
        AND ucr.clinic_id = c.id
        AND ucr.role_id = r.id
  );

INSERT INTO vet.role_permissions (organization_id, role_id, permission_id)
SELECT r.organization_id, r.id, p.id
FROM vet.roles r
JOIN vet.permissions p ON p.code IN (
    'clients.read',
    'clients.write',
    'appointments.manage',
    'clinical.read',
    'clinical.write',
    'inventory.read',
    'inventory.write',
    'billing.manage',
    'users.manage',
    'reports.read',
    'audit.read'
)
WHERE r.organization_id = (
    SELECT id FROM vet.organizations WHERE code = 'ORG'
)
  AND r.code = 'clinic_admin'
  AND NOT EXISTS (
      SELECT 1
      FROM vet.role_permissions rp
      WHERE rp.organization_id = r.organization_id
        AND rp.role_id = r.id
        AND rp.permission_id = p.id
  );

INSERT INTO vet.platform_admins (id, email, password_hash, display_name, is_active, created_at, updated_at)
SELECT '66666666-6666-4666-8666-666666666666'::uuid,
       'platform@admin.com',
       crypt('secret', gen_salt('bf', 10)),
       'Platform Admin',
       true,
       now(),
       now()
WHERE NOT EXISTS (
    SELECT 1 FROM vet.platform_admins WHERE email = 'platform@admin.com'
);

-- Klinik grubu örneği: MAIN2 şubesi MAIN ile aynı grupta (veri paylaşır), OTHER ayrı bir klinik grubudur (izole).
INSERT INTO vet.clinics (id, organization_id, clinic_group_id, code, name, city, is_active)
SELECT '77777777-7777-4777-8777-777777777771'::uuid, c.organization_id, c.clinic_group_id, 'MAIN2', 'Merkez Klinik - Şube 2', 'İstanbul', true
FROM vet.clinics c
WHERE c.code = 'MAIN' AND c.organization_id = (SELECT id FROM vet.organizations WHERE code = 'ORG')
  AND NOT EXISTS (SELECT 1 FROM vet.clinics x WHERE x.organization_id = c.organization_id AND x.code = 'MAIN2');

INSERT INTO vet.clinics (id, organization_id, clinic_group_id, code, name, city, is_active)
SELECT '77777777-7777-4777-8777-777777777772'::uuid, o.id, (SELECT g.id FROM vet.clinic_groups g WHERE g.organization_id = o.id AND g.code = 'OTHER'), 'OTHER', 'Diğer Klinik', 'Ankara', true
FROM vet.organizations o
WHERE o.code = 'ORG'
  AND NOT EXISTS (SELECT 1 FROM vet.clinics x WHERE x.organization_id = o.id AND x.code = 'OTHER');

INSERT INTO vet.app_users (id, organization_id, email, password_hash, display_name, is_active, mfa_enabled)
SELECT '88888888-8888-4888-8888-888888888888'::uuid, o.id, 'other@test.com', crypt('secret', gen_salt('bf', 10)), 'Other Clinic User', true, false
FROM vet.organizations o
WHERE o.code = 'ORG'
  AND NOT EXISTS (SELECT 1 FROM vet.app_users u WHERE u.organization_id = o.id AND lower(u.email) = 'other@test.com');

INSERT INTO vet.user_clinic_roles (organization_id, user_id, clinic_id, role_id, valid_from)
SELECT o.id, u.id, c.id, r.id, CURRENT_DATE
FROM vet.organizations o
JOIN vet.app_users u ON u.organization_id = o.id AND lower(u.email) = 'other@test.com'
JOIN vet.clinics c ON c.organization_id = o.id AND c.code = 'OTHER'
JOIN vet.roles r ON r.organization_id = o.id AND r.code = 'clinic_admin'
WHERE o.code = 'ORG'
  AND NOT EXISTS (
      SELECT 1 FROM vet.user_clinic_roles ucr
      WHERE ucr.organization_id = o.id AND ucr.user_id = u.id AND ucr.clinic_id = c.id AND ucr.role_id = r.id
  );

-- Resepsiyonist rolü: randevu, müşteri/hayvan/arama, kasa/fatura ve klinik veri okuma
INSERT INTO vet.roles (id, organization_id, code, name, description, is_system_role)
SELECT '99999999-9999-4999-8999-999999999991'::uuid, o.id, 'receptionist', 'Resepsiyonist', 'Resepsiyon rolü', true
FROM vet.organizations o
WHERE o.code = 'ORG'
  AND NOT EXISTS (SELECT 1 FROM vet.roles r WHERE r.organization_id = o.id AND r.code = 'receptionist');

INSERT INTO vet.role_permissions (organization_id, role_id, permission_id)
SELECT r.organization_id, r.id, p.id
FROM vet.roles r
JOIN vet.permissions p ON p.code IN (
    'appointments.manage',
    'clients.read',
    'clients.write',
    'billing.manage',
    'clinical.read'
)
WHERE r.organization_id = (SELECT id FROM vet.organizations WHERE code = 'ORG')
  AND r.code = 'receptionist'
  AND NOT EXISTS (
      SELECT 1 FROM vet.role_permissions rp
      WHERE rp.organization_id = r.organization_id AND rp.role_id = r.id AND rp.permission_id = p.id
  );

INSERT INTO vet.app_users (id, organization_id, email, password_hash, display_name, is_active, mfa_enabled)
SELECT '99999999-9999-4999-8999-999999999992'::uuid, o.id, 'reception@test.com', crypt('secret', gen_salt('bf', 10)), 'Resepsiyon Demo User', true, false
FROM vet.organizations o
WHERE o.code = 'ORG'
  AND NOT EXISTS (SELECT 1 FROM vet.app_users u WHERE u.organization_id = o.id AND lower(u.email) = 'reception@test.com');

INSERT INTO vet.user_clinic_roles (organization_id, user_id, clinic_id, role_id, valid_from)
SELECT o.id, u.id, c.id, r.id, CURRENT_DATE
FROM vet.organizations o
JOIN vet.app_users u ON u.organization_id = o.id AND lower(u.email) = 'reception@test.com'
JOIN vet.clinics c ON c.organization_id = o.id AND c.code = 'MAIN'
JOIN vet.roles r ON r.organization_id = o.id AND r.code = 'receptionist'
WHERE o.code = 'ORG'
  AND NOT EXISTS (
      SELECT 1 FROM vet.user_clinic_roles ucr
      WHERE ucr.organization_id = o.id AND ucr.user_id = u.id AND ucr.clinic_id = c.id AND ucr.role_id = r.id
  );

COMMIT;
