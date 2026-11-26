-- Şube mutlaka bir klinik grubuna (ve dolayısıyla organizasyona) bağlı olmalıdır; otomatik grup oluşturma kaldırıldı.
DROP TRIGGER IF EXISTS trg_clinics_assign_default_group ON vet.clinics;
DROP FUNCTION IF EXISTS vet.assign_default_clinic_group();
