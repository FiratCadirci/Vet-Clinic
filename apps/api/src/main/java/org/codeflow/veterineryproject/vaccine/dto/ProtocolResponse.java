package org.codeflow.veterineryproject.vaccine.dto;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record ProtocolResponse(UUID id, String code, String name, UUID speciesId, String speciesCode, int versionNo,
                               String status, Integer boosterIntervalDays, String notes, UUID approvedBy,
                               OffsetDateTime approvedAt, List<ProtocolDoseResponse> doses) {

    public ProtocolResponse withDoses(List<ProtocolDoseResponse> value) {
        return new ProtocolResponse(id, code, name, speciesId, speciesCode, versionNo, status, boosterIntervalDays, notes,
                approvedBy, approvedAt, value);
    }
}
