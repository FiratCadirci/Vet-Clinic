package org.codeflow.veterineryproject.encounter.workflow;

import org.codeflow.veterineryproject.common.api.BusinessRuleException;
import org.codeflow.veterineryproject.common.api.ConflictException;
import org.codeflow.veterineryproject.encounter.dto.EncounterResponse;
import org.codeflow.veterineryproject.encounter.dto.EncounterStatus;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EncounterStepTest {

    private final AdmissionStep admission = new AdmissionStep();
    private final ExaminationStep examination = new ExaminationStep();
    private final DiagnosisStep diagnosis = new DiagnosisStep();
    private final PlanStep plan = new PlanStep();
    private final CloseStep close = new CloseStep();

    @Test
    void template_validSequenceMovesThroughAllStatuses() {
        EncounterResponse e = admission.execute(draft(), new AdmissionStep.Input(" Topallama ", null));
        assertThat(e.status()).isEqualTo(EncounterStatus.ADMISSION);
        assertThat(e.chiefComplaint()).isEqualTo("Topallama");

        e = examination.execute(e, "Sol arka bacak şiş");
        assertThat(e.status()).isEqualTo(EncounterStatus.EXAMINATION);
        e = diagnosis.execute(e, "Burkulma");
        assertThat(e.status()).isEqualTo(EncounterStatus.DIAGNOSIS);
        e = plan.execute(e, new PlanStep.Input("İstirahat", null));
        assertThat(e.status()).isEqualTo(EncounterStatus.PLAN);
        e = close.execute(e, "Not");
        assertThat(e.status()).isEqualTo(EncounterStatus.CLOSED);
        assertThat(e.endedAt()).isNotNull();
        assertThat(e.vetNotes()).isEqualTo("Not");
    }

    @Test
    void template_skippingAStepIsConflict() {
        EncounterResponse admitted = admission.execute(draft(), new AdmissionStep.Input("Şikâyet", null));

        assertThatThrownBy(() -> diagnosis.execute(admitted, "Tanı")).isInstanceOf(ConflictException.class);
        assertThatThrownBy(() -> close.execute(admitted, null)).isInstanceOf(ConflictException.class);
    }

    @Test
    void template_repeatingAStepIsConflict() {
        EncounterResponse examined = examination.execute(admission.execute(draft(), new AdmissionStep.Input("Şikâyet", null)), "Bulgu");

        assertThatThrownBy(() -> examination.execute(examined, "Yeni bulgu")).isInstanceOf(ConflictException.class);
    }

    @Test
    void template_closedEncounterRejectsEveryStep() {
        EncounterResponse closed = admission.execute(draft(), new AdmissionStep.Input("Şikâyet", null)).withStatus(EncounterStatus.CLOSED);

        assertThatThrownBy(() -> examination.execute(closed, "x")).isInstanceOf(ConflictException.class)
                .hasMessageContaining("Kapatılmış");
        assertThatThrownBy(() -> close.execute(closed, null)).isInstanceOf(ConflictException.class);
    }

    @Test
    void template_validationFailuresAreBusinessRuleErrors() {
        assertThatThrownBy(() -> admission.execute(draft(), new AdmissionStep.Input("  ", null))).isInstanceOf(BusinessRuleException.class);

        EncounterResponse planned = admission.execute(draft(), new AdmissionStep.Input("Şikâyet", null)).withStatus(EncounterStatus.PLAN);
        assertThatThrownBy(() -> close.execute(planned, null)).isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Fizik muayene");
    }

    @Test
    void template_followUpBeforeStartIsRejected() {
        EncounterResponse diagnosed = admission.execute(draft(), new AdmissionStep.Input("Şikâyet", null)).withStatus(EncounterStatus.DIAGNOSIS);

        assertThatThrownBy(() -> plan.execute(diagnosed, new PlanStep.Input("Plan", diagnosed.startedAt().minusDays(1))))
                .isInstanceOf(BusinessRuleException.class);
    }

    private EncounterResponse draft() {
        return new EncounterResponse(UUID.randomUUID(), "E-000001", UUID.randomUUID(), null, null, UUID.randomUUID(), null,
                UUID.randomUUID(), "CONSULTATION", null, OffsetDateTime.now(), null, null, null, null, null, null, null, null,
                UUID.randomUUID(), null, null, List.of(), List.of());
    }
}
