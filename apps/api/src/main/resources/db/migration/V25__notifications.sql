-- Bildirim modülü (FG-I1..I4, FG-D5): uygulama içi / e-posta / push kanalları, kullanıcı tercihleri,
-- gönderim kaydı, push cihazları ve aşı hatırlatma tekilleştirmesi.

-- ---------------------------------------------------------------------------
-- 1. notification_outbox: her teslim denemesinin kaydı (gönderim kaydı) ve IN_APP satırları için gelen kutusu.
--    Personel bildirimleri (kritik stok) ve davet e-postası müşteri/hayvan kaydına bağlı olmadığından alıcı
--    kullanıcı (user_id) eklenir, IN_APP kanalı ve SKIPPED (tercih kapalı) durumu açılır.
-- ---------------------------------------------------------------------------
ALTER TABLE vet.notification_outbox
    ADD COLUMN notification_type varchar(50),
    ADD COLUMN user_id           uuid,
    ADD COLUMN recipient_name    varchar(200),
    ADD COLUMN read_at           timestamptz,
    ALTER COLUMN recipient DROP NOT NULL,
    ADD CONSTRAINT fk_notification_outbox_user
        FOREIGN KEY (organization_id, user_id) REFERENCES vet.app_users (organization_id, id) ON DELETE CASCADE,
    ADD CONSTRAINT ck_notification_outbox_recipient CHECK (recipient IS NOT NULL OR user_id IS NOT NULL);

ALTER TABLE vet.notification_outbox DROP CONSTRAINT notification_outbox_channel_check;
ALTER TABLE vet.notification_outbox ADD CONSTRAINT notification_outbox_channel_check
    CHECK (channel IN ('SMS','EMAIL','WHATSAPP','PUSH','IN_APP'));
ALTER TABLE vet.notification_outbox DROP CONSTRAINT notification_outbox_status_check;
ALTER TABLE vet.notification_outbox ADD CONSTRAINT notification_outbox_status_check
    CHECK (status IN ('PENDING','PROCESSING','SENT','FAILED','CANCELLED','SKIPPED'));

CREATE INDEX ix_notification_outbox_inbox
    ON vet.notification_outbox (organization_id, user_id, created_at DESC) WHERE channel = 'IN_APP';
CREATE INDEX ix_notification_outbox_org_created
    ON vet.notification_outbox (organization_id, created_at DESC);

-- Kayıt organizasyon kapsamındadır: yönetici kendi organizasyonunun tüm gönderimlerini görür (FG-I3).
-- Eski şube politikası müşteri/hayvan bağı olmayan personel bildirimlerini ve oturumsuz (zamanlanmış görev)
-- yazmayı engellediği için org politikasına çevrilir. Kişisel gelen kutusu sorguda user_id ile süzülür.
DROP POLICY IF EXISTS tenant_branch_isolation ON vet.notification_outbox;
DROP POLICY IF EXISTS tenant_isolation ON vet.notification_outbox;
CREATE POLICY tenant_isolation ON vet.notification_outbox
    USING (organization_id = NULLIF(current_setting('app.organization_id', true), '')::uuid)
    WITH CHECK (organization_id = NULLIF(current_setting('app.organization_id', true), '')::uuid);

-- ---------------------------------------------------------------------------
-- 2. notification_preferences: olay türü × kanal tercihi artık oturumdaki kullanıcıya (personel veya
--    mobil hesaplı hayvan sahibi) bağlıdır. Satır yoksa kanal açıktır.
-- ---------------------------------------------------------------------------
ALTER TABLE vet.notification_preferences
    ADD COLUMN user_id uuid,
    ALTER COLUMN client_id DROP NOT NULL,
    ADD CONSTRAINT fk_notification_preferences_user
        FOREIGN KEY (organization_id, user_id) REFERENCES vet.app_users (organization_id, id) ON DELETE CASCADE,
    ADD CONSTRAINT ck_notification_preferences_owner CHECK (client_id IS NOT NULL OR user_id IS NOT NULL);

ALTER TABLE vet.notification_preferences DROP CONSTRAINT notification_preferences_channel_check;
ALTER TABLE vet.notification_preferences ADD CONSTRAINT notification_preferences_channel_check
    CHECK (channel IN ('SMS','EMAIL','WHATSAPP','PUSH','IN_APP'));

CREATE UNIQUE INDEX ux_notification_preferences_user_event
    ON vet.notification_preferences (organization_id, user_id, notification_type, channel)
    WHERE user_id IS NOT NULL;

-- ---------------------------------------------------------------------------
-- 3. push_devices: Expo push token'ları. Aynı cihaz organizasyonda tek kullanıcıya bağlıdır
--    (başka kullanıcı aynı token'la kaydolursa token ona taşınır).
-- ---------------------------------------------------------------------------
CREATE TABLE vet.push_devices (
    id                  uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id     uuid NOT NULL REFERENCES vet.organizations(id),
    user_id             uuid NOT NULL,
    expo_push_token     varchar(255) NOT NULL,
    platform            varchar(20) NOT NULL CHECK (platform IN ('IOS','ANDROID')),
    last_seen_at        timestamptz NOT NULL DEFAULT now(),
    created_at          timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT uq_push_devices_org_token UNIQUE (organization_id, expo_push_token),
    FOREIGN KEY (organization_id, user_id) REFERENCES vet.app_users (organization_id, id) ON DELETE CASCADE
);
CREATE INDEX ix_push_devices_user ON vet.push_devices (organization_id, user_id);

ALTER TABLE vet.push_devices ENABLE ROW LEVEL SECURITY;
ALTER TABLE vet.push_devices FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON vet.push_devices
    USING (organization_id = NULLIF(current_setting('app.organization_id', true), '')::uuid)
    WITH CHECK (organization_id = NULLIF(current_setting('app.organization_id', true), '')::uuid);

-- ---------------------------------------------------------------------------
-- 4. vaccine_reminder_dispatches: aşı kaydı + hatırlatma türü (yaklaşan/geciken) başına tek satır.
--    Birincil anahtar eşzamanlı iki görev koşusunda da tek hatırlatma gitmesini garanti eder (FG-D5).
-- ---------------------------------------------------------------------------
CREATE TABLE vet.vaccine_reminder_dispatches (
    organization_id     uuid NOT NULL REFERENCES vet.organizations(id),
    vaccine_record_id   uuid NOT NULL,
    reminder_kind       varchar(20) NOT NULL CHECK (reminder_kind IN ('UPCOMING','OVERDUE')),
    due_date            date NOT NULL,
    created_at          timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (organization_id, vaccine_record_id, reminder_kind),
    FOREIGN KEY (organization_id, vaccine_record_id)
        REFERENCES vet.vaccine_records (organization_id, id) ON DELETE CASCADE
);

ALTER TABLE vet.vaccine_reminder_dispatches ENABLE ROW LEVEL SECURITY;
ALTER TABLE vet.vaccine_reminder_dispatches FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON vet.vaccine_reminder_dispatches
    USING (organization_id = NULLIF(current_setting('app.organization_id', true), '')::uuid)
    WITH CHECK (organization_id = NULLIF(current_setting('app.organization_id', true), '')::uuid);

-- ---------------------------------------------------------------------------
-- 5. Alıcı çözümleme fonksiyonları (SECURITY DEFINER).
--    Bildirim, olayı tetikleyen personelin şube görünürlüğünden ya da kullanıcısız zamanlanmış görevden
--    bağımsız olarak doğru alıcıya gitmelidir. Fonksiyonlar yalnızca app.organization_id ile ayarlanmış
--    organizasyonun satırlarını döner; başka organizasyon parametresi verilse bile sızıntı olmaz.
-- ---------------------------------------------------------------------------

-- Zamanlanmış görev için aktif organizasyon kimlikleri (oturum öncesi, kişisel veri yok).
CREATE OR REPLACE FUNCTION vet.notification_active_organizations()
RETURNS SETOF uuid
LANGUAGE sql
STABLE
SECURITY DEFINER
SET search_path = vet, pg_temp
AS $$
    SELECT o.id FROM vet.organizations o WHERE o.is_active = true ORDER BY o.id;
$$;

-- Hayvanın güncel sahipleri (OWNER/CO_OWNER/GUARDIAN): mobil hesap ve e-posta (birincil EMAIL iletişimi,
-- yoksa mobil hesabın e-postası).
CREATE OR REPLACE FUNCTION vet.notification_animal_owners(p_animal_id uuid)
RETURNS TABLE (
    client_id     uuid,
    client_name   text,
    owner_user_id uuid,
    owner_email   text,
    animal_name   text
)
LANGUAGE sql
STABLE
SECURITY DEFINER
SET search_path = vet, pg_temp
AS $$
    SELECT c.id,
           c.display_name::text,
           c.app_user_id,
           COALESCE(
               (SELECT cc.contact_value FROM vet.client_contacts cc
                 WHERE cc.organization_id = c.organization_id AND cc.client_id = c.id AND cc.contact_type = 'EMAIL'
                 ORDER BY cc.is_primary DESC, cc.created_at LIMIT 1),
               (SELECT u.email FROM vet.app_users u
                 WHERE u.organization_id = c.organization_id AND u.id = c.app_user_id AND u.is_active)
           )::text,
           a.name::text
      FROM vet.animals a
      JOIN vet.animal_owners ao ON ao.organization_id = a.organization_id AND ao.animal_id = a.id
      JOIN vet.clients c ON c.organization_id = ao.organization_id AND c.id = ao.client_id
     WHERE a.organization_id = NULLIF(current_setting('app.organization_id', true), '')::uuid
       AND a.id = p_animal_id
       AND a.deceased_at IS NULL
       AND ao.valid_to IS NULL
       AND ao.relationship_type IN ('OWNER','CO_OWNER','GUARDIAN')
       AND c.is_active
     ORDER BY ao.is_primary_contact DESC, c.id;
$$;

-- Randevu bildirimi için özet: hayvan, müşteri, zaman ve şube adı. Randevuyu değiştiren kullanıcının
-- (ör. ileride portaldan iptal eden sahip) şube görünürlüğünden bağımsızdır.
CREATE OR REPLACE FUNCTION vet.notification_appointment(p_appointment_id uuid)
RETURNS TABLE (
    animal_id   uuid,
    client_id   uuid,
    starts_at   timestamptz,
    clinic_name text
)
LANGUAGE sql
STABLE
SECURITY DEFINER
SET search_path = vet, pg_temp
AS $$
    SELECT ap.animal_id, ap.client_id, ap.starts_at, cl.name::text
      FROM vet.appointments ap
      JOIN vet.clinics cl ON cl.organization_id = ap.organization_id AND cl.id = ap.clinic_id
     WHERE ap.organization_id = NULLIF(current_setting('app.organization_id', true), '')::uuid
       AND ap.id = p_appointment_id;
$$;

-- Şubede belirli yetkiye sahip aktif personel (ör. kritik stok uyarısı için inventory.write).
CREATE OR REPLACE FUNCTION vet.notification_clinic_staff(p_clinic_id uuid, p_permission text)
RETURNS TABLE (
    user_id      uuid,
    display_name text,
    email        text
)
LANGUAGE sql
STABLE
SECURITY DEFINER
SET search_path = vet, pg_temp
AS $$
    SELECT DISTINCT u.id, u.display_name::text, u.email::text
      FROM vet.user_clinic_roles ucr
      JOIN vet.app_users u ON u.organization_id = ucr.organization_id AND u.id = ucr.user_id
      JOIN vet.role_permissions rp ON rp.organization_id = ucr.organization_id AND rp.role_id = ucr.role_id
      JOIN vet.permissions p ON p.id = rp.permission_id
     WHERE ucr.organization_id = NULLIF(current_setting('app.organization_id', true), '')::uuid
       AND ucr.clinic_id = p_clinic_id
       AND p.code = p_permission
       AND u.is_active
       AND ucr.valid_from <= CURRENT_DATE
       AND (ucr.valid_to IS NULL OR ucr.valid_to >= CURRENT_DATE);
$$;

-- Hatırlatılacak aşılar: hayvan + aşı adı başına en son kayıt (aşı modülündeki "due" kuralıyla aynı),
-- vadesi p_days_ahead gün içinde ya da geçmiş, bu tür için henüz hatırlatma gönderilmemiş olanlar.
CREATE OR REPLACE FUNCTION vet.vaccine_reminder_candidates(p_days_ahead integer)
RETURNS TABLE (
    vaccine_record_id uuid,
    animal_id         uuid,
    vaccine_name      text,
    next_due_at       date,
    reminder_kind     text
)
LANGUAGE sql
STABLE
SECURITY DEFINER
SET search_path = vet, pg_temp
AS $$
    SELECT l.id, l.animal_id, l.vaccine_name::text, l.next_due_at, l.kind
      FROM (
          SELECT DISTINCT ON (vr.animal_id, lower(vr.vaccine_name))
                 vr.id, vr.organization_id, vr.animal_id, vr.vaccine_name, vr.next_due_at,
                 CASE WHEN vr.next_due_at < CURRENT_DATE THEN 'OVERDUE' ELSE 'UPCOMING' END AS kind
            FROM vet.vaccine_records vr
           WHERE vr.organization_id = NULLIF(current_setting('app.organization_id', true), '')::uuid
           ORDER BY vr.animal_id, lower(vr.vaccine_name), vr.administered_at DESC, vr.created_at DESC
      ) l
      JOIN vet.animals a ON a.organization_id = l.organization_id AND a.id = l.animal_id
     WHERE a.deceased_at IS NULL
       AND l.next_due_at IS NOT NULL
       AND l.next_due_at <= CURRENT_DATE + p_days_ahead
       AND NOT EXISTS (
           SELECT 1 FROM vet.vaccine_reminder_dispatches d
            WHERE d.organization_id = l.organization_id
              AND d.vaccine_record_id = l.id
              AND d.reminder_kind = l.kind
       )
     ORDER BY l.next_due_at, l.id;
$$;

REVOKE ALL ON FUNCTION vet.notification_active_organizations() FROM PUBLIC;
REVOKE ALL ON FUNCTION vet.notification_animal_owners(uuid) FROM PUBLIC;
REVOKE ALL ON FUNCTION vet.notification_appointment(uuid) FROM PUBLIC;
REVOKE ALL ON FUNCTION vet.notification_clinic_staff(uuid, text) FROM PUBLIC;
REVOKE ALL ON FUNCTION vet.vaccine_reminder_candidates(integer) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION vet.notification_active_organizations() TO vet_app;
GRANT EXECUTE ON FUNCTION vet.notification_animal_owners(uuid) TO vet_app;
GRANT EXECUTE ON FUNCTION vet.notification_appointment(uuid) TO vet_app;
GRANT EXECUTE ON FUNCTION vet.notification_clinic_staff(uuid, text) TO vet_app;
GRANT EXECUTE ON FUNCTION vet.vaccine_reminder_candidates(integer) TO vet_app;
