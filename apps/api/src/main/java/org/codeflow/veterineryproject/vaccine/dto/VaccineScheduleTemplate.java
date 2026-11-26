package org.codeflow.veterineryproject.vaccine.dto;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Türe (ve yaşa) göre üretilen aşı takvimi önerisi; klinik hekimi tarafından doğrulanmalıdır. */
public record VaccineScheduleTemplate(String speciesCode, Integer ageDays, List<Entry> entries) {

    public record Entry(UUID protocolId, String protocolCode, String protocolName, UUID doseId, int doseNumber,
                        String vaccineName, Integer minimumAgeDays, LocalDate earliestDate, boolean eligibleNow,
                        Integer intervalFromPreviousDays, Integer boosterIntervalDays) {
    }
}
