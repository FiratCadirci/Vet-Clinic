-- Tek giriş ekranı: yalnızca e-posta ile (organizasyon kodu opsiyonel) giriş adaylarını çözer.
-- Oturum öncesi çalıştığı için SECURITY DEFINER; yalnızca aktif organizasyonların kullanıcı kimliklerini ve
-- hayvan sahibi (clients.app_user_id) bilgisini döner, parola/profil verisi ifşa etmez.
CREATE OR REPLACE FUNCTION vet.resolve_login_candidates(p_email text, p_organization_code text DEFAULT NULL)
RETURNS TABLE (
    organization_id   uuid,
    organization_code text,
    organization_name text,
    user_id           uuid,
    is_client         boolean
)
LANGUAGE sql
STABLE
SECURITY DEFINER
SET search_path = vet, pg_temp
AS $$
    SELECT o.id,
           o.code::text,
           o.name::text,
           u.id,
           EXISTS (
               SELECT 1
                 FROM vet.clients c
                WHERE c.organization_id = u.organization_id
                  AND c.app_user_id = u.id
           )
      FROM vet.app_users u
      JOIN vet.organizations o ON o.id = u.organization_id
     WHERE lower(u.email) = lower(btrim(p_email))
       AND o.is_active = true
       AND (p_organization_code IS NULL
            OR btrim(p_organization_code) = ''
            OR lower(o.code) = lower(btrim(p_organization_code)))
     ORDER BY o.code
     LIMIT 20;
$$;

REVOKE ALL ON FUNCTION vet.resolve_login_candidates(text, text) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION vet.resolve_login_candidates(text, text) TO vet_app;
