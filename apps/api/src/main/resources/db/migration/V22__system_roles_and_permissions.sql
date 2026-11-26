-- Proje dokümanındaki dört personel rolü (klinik yöneticisi, şube yöneticisi, veteriner hekim, resepsiyon)
-- her organizasyon için sistem rolü olarak tanımlanır. Rol kodları UnifiedAuthService.resolvePanels ile uyumludur.

INSERT INTO vet.permissions (code, description) VALUES
    ('schedule.manage', 'Hekim çalışma saatlerini yönetme'),
    ('services.manage', 'Klinik hizmetlerini ve fiyatlarını yönetme'),
    ('branches.manage', 'Klinik ve şube yönetimi'),
    ('subscriptions.manage', 'Abonelik paketini yönetme')
ON CONFLICT (code) DO NOTHING;

-- Sistem rollerini ve yetkilerini oluşturur (tekrar çalıştırılabilir). Kayıt (signup) akışında ve demo verisinde kullanılır.
CREATE OR REPLACE FUNCTION vet.seed_system_roles(p_organization_id uuid)
RETURNS void
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = vet, pg_temp
AS $$
DECLARE
    r record;
    v_role_id uuid;
BEGIN
    FOR r IN
        SELECT * FROM (VALUES
            ('clinic_admin', 'Klinik yöneticisi', ARRAY[
                'clients.read','clients.write','appointments.manage','clinical.read','clinical.write','clinical.delete',
                'diagnostics.manage','inventory.read','inventory.write','billing.manage','users.manage','reports.read',
                'audit.read','clinic_roles.manage','vaccine_protocols.manage','subscriptions.read','commissions.read',
                'schedule.manage','services.manage','branches.manage','subscriptions.manage']),
            ('branch_manager', 'Şube yöneticisi', ARRAY[
                'clients.read','clients.write','appointments.manage','clinical.read','clinical.write',
                'diagnostics.manage','inventory.read','inventory.write','billing.manage','users.manage','reports.read',
                'audit.read','clinic_roles.manage','vaccine_protocols.manage','schedule.manage','services.manage']),
            ('veterinarian', 'Veteriner hekim', ARRAY[
                'clients.read','clients.write','appointments.manage','clinical.read','clinical.write','clinical.delete',
                'diagnostics.manage','inventory.read','reports.read','vaccine_protocols.manage']),
            ('receptionist', 'Resepsiyon', ARRAY[
                'clients.read','clients.write','appointments.manage','inventory.read','inventory.write','billing.manage'])
        ) AS t(code, name, perms)
    LOOP
        SELECT id INTO v_role_id FROM vet.roles WHERE organization_id = p_organization_id AND code = r.code;
        IF v_role_id IS NULL THEN
            v_role_id := gen_random_uuid();
            INSERT INTO vet.roles (id, organization_id, code, name, is_system_role)
            VALUES (v_role_id, p_organization_id, r.code, r.name, true);
        END IF;
        INSERT INTO vet.role_permissions (organization_id, role_id, permission_id)
        SELECT p_organization_id, v_role_id, p.id FROM vet.permissions p WHERE p.code = ANY (r.perms)
        ON CONFLICT DO NOTHING;
    END LOOP;
END;
$$;

REVOKE ALL ON FUNCTION vet.seed_system_roles(uuid) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION vet.seed_system_roles(uuid) TO vet_app;
