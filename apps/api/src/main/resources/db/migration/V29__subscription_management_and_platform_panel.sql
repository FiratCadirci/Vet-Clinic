-- Abonelik yönetimi (FG-K1..K3) ve platform yöneticisi paneli (FG-K5).
--
-- V10 platform yöneticisini tüm tenant tablolarında salt okur yapmıştı. Panelin gerektirdiği yazmalar bilinçli ve dar
-- tutulur: yalnız abonelik satırının güncellenmesi (paket değiştirme / askıya alma) ile platform kataloğu
-- (paketler, komisyon kuralları). Tıbbi ve operasyonel tablolarda platform yazma yetkisi yoktur. Bu yazmaların hepsi
-- V6'daki vet.log_platform_admin_write trigger'ı ile platform_admin_access_log'a düşer.

-- ---------------------------------------------------------------------------
-- 1. Platform kataloğu (paketler, komisyon kuralları): herkes okur, yalnız platform yöneticisi yazar.
--    Tablolar organizasyonsuzdur; kayıt ekranı oturumsuz paket listeler ve fatura trigger'ı kuralları kiracı
--    bağlamında okur, bu yüzden okuma serbesttir. FORCE açılmaz: tablo sahibi (migration kullanıcısı) seed yazabilir;
--    uygulama rolü vet_app sahip olmadığı için politikalara tabidir. Klinik personeli bu tablolara yazamaz (FG-K4).
-- ---------------------------------------------------------------------------
DO $$
DECLARE
    t text;
BEGIN
    FOREACH t IN ARRAY ARRAY['subscription_plans', 'commission_rules'] LOOP
        EXECUTE format('ALTER TABLE vet.%I ENABLE ROW LEVEL SECURITY', t);
        EXECUTE format('DROP POLICY IF EXISTS catalog_read ON vet.%I', t);
        EXECUTE format('CREATE POLICY catalog_read ON vet.%I FOR SELECT TO vet_app USING (true)', t);
        EXECUTE format('DROP POLICY IF EXISTS platform_admin_insert ON vet.%I', t);
        EXECUTE format('CREATE POLICY platform_admin_insert ON vet.%I FOR INSERT TO vet_app WITH CHECK ((SELECT vet.is_platform_admin()))', t);
        EXECUTE format('DROP POLICY IF EXISTS platform_admin_update ON vet.%I', t);
        EXECUTE format('CREATE POLICY platform_admin_update ON vet.%I FOR UPDATE TO vet_app USING ((SELECT vet.is_platform_admin())) WITH CHECK ((SELECT vet.is_platform_admin()))', t);
        EXECUTE format('DROP POLICY IF EXISTS platform_admin_delete ON vet.%I', t);
        EXECUTE format('CREATE POLICY platform_admin_delete ON vet.%I FOR DELETE TO vet_app USING ((SELECT vet.is_platform_admin()))', t);
        -- V6 trigger'ı yalnız organization_id taşıyan tablolara eklenmişti; katalog yazmaları da loglanır.
        EXECUTE format('DROP TRIGGER IF EXISTS trg_platform_admin_access_log ON vet.%I', t);
        EXECUTE format('CREATE TRIGGER trg_platform_admin_access_log AFTER INSERT OR UPDATE OR DELETE ON vet.%I FOR EACH ROW EXECUTE FUNCTION vet.log_platform_admin_write()', t);
    END LOOP;
END;
$$;

-- ---------------------------------------------------------------------------
-- 2. Platform yöneticisi bir kliniğin aboneliğini değiştirebilir / askıya alabilir (yalnız UPDATE; ekleme/silme yok).
-- ---------------------------------------------------------------------------
DROP POLICY IF EXISTS platform_admin_update ON vet.organization_subscriptions;
CREATE POLICY platform_admin_update ON vet.organization_subscriptions
    FOR UPDATE TO vet_app
    USING ((SELECT vet.is_platform_admin()))
    WITH CHECK ((SELECT vet.is_platform_admin()));

-- ---------------------------------------------------------------------------
-- 3. FG-K3 günlük görev: dönemi biten deneme/aktif aboneliği olan organizasyonlar. Yalnız kimlik döner; durum
--    güncellemesi her organizasyonun kendi tenant bağlamında (RLS altında) yapılır.
-- ---------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION vet.subscription_due_organizations()
RETURNS SETOF uuid
LANGUAGE sql
STABLE
SECURITY DEFINER
SET search_path = vet, pg_temp
AS $$
    SELECT DISTINCT s.organization_id
      FROM vet.organization_subscriptions s
     WHERE s.status IN ('TRIAL', 'ACTIVE')
       AND s.current_period_end <= now()
     ORDER BY s.organization_id;
$$;

-- ---------------------------------------------------------------------------
-- 4. Platform klinik listesi: paket limitine sayılan kullanım (V23 vet.organization_usage ile aynı sayım).
--    Yalnız platform yöneticisi oturumunda satır döner; tıbbi veri içermez.
-- ---------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION vet.platform_organization_usage()
RETURNS TABLE (organization_id uuid, active_branches integer, active_users integer)
LANGUAGE sql
STABLE
SECURITY DEFINER
SET search_path = vet, pg_temp
AS $$
    SELECT o.id, u.active_branches, u.active_users
      FROM vet.organizations o
     CROSS JOIN LATERAL vet.organization_usage(o.id) u
     WHERE vet.is_platform_admin();
$$;

REVOKE ALL ON FUNCTION vet.subscription_due_organizations() FROM PUBLIC;
REVOKE ALL ON FUNCTION vet.platform_organization_usage() FROM PUBLIC;
GRANT EXECUTE ON FUNCTION vet.subscription_due_organizations() TO vet_app;
GRANT EXECUTE ON FUNCTION vet.platform_organization_usage() TO vet_app;

-- Platform gelir raporu (organizasyonlar arası, ödeme tarihine göre aylık).
CREATE INDEX IF NOT EXISTS ix_subscription_billing_processed
    ON vet.subscription_billing_events (processed_at)
    WHERE status = 'SUCCEEDED';
