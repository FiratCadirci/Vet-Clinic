package org.codeflow.veterineryproject.appointment.service;

import org.codeflow.veterineryproject.appointment.dto.AppointmentClinicalSummaryResponse;
import org.codeflow.veterineryproject.appointment.dto.AppointmentClinicalSummaryResponse.EncounterSummary;
import org.codeflow.veterineryproject.encounter.service.EncounterService;
import org.codeflow.veterineryproject.vaccine.service.VaccinationService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class AppointmentClinicalService {

    private final EncounterService encounters;
    private final VaccinationService vaccinations;

    public AppointmentClinicalService(EncounterService encounters, VaccinationService vaccinations) {
        this.encounters = encounters;
        this.vaccinations = vaccinations;
    }

    @Transactional(readOnly = true)
    public AppointmentClinicalSummaryResponse summary(UUID appointmentId) {
        return new AppointmentClinicalSummaryResponse(appointmentId, encounters.listByAppointment(appointmentId).stream()
                .map(e -> new EncounterSummary(e, vaccinations.listByEncounter(e.id())))
                .toList());
    }
}
