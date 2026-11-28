-- Raporlama (FG-L1) ve denetim izi (FG-L2, FOG-8): rapor sorguları şube + tarih aralığıyla, denetim izi
-- organizasyon + zaman sırasıyla okunur. Yeni tablo yoktur; yalnız bu sorgular için indeksler eklenir.

-- Muayene sayıları: şube + başlangıç zamanı.
CREATE INDEX IF NOT EXISTS ix_report_encounters_clinic_started
    ON vet.encounters (organization_id, clinic_id, started_at);

-- Satış ve şube cirosu: kesilmiş faturalar kesim zamanına göre (taslaklarda issued_at boştur).
CREATE INDEX IF NOT EXISTS ix_report_invoices_clinic_issued
    ON vet.invoices (organization_id, clinic_id, issued_at)
    WHERE issued_at IS NOT NULL;

-- Geciken aşılar: hastanın aynı aşıya ait son kaydı aranır.
CREATE INDEX IF NOT EXISTS ix_report_vaccine_records_animal_name
    ON vet.vaccine_records (organization_id, animal_id, lower(vaccine_name), administered_at DESC);

-- Denetim izi ekranı: filtresiz liste en yeni kayıttan başlar (en fazla 500 kayıt).
CREATE INDEX IF NOT EXISTS ix_audit_org_occurred
    ON vet.audit_logs (organization_id, occurred_at DESC);
