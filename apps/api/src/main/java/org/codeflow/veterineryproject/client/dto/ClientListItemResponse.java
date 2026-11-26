package org.codeflow.veterineryproject.client.dto;

import com.fasterxml.jackson.annotation.JsonUnwrapped;

import java.util.List;
import java.util.UUID;

/** ClientResponse alanlarının tamamı + hayvan özeti. animals en fazla ilk 5 hayvandır; animalCount gerçek toplamdır. */
public record ClientListItemResponse(
        @JsonUnwrapped ClientResponse client,
        long animalCount,
        List<ClientAnimalSummary> animals,
        List<UUID> matchedAnimalIds
) {
}
