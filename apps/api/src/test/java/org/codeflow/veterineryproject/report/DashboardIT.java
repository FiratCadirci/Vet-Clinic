package org.codeflow.veterineryproject.report;

import com.fasterxml.jackson.databind.JsonNode;
import org.codeflow.veterineryproject.common.test.ApiTestSupport;
import org.codeflow.veterineryproject.common.test.ReportTestData;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.codeflow.veterineryproject.common.test.ReportTestData.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Genel bakış: seçili şubenin kartları ve rol yetkisine göre görünen bölümler. */
@SpringBootTest
@AutoConfigureMockMvc
class DashboardIT extends ApiTestSupport {

    private static final String DASHBOARD = "/api/v1/dashboard";

    @BeforeAll
    static void seed() {
        ReportTestData.seed(superUserDataSource());
    }

    @AfterAll
    static void cleanup() {
        ReportTestData.cleanup(superUserDataSource());
    }

    @Test
    void admin_seesAllCardsOfSelectedBranch() throws Exception {
        JsonNode d = body(getJson(token(ORG_R_CODE, EMAIL_ADMIN), R1.toString(), DASHBOARD).andExpect(status().isOk()));
        assertThat(d.get("clinicId").asText()).isEqualTo(R1.toString());
        assertThat(d.get("date").asText()).isEqualTo(today().toString());

        // KPI: son 30 gün (R1): bugünkü açık muayene ve bugünkü iki randevu (biri iptal).
        JsonNode kpis = d.get("kpis");
        assertThat(kpis.get("examinations").asLong()).isEqualTo(1);
        assertThat(kpis.get("appointments").get("BOOKED").asLong()).isEqualTo(1);
        assertThat(kpis.get("appointments").get("CANCELLED").asLong()).isEqualTo(1);
        assertThat(kpis.get("overdueVaccinations").asLong()).isEqualTo(1);

        JsonNode appointments = d.get("todayAppointments");
        assertThat(appointments).hasSize(1);
        assertThat(appointments.get(0).get("id").asText()).isEqualTo(APPT_TODAY_R1.toString());
        assertThat(appointments.get(0).get("animalName").asText()).isEqualTo("Karabaş");
        assertThat(appointments.get(0).get("clientName").asText()).isEqualTo(CLIENT1_NAME);
        assertThat(appointments.get(0).get("vetName").asText()).isEqualTo(VET1_NAME);
        assertThat(appointments.get(0).get("status").asText()).isEqualTo("BOOKED");

        JsonNode open = d.get("openEncounters");
        assertThat(open).hasSize(1);
        assertThat(open.get(0).get("id").asText()).isEqualTo(ENC_OPEN_TODAY.toString());
        assertThat(open.get(0).get("status").asText()).isEqualTo("ADMISSION");

        // 14 gün içinde ya da gecikmiş, son doz: Karma (gecikmiş) ve Lepto; ölen hasta ve 30 gün sonraki doz yok.
        JsonNode due = d.get("dueVaccinations");
        assertThat(ids(due, "vaccinationId")).containsExactly(VR_KARMA_R1.toString(), VR_LEPTO_R1.toString());
        assertThat(due.get(0).get("overdue").asBoolean()).isTrue();
        assertThat(due.get(1).get("overdue").asBoolean()).isFalse();
        assertThat(due.get(0).get("clientName").asText()).isEqualTo(CLIENT1_NAME);
        assertThat(due.get(0).get("clientPhone").asText()).isEqualTo(CLIENT1_PHONE);

        // Kritik stok: Amoksisilin R1 bakiyesi 100 − 5 − 10 + 5 (Şubat) = 90 < 200.
        JsonNode low = d.get("stockAlerts").get("lowStock");
        assertThat(low).hasSize(1);
        assertThat(low.get(0).get("productId").asText()).isEqualTo(PRODUCT_AMOX.toString());
        assertThat(low.get(0).get("onHand").decimalValue()).isEqualByComparingTo("90");
    }

    @Test
    void receptionist_seesOnlyPermittedCards() throws Exception {
        JsonNode d = body(getJson(token(ORG_R_CODE, EMAIL_RECEPTION), R1.toString(), DASHBOARD).andExpect(status().isOk()));
        assertThat(d.get("kpis").isNull()).isTrue();
        assertThat(d.get("openEncounters").isNull()).isTrue();
        assertThat(d.get("todayAppointments")).hasSize(1);
        assertThat(d.get("dueVaccinations")).hasSize(2);
        assertThat(d.get("stockAlerts").isNull()).isFalse();
    }

    @Test
    void branchSelection_changesCards_andForeignBranchIsForbidden() throws Exception {
        String manager = token(ORG_R_CODE, EMAIL_MANAGER_R2);
        JsonNode d = body(getJson(manager, R2.toString(), DASHBOARD).andExpect(status().isOk()));
        assertThat(d.get("todayAppointments")).hasSize(1);
        assertThat(d.get("openEncounters")).isEmpty();
        assertThat(ids(d.get("dueVaccinations"), "vaccinationId")).containsExactly(VR_KARMA_R2.toString());
        getJson(manager, R1.toString(), DASHBOARD).andExpect(status().isForbidden());
        getJson(token(ORG_Q_CODE, EMAIL_ADMIN_Q), R1.toString(), DASHBOARD).andExpect(status().isForbidden());
    }

    private static List<String> ids(JsonNode array, String field) {
        List<String> out = new ArrayList<>();
        array.forEach(n -> out.add(n.get(field).asText()));
        return out;
    }
}
