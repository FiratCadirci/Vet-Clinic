package org.codeflow.veterineryproject.encounter.dto;

import java.util.List;

/** Muayene türünün akış tanımı: adımlar sırasıyla, her adımda girilebilen ve zorunlu alanlar (JSON alan adları). */
public record EncounterFlowResponse(String code, String label, List<String> encounterTypes, List<Step> steps) {

    public record Step(EncounterStatus status, String label, List<String> fields, List<String> required) {
    }
}
