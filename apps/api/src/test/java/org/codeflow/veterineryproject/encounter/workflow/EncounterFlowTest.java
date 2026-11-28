package org.codeflow.veterineryproject.encounter.workflow;

import org.codeflow.veterineryproject.common.api.BusinessRuleException;
import org.codeflow.veterineryproject.common.api.ConflictException;
import org.codeflow.veterineryproject.encounter.dto.EncounterFlowResponse;
import org.codeflow.veterineryproject.encounter.dto.EncounterResponse;
import org.codeflow.veterineryproject.encounter.dto.EncounterStatus;
import org.codeflow.veterineryproject.encounter.dto.TreatmentResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Template Method: muayene türleri (FG-C2) aynı iskeletle farklı adım/zorunlu alan/uyarı üretir; DB'siz. */
@DisplayName("Muayene akış türleri (Template Method)")
class EncounterFlowTest {

    private final EncounterFlows flows = EncounterFlows.standard();
    private final AdmissionStep admission = new AdmissionStep();
    private final ExaminationStep examination = new ExaminationStep();
    private final DiagnosisStep diagnosis = new DiagnosisStep();
    private final PlanStep plan = new PlanStep();
    private final CloseStep close = new CloseStep();

    @Test
    @DisplayName("tür eşlemesi: kontrol ve tele-tıp genel akışı, aşı/acil/cerrahi kendi akışını kullanır")
    void testForTypeGivenEncounterTypesReturnsMatchingFlow() {
        assertThat(flows.forType("CONSULTATION")).isInstanceOf(GeneralEncounterFlow.class);
        assertThat(flows.forType("FOLLOW_UP")).isInstanceOf(GeneralEncounterFlow.class);
        assertThat(flows.forType("VACCINATION")).isInstanceOf(VaccinationVisitFlow.class);
        assertThat(flows.forType("EMERGENCY")).isInstanceOf(EmergencyEncounterFlow.class);
        assertThat(flows.forType("SURGERY")).isInstanceOf(SurgeryEncounterFlow.class);
        assertThat(flows.forType("BILINMEYEN")).isInstanceOf(GeneralEncounterFlow.class);
    }

    @Test
    @DisplayName("aşı ziyareti tanı adımını atlar: incelemeden sonra plan gelir, tanı adımı 409")
    void testVaccinationFlowGivenExaminedEncounterSkipsDiagnosis() {
        EncounterFlow flow = flows.forType("VACCINATION");
        EncounterResponse admitted = admission.execute(draft("VACCINATION"), new AdmissionStep.Input(null, null), flow);
        EncounterResponse examined = examination.execute(admitted.withVitals(new BigDecimal("8"), new BigDecimal("38.5")), null, flow);

        assertThatThrownBy(() -> diagnosis.execute(examined, "Tanı", flow)).isInstanceOf(ConflictException.class)
                .hasMessageContaining("Aşı ziyareti");
        EncounterResponse planned = plan.execute(examined, new PlanStep.Input("Karma 2. doz", null), flow);
        assertThat(planned.status()).isEqualTo(EncounterStatus.PLAN);
        assertThat(close.execute(planned, null, flow).status()).isEqualTo(EncounterStatus.CLOSED);
    }

    @Test
    @DisplayName("aşı ziyareti incelemede kilo ve ısı ister; ateşte uyarı verir")
    void testVaccinationFlowGivenMissingVitalsRejectsAndFeverWarns() {
        EncounterFlow flow = flows.forType("VACCINATION");
        EncounterResponse admitted = admission.execute(draft("VACCINATION"), new AdmissionStep.Input(null, null), flow);

        assertThatThrownBy(() -> examination.execute(admitted, "Bulgu", flow)).isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Kilo");
        EncounterResponse feverish = examination.execute(admitted.withVitals(new BigDecimal("8"), new BigDecimal("39.6")), null, flow);
        assertThat(feverish.warnings()).singleElement().asString().contains("Ateşli");
    }

    @Test
    @DisplayName("acil muayene kabulde vücut ısısı ister ve kritik ısıda uyarır; planda uygulanan işlem zorunlu")
    void testEmergencyFlowGivenAdmissionRequiresTemperatureAndPlanRequiresProcedure() {
        EncounterFlow flow = flows.forType("EMERGENCY");
        assertThatThrownBy(() -> admission.execute(draft("EMERGENCY"), new AdmissionStep.Input("Kanama", null), flow))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("Vücut ısısı");

        EncounterResponse admitted = admission.execute(draft("EMERGENCY").withVitals(null, new BigDecimal("36.8")),
                new AdmissionStep.Input("Kanama", null), flow);
        assertThat(admitted.warnings()).singleElement().asString().contains("Kritik vücut ısısı: 36.8");
        EncounterResponse diagnosed = diagnosis.execute(examination.execute(admitted, "Yara", flow), "Laserasyon", flow);
        assertThatThrownBy(() -> plan.execute(diagnosed, new PlanStep.Input("Dikiş", null), flow))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("Uygulanan işlem");

        EncounterResponse withProcedure = diagnosed.withDetails(List.of(), List.of(procedure(diagnosed.id())));
        assertThat(plan.execute(withProcedure, new PlanStep.Input("Dikiş", null), flow).status()).isEqualTo(EncounterStatus.PLAN);
    }

    @Test
    @DisplayName("cerrahi kabulde anamnez, incelemede kilo ister; operasyon notu yoksa kapatmada uyarır")
    void testSurgeryFlowGivenMissingFieldsRejectsAndWarnsOnClose() {
        EncounterFlow flow = flows.forType("SURGERY");
        assertThatThrownBy(() -> admission.execute(draft("SURGERY"), new AdmissionStep.Input("Kısırlaştırma", null), flow))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("Anamnez");
        EncounterResponse admitted = admission.execute(draft("SURGERY"), new AdmissionStep.Input("Kısırlaştırma", "Sağlıklı"), flow);
        assertThatThrownBy(() -> examination.execute(admitted, "Normal", flow)).hasMessageContaining("Kilo");

        EncounterResponse examined = examination.execute(admitted.withVitals(new BigDecimal("4.2"), null), "Normal", flow);
        EncounterResponse planned = plan.execute(diagnosis.execute(examined, "Sağlıklı", flow)
                .withDetails(List.of(), List.of(procedure(examined.id()))), new PlanStep.Input("Kontrol", null), flow);
        EncounterResponse closed = close.execute(planned, null, flow);

        assertThat(closed.status()).isEqualTo(EncounterStatus.CLOSED);
        assertThat(closed.warnings()).singleElement().asString().contains("Operasyon notu");
        assertThat(close.execute(planned, "Komplikasyonsuz", flow).warnings()).isEmpty();
    }

    @Test
    @DisplayName("genel akışta sıra dışı adım 409, kapatma tüm adımların zorunlu alanlarını ister")
    void testGeneralFlowGivenOutOfOrderStepIsConflict() {
        EncounterFlow flow = flows.forType("CONSULTATION");
        EncounterResponse admitted = admission.execute(draft("CONSULTATION"), new AdmissionStep.Input("Öksürük", null), flow);

        assertThatThrownBy(() -> plan.execute(admitted, new PlanStep.Input("x", null), flow)).isInstanceOf(ConflictException.class);
        assertThatThrownBy(() -> close.execute(admitted.withStatus(EncounterStatus.PLAN), null, flow))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("Fizik muayene");
    }

    @Test
    @DisplayName("akış tanımı adımları, girilebilen ve zorunlu alanları JSON adlarıyla verir")
    void testDescribeGivenFlowsListsStepsAndFields() {
        EncounterFlowResponse vaccination = flows.forType("VACCINATION").describe();
        EncounterFlowResponse general = flows.forType("OTHER").describe();

        assertThat(vaccination.steps()).extracting(EncounterFlowResponse.Step::status)
                .containsExactly(EncounterStatus.ADMISSION, EncounterStatus.EXAMINATION, EncounterStatus.PLAN);
        assertThat(vaccination.steps().get(1).required()).containsExactly("weightKg", "temperatureC");
        assertThat(vaccination.steps().get(2).fields()).contains("assessment", "treatmentPlan", "procedures", "vetNotes");
        assertThat(general.encounterTypes()).containsExactly("CONSULTATION", "FOLLOW_UP", "OTHER", "TELEMEDICINE");
        assertThat(general.steps()).hasSize(4);
    }

    private static EncounterResponse draft(String type) {
        return new EncounterResponse(UUID.randomUUID(), "E-000001", UUID.randomUUID(), null, null, UUID.randomUUID(), null,
                UUID.randomUUID(), type, null, OffsetDateTime.now(), null, null, null, null, null, null, null, null,
                UUID.randomUUID(), null, null, List.of(), List.of());
    }

    private static TreatmentResponse procedure(UUID encounterId) {
        return new TreatmentResponse(UUID.randomUUID(), encounterId, "PROCEDURE", "İşlem", null, null, null, null, null, null,
                false, null, OffsetDateTime.now(), UUID.randomUUID(), OffsetDateTime.now());
    }
}
