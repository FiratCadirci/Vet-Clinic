package org.codeflow.veterineryproject.vaccine.factory;

/**
 * Fabrikanın önerdiği tek aşı serisi (onaysız taslak). code türe özgü önek olmadan verilir (ör. "CORE");
 * kayıtta "DOG-CORE" olur. İlk doz yaşı gün, aralıklar gün cinsindendir. Tıbbi takvim klinik tarafından doğrulanır.
 */
public record ProtocolDraft(String code, String vaccineName, int firstDoseAgeDays, int doseCount, int doseIntervalDays,
                            int boosterIntervalDays) {

    private static final int DAYS_PER_WEEK = 7;

    /** Yaşı hafta cinsinden veren kısa kurucu (takvimler haftayla anlatılır). */
    static ProtocolDraft weeks(String code, String vaccineName, int firstDoseAgeWeeks, int doseCount, int doseIntervalDays,
                               int boosterIntervalDays) {
        return new ProtocolDraft(code, vaccineName, firstDoseAgeWeeks * DAYS_PER_WEEK, doseCount, doseIntervalDays,
                boosterIntervalDays);
    }
}
