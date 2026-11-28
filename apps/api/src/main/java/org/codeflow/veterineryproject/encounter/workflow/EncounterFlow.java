package org.codeflow.veterineryproject.encounter.workflow;

import org.codeflow.veterineryproject.common.api.BusinessRuleException;
import org.codeflow.veterineryproject.common.api.ConflictException;
import org.codeflow.veterineryproject.encounter.dto.EncounterFlowResponse;
import org.codeflow.veterineryproject.encounter.dto.EncounterResponse;
import org.codeflow.veterineryproject.encounter.dto.EncounterStatus;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.codeflow.veterineryproject.encounter.dto.EncounterStatus.ADMISSION;
import static org.codeflow.veterineryproject.encounter.dto.EncounterStatus.CLOSED;
import static org.codeflow.veterineryproject.encounter.dto.EncounterStatus.DIAGNOSIS;
import static org.codeflow.veterineryproject.encounter.dto.EncounterStatus.EXAMINATION;
import static org.codeflow.veterineryproject.encounter.dto.EncounterStatus.PLAN;

/**
 * Template Method (FG-C1, FG-C2): muayene türünün akışı. İskelet sabittir ve alt sınıflarca değiştirilemez
 * ({@link #previousOf}, {@link #completeStep}, {@link #close}, {@link #describe}); türler yalnızca kancaları
 * özelleştirir: adım listesi ({@link #steps()}), adımda girilebilen ({@link #fieldsFor}) ve zorunlu
 * ({@link #requiredFor}) alanlar, adım sonrası ({@link #onStepCompleted}) ve kapatma öncesi ({@link #beforeClose}) uyarılar.
 * Adımın kendisini (kapalı mı, sıra doğru mu, veriyi uygula) {@link EncounterStep} yürütür; geçişi bu akışa sorar.
 */
public abstract class EncounterFlow {

    private static final Map<EncounterStatus, String> STEP_LABELS = Map.of(ADMISSION, "Kabul", EXAMINATION, "İnceleme",
            DIAGNOSIS, "Tanı", PLAN, "Tedavi planı", CLOSED, "Kapatma");

    private static final Map<EncounterStatus, Set<FlowField>> DEFAULT_FIELDS = Map.of(
            ADMISSION, EnumSet.of(FlowField.CHIEF_COMPLAINT, FlowField.HISTORY, FlowField.WEIGHT, FlowField.TEMPERATURE),
            EXAMINATION, EnumSet.of(FlowField.EXAMINATION_SUMMARY, FlowField.WEIGHT, FlowField.TEMPERATURE),
            DIAGNOSIS, EnumSet.of(FlowField.ASSESSMENT),
            PLAN, EnumSet.of(FlowField.TREATMENT_PLAN, FlowField.PROCEDURES));

    /** Akış kodu (GENERAL, VACCINATION, EMERGENCY, SURGERY). */
    public abstract String code();

    public abstract String label();

    /** Bu akışı kullanan encounter_type değerleri. */
    public abstract Set<String> encounterTypes();

    // ------------------------------------------------------------------ kancalar

    /** Akıştaki doldurulabilir adımlar, sırasıyla; son adımdan sonra muayene kapatılabilir. */
    protected List<EncounterStatus> steps() {
        return List.of(ADMISSION, EXAMINATION, DIAGNOSIS, PLAN);
    }

    /** Adımda girilebilen alanlar (hekim notu her adımda girilebilir). */
    protected Set<FlowField> fieldsFor(EncounterStatus step) {
        return DEFAULT_FIELDS.get(step);
    }

    /** Adım tamamlanırken dolu olması gereken alanlar. */
    protected abstract Set<FlowField> requiredFor(EncounterStatus step);

    /** Adım tamamlandıktan sonra çalışan kanca; uyarı listesi döner (kaydı engellemez). */
    protected List<String> onStepCompleted(EncounterStatus step, EncounterResponse encounter) {
        return List.of();
    }

    /** Kapatmadan hemen önce çalışan kanca; uyarı listesi döner. */
    protected List<String> beforeClose(EncounterResponse encounter) {
        return List.of();
    }

    // ------------------------------------------------------------------ şablon metotlar (değişmez)

    /**
     * {@code target} adımına geçmek için muayenenin bulunması gereken durum: kabul için null (kayıt yok),
     * kapatma için son adım. Adım bu akışta yoksa 409.
     */
    public final EncounterStatus previousOf(EncounterStatus target) {
        List<EncounterStatus> steps = steps();
        if (target == CLOSED) {
            return steps.getLast();
        }
        int index = steps.indexOf(target);
        if (index < 0) {
            throw new ConflictException("%s akışında '%s' adımı yoktur.".formatted(label(), STEP_LABELS.get(target)));
        }
        return index == 0 ? null : steps.get(index - 1);
    }

    /** Adımın zorunlu alanlarını denetler (eksikse 422) ve adım kancasının uyarılarını döner. */
    public final List<String> completeStep(EncounterStatus step, EncounterResponse after) {
        requireFields(step, after, "%s zorunludur.");
        return onStepCompleted(step, after);
    }

    /** Kapatma: akıştaki tüm adımların zorunlu alanları dolu olmalı; ardından kapatma kancası. */
    public final List<String> close(EncounterResponse current) {
        for (EncounterStatus step : steps()) {
            requireFields(step, current, "%s olmadan muayene kapatılamaz.");
        }
        return beforeClose(current);
    }

    /** İstemcinin adımları çizebilmesi için akış tanımı. */
    public final EncounterFlowResponse describe() {
        List<EncounterFlowResponse.Step> described = steps().stream().map(step -> new EncounterFlowResponse.Step(step,
                STEP_LABELS.get(step), names(withVetNotes(fieldsFor(step))), names(requiredFor(step)))).toList();
        return new EncounterFlowResponse(code(), label(), encounterTypes().stream().sorted().toList(), described);
    }

    private void requireFields(EncounterStatus step, EncounterResponse encounter, String template) {
        Set<FlowField> required = requiredFor(step);
        for (FlowField field : FlowField.values()) {
            if (required.contains(field) && !field.isFilledIn(encounter)) {
                throw new BusinessRuleException(template.formatted(field.label()));
            }
        }
    }

    private static Set<FlowField> withVetNotes(Set<FlowField> fields) {
        EnumSet<FlowField> all = EnumSet.noneOf(FlowField.class);
        all.addAll(fields);
        all.add(FlowField.VET_NOTES);
        return all;
    }

    private static List<String> names(Set<FlowField> fields) {
        List<String> out = new ArrayList<>();
        for (FlowField field : FlowField.values()) {
            if (fields.contains(field)) {
                out.add(field.jsonName());
            }
        }
        return out;
    }
}
