package org.codeflow.veterineryproject.encounter.workflow;

import org.codeflow.veterineryproject.encounter.dto.EncounterResponse;

import java.util.function.Predicate;

/** Muayene akışında girilen/zorunlu tutulabilen klinik alanlar; JSON adı istek/yanıt alan adıyla aynıdır. */
public enum FlowField {
    CHIEF_COMPLAINT("chiefComplaint", "Şikâyet", e -> filled(e.chiefComplaint())),
    HISTORY("history", "Anamnez", e -> filled(e.history())),
    WEIGHT("weightKg", "Kilo (kg)", e -> e.weightKg() != null),
    TEMPERATURE("temperatureC", "Vücut ısısı (°C)", e -> e.temperatureC() != null),
    EXAMINATION_SUMMARY("examinationSummary", "Fizik muayene bulgusu", e -> filled(e.examinationSummary())),
    ASSESSMENT("assessment", "Tanı", e -> filled(e.assessment())),
    TREATMENT_PLAN("treatmentPlan", "Tedavi planı", e -> filled(e.treatmentPlan())),
    /** Uygulanan işlemler: muayeneye eklenmiş en az bir PROCEDURE tedavi kalemi. */
    PROCEDURES("procedures", "Uygulanan işlem", e -> e.treatments() != null
            && e.treatments().stream().anyMatch(t -> "PROCEDURE".equals(t.itemType()))),
    VET_NOTES("vetNotes", "Hekim notu", e -> filled(e.vetNotes()));

    private final String jsonName;
    private final String label;
    private final Predicate<EncounterResponse> filled;

    FlowField(String jsonName, String label, Predicate<EncounterResponse> filled) {
        this.jsonName = jsonName;
        this.label = label;
        this.filled = filled;
    }

    public String jsonName() {
        return jsonName;
    }

    public String label() {
        return label;
    }

    public boolean isFilledIn(EncounterResponse encounter) {
        return filled.test(encounter);
    }

    private static boolean filled(String value) {
        return value != null && !value.isBlank();
    }
}
