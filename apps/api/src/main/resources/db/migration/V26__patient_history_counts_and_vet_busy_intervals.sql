-- Hasta geçmişi (FG-C5, FG-J1) ve uygun saat hesabı (FG-E2) için RLS'i delmeden çalışan iki dar kapsamlı fonksiyon.
-- İkisi de SECURITY DEFINER'dır ama yalnızca oturumdaki organizasyonla (app.organization_id) sınırlıdır ve
-- tıbbi içerik döndürmez: biri yalnızca kayıt SAYISI, diğeri yalnızca dolu zaman ARALIKLARI verir.

-- Hastanın organizasyondaki tüm muayene / aşı / randevu kayıtlarının sayısı. Uygulama, kullanıcının RLS ve
-- yetkiyle görebildiği sayıyı bundan çıkararak "gizlenen kayıt sayısı"nı hesaplar.
-- Hasta çağıranın klinik grubuna açık değilse (başka grup/organizasyon) sıfır döner; böylece bilinmeyen bir
-- hasta kimliği için sayı sızmaz.
CREATE OR REPLACE FUNCTION vet.count_animal_history_records(p_animal_id uuid)
RETURNS TABLE (encounters bigint, vaccinations bigint, appointments bigint)
LANGUAGE plpgsql
STABLE
SECURITY DEFINER
SET search_path = vet, pg_temp
AS $$
DECLARE
    v_org uuid := NULLIF(current_setting('app.organization_id', true), '')::uuid;
BEGIN
    IF v_org IS NULL OR p_animal_id IS NULL OR NOT EXISTS (
        SELECT 1 FROM vet.animal_clinic_access aa
        WHERE aa.organization_id = v_org
          AND aa.animal_id = p_animal_id
          AND aa.revoked_at IS NULL
          AND vet.current_user_has_clinic_group_access(aa.organization_id, aa.clinic_id)
    ) THEN
        RETURN QUERY SELECT 0::bigint, 0::bigint, 0::bigint;
        RETURN;
    END IF;

    RETURN QUERY SELECT
        (SELECT count(*) FROM vet.encounters e WHERE e.organization_id = v_org AND e.animal_id = p_animal_id),
        (SELECT count(*) FROM vet.vaccine_records vr WHERE vr.organization_id = v_org AND vr.animal_id = p_animal_id),
        (SELECT count(*) FROM vet.appointments ap WHERE ap.organization_id = v_org AND ap.animal_id = p_animal_id);
END;
$$;

REVOKE ALL ON FUNCTION vet.count_animal_history_records(uuid) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION vet.count_animal_history_records(uuid) TO vet_app;

-- Hekimin verilen penceredeki dolu aralıkları (iptal ve gelmedi hariç), organizasyonun tüm şubelerinde.
-- Randevu EXCLUDE kısıtı organizasyon genelinde çalıştığı için uygun saat listesi de başka klinik grubundaki
-- randevuları dolu saymalıdır; RLS bu satırları gizlediğinden yalnızca başlangıç/bitiş döndüren bu fonksiyon kullanılır.
CREATE OR REPLACE FUNCTION vet.veterinarian_busy_intervals(
    p_veterinarian_id uuid,
    p_from timestamptz,
    p_to timestamptz,
    p_exclude_appointment_id uuid DEFAULT NULL
)
RETURNS TABLE (starts_at timestamptz, ends_at timestamptz)
LANGUAGE sql
STABLE
SECURITY DEFINER
SET search_path = vet, pg_temp
AS $$
    SELECT ap.starts_at, ap.ends_at
    FROM vet.appointments ap
    WHERE ap.organization_id = NULLIF(current_setting('app.organization_id', true), '')::uuid
      AND ap.veterinarian_id = p_veterinarian_id
      AND ap.status NOT IN ('CANCELLED', 'NO_SHOW')
      AND ap.starts_at < p_to
      AND ap.ends_at > p_from
      AND (p_exclude_appointment_id IS NULL OR ap.id <> p_exclude_appointment_id)
    ORDER BY ap.starts_at;
$$;

REVOKE ALL ON FUNCTION vet.veterinarian_busy_intervals(uuid, timestamptz, timestamptz, uuid) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION vet.veterinarian_busy_intervals(uuid, timestamptz, timestamptz, uuid) TO vet_app;
