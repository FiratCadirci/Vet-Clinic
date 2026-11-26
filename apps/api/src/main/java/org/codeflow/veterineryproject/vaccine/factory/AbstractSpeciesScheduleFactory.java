package org.codeflow.veterineryproject.vaccine.factory;

import org.codeflow.veterineryproject.vaccine.dto.ProtocolDoseResponse;
import org.codeflow.veterineryproject.vaccine.dto.ProtocolResponse;
import org.codeflow.veterineryproject.vaccine.dto.VaccineScheduleTemplate;
import org.codeflow.veterineryproject.vaccine.dto.VaccineScheduleTemplate.Entry;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Ortak şablon üretimi; türlere özgü kural: ilk doz için en erken yaş. */
abstract class AbstractSpeciesScheduleFactory implements VaccineScheduleFactory {

    /** İlk dozun uygulanabileceği en erken yaş (gün). */
    protected abstract int minimumFirstDoseAgeDays();

    protected boolean appliesTo(String protocolSpeciesCode, String animalSpeciesCode) {
        return protocolSpeciesCode != null && protocolSpeciesCode.equalsIgnoreCase(animalSpeciesCode);
    }

    @Override
    public VaccineScheduleTemplate create(AnimalProfile animal, List<ProtocolResponse> approvedProtocols, LocalDate today) {
        Integer ageDays = animal.birthDate() == null ? null : (int) ChronoUnit.DAYS.between(animal.birthDate(), today);
        List<Entry> entries = new ArrayList<>();
        approvedProtocols.stream()
                .filter(p -> "APPROVED".equals(p.status()) && appliesTo(p.speciesCode(), animal.speciesCode()))
                .sorted(Comparator.comparing(ProtocolResponse::code))
                .forEach(protocol -> {
                    for (ProtocolDoseResponse dose : protocol.doses().stream()
                            .sorted(Comparator.comparingInt(ProtocolDoseResponse::doseNumber)).toList()) {
                        int minimum = Math.max(dose.recommendedAgeDays() == null ? 0 : dose.recommendedAgeDays(),
                                dose.doseNumber() == 1 ? minimumFirstDoseAgeDays() : 0);
                        LocalDate earliest = animal.birthDate() == null ? null : animal.birthDate().plusDays(minimum);
                        boolean eligible = earliest == null || !today.isBefore(earliest);
                        entries.add(new Entry(protocol.id(), protocol.code(), protocol.name(), dose.id(), dose.doseNumber(),
                                dose.vaccineName(), minimum, earliest, eligible, dose.intervalFromPreviousDays(),
                                protocol.boosterIntervalDays()));
                    }
                });
        return new VaccineScheduleTemplate(animal.speciesCode(), ageDays, entries);
    }
}
