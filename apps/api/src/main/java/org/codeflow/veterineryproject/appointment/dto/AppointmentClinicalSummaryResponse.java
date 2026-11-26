package org.codeflow.veterineryproject.appointment.dto;

import org.codeflow.veterineryproject.encounter.dto.EncounterResponse;
import org.codeflow.veterineryproject.vaccine.dto.VaccineRecordResponse;

import java.util.List;
import java.util.UUID;

/** Randevuya bağlı muayeneler; her muayenenin tedavi (EncounterResponse.treatments) ve aşı kayıtlarıyla birlikte. */
public record AppointmentClinicalSummaryResponse(UUID appointmentId, List<EncounterSummary> encounters) {

    public record EncounterSummary(EncounterResponse encounter, List<VaccineRecordResponse> vaccinations) {
    }
}
