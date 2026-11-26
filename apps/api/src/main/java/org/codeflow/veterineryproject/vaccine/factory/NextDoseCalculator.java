package org.codeflow.veterineryproject.vaccine.factory;

import org.codeflow.veterineryproject.vaccine.dto.ProtocolDoseResponse;
import org.codeflow.veterineryproject.vaccine.dto.ProtocolResponse;

import java.time.LocalDate;
import java.util.Optional;

/** Sonraki doz tarihi: bir sonraki dozun aralığı, son dozdan sonra rapel aralığı. Sonuç hekim tarafından değiştirilebilir. */
public final class NextDoseCalculator {

    private NextDoseCalculator() {
    }

    public static Optional<LocalDate> compute(LocalDate administered, ProtocolResponse protocol, int doseNumber) {
        if (protocol == null) {
            return Optional.empty();
        }
        Optional<ProtocolDoseResponse> next = protocol.doses().stream().filter(d -> d.doseNumber() == doseNumber + 1).findFirst();
        if (next.isPresent() && next.get().intervalFromPreviousDays() != null) {
            return Optional.of(administered.plusDays(next.get().intervalFromPreviousDays()));
        }
        if (next.isEmpty() && protocol.boosterIntervalDays() != null) {
            return Optional.of(administered.plusDays(protocol.boosterIntervalDays()));
        }
        return Optional.empty();
    }
}
