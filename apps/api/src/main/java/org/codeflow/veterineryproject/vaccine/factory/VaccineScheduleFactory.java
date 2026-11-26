package org.codeflow.veterineryproject.vaccine.factory;

import org.codeflow.veterineryproject.vaccine.dto.ProtocolResponse;
import org.codeflow.veterineryproject.vaccine.dto.VaccineScheduleTemplate;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Factory Method: türe göre aşı takvimi şablonu üretir. */
public interface VaccineScheduleFactory {

    record AnimalProfile(UUID id, String speciesCode, LocalDate birthDate) {
    }

    boolean supports(String speciesCode);

    VaccineScheduleTemplate create(AnimalProfile animal, List<ProtocolResponse> approvedProtocols, LocalDate today);
}
