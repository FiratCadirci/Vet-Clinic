package org.codeflow.veterineryproject.vaccine.factory;

import org.codeflow.veterineryproject.vaccine.dto.ProtocolResponse;
import org.codeflow.veterineryproject.vaccine.dto.VaccineScheduleTemplate;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Factory Method: türe göre aşı takvimi üretir. {@link #createDraft()} türün varsayılan (onaysız) şablon
 * taslağını, {@link #create} ise onaylı protokollerden hastaya özel takvim önerisini üretir.
 */
public interface VaccineScheduleFactory {

    record AnimalProfile(UUID id, String speciesCode, LocalDate birthDate) {
    }

    boolean supports(String speciesCode);

    VaccineScheduleTemplate create(AnimalProfile animal, List<ProtocolResponse> approvedProtocols, LocalDate today);

    /** Türün varsayılan aşı serileri (taslak); hazır takvimi olmayan türde boş liste. */
    List<ProtocolDraft> createDraft();
}
