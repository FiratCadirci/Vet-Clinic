package org.codeflow.veterineryproject.search.dto;

import java.util.List;
import java.util.UUID;

public record GlobalSearchResponse(String query, List<ClientHit> clients, List<AnimalHit> animals) {

    public record ClientHit(UUID id, String displayName, String phone, boolean active) {
    }

    public record AnimalHit(UUID id, String patientNo, String name, String speciesName, String chipNo,
                            UUID ownerId, String ownerName, String ownerPhone) {
    }
}
