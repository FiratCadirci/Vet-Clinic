package org.codeflow.veterineryproject.billing;

import com.fasterxml.jackson.databind.JsonNode;
import org.codeflow.veterineryproject.common.test.ApiTestSupport;
import org.codeflow.veterineryproject.common.test.RegistrationTestData;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.codeflow.veterineryproject.common.test.RegistrationTestData.*;
import static org.codeflow.veterineryproject.common.test.TenantTestData.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class BillingIT extends ApiTestSupport {

    private static final String CLINIC = BRANCH_A1_S1.toString();
    private static final String URL = "/api/v1/billing";

    @BeforeAll
    static void seed() {
        RegistrationTestData.seed(superUserDataSource());
    }

    private JsonNode newInvoice(String t, String price) throws Exception {
        Map<String, Object> line = Map.of("lineType", "OTHER", "description", "Muayene", "quantity", 1,
                "unitPrice", price, "taxRate", 0);
        return body(postJson(t, CLINIC, URL + "/invoices",
                Map.of("clientId", PATIENT_A1_S1, "lines", List.of(line))).andExpect(status().isCreated()));
    }

    private static String id(JsonNode inv) {
        return inv.path("invoice").path("id").asText();
    }

    private static double num(JsonNode n) {
        return n.decimalValue().doubleValue();
    }

    @Test
    void fullPaymentFlowDraftToPaid() throws Exception {
        String t = token(ORG_A_CODE, EMAIL_FRONTDESK_A1);
        JsonNode inv = newInvoice(t, "100.00");
        String id = id(inv);
        assertThat(inv.path("invoice").path("status").asText()).isEqualTo("DRAFT");
        assertThat(inv.path("invoice").path("invoiceNo").asText()).startsWith("F-");

        postJson(t, CLINIC, URL + "/invoices/" + id + "/payments", Map.of("amount", 10, "paymentMethod", "CASH"))
                .andExpect(status().isUnprocessableEntity());

        postJson(t, CLINIC, URL + "/invoices/" + id + "/issue", Map.of()).andExpect(status().isOk());

        JsonNode part = body(postJson(t, CLINIC, URL + "/invoices/" + id + "/payments",
                Map.of("amount", 40, "paymentMethod", "CASH")).andExpect(status().isCreated()));
        assertThat(part.path("invoice").path("status").asText()).isEqualTo("PARTIALLY_PAID");
        assertThat(num(part.path("invoice").path("balance"))).isEqualTo(60.0);

        postJson(t, CLINIC, URL + "/invoices/" + id + "/payments", Map.of("amount", 61, "paymentMethod", "CASH"))
                .andExpect(status().isUnprocessableEntity());

        JsonNode paid = body(postJson(t, CLINIC, URL + "/invoices/" + id + "/payments",
                Map.of("amount", 60, "paymentMethod", "CREDIT_CARD")).andExpect(status().isCreated()));
        assertThat(paid.path("invoice").path("status").asText()).isEqualTo("PAID");
        assertThat(paid.path("payments")).hasSize(2);

        JsonNode bal = body(getJson(t, URL + "/clients/" + PATIENT_A1_S1 + "/balance").andExpect(status().isOk()));
        assertThat(bal.toString()).contains("\"clientId\"");

        JsonNode daily = body(getJson(t, URL + "/cashier/daily?date=" + LocalDate.now() + "&clinicId=" + CLINIC)
                .andExpect(status().isOk()));
        assertThat(daily.toString()).contains("CASH").contains("CREDIT_CARD");
    }

    @Test
    void draftCanBeVoidedAndListFilters() throws Exception {
        String t = token(ORG_A_CODE, EMAIL_FRONTDESK_A1);
        String id = id(newInvoice(t, "50.00"));
        JsonNode voided = body(postJson(t, CLINIC, URL + "/invoices/" + id + "/void", Map.of()).andExpect(status().isOk()));
        assertThat(voided.path("invoice").path("status").asText()).isEqualTo("VOID");

        JsonNode list = body(getJson(t, URL + "/invoices?status=VOID&clientId=" + PATIENT_A1_S1).andExpect(status().isOk()));
        assertThat(list.path("content").size()).isGreaterThan(0);
        postJson(t, CLINIC, URL + "/invoices/" + id + "/issue", Map.of()).andExpect(status().isUnprocessableEntity());
    }

    @Test
    void validationAndSecurity() throws Exception {
        String t = token(ORG_A_CODE, EMAIL_FRONTDESK_A1);
        postJson(t, CLINIC, URL + "/invoices", Map.of("clientId", PATIENT_A1_S1, "lines", List.of()))
                .andExpect(status().isBadRequest());
        postJson(t, URL + "/invoices", Map.of("clientId", PATIENT_A1_S1)).andExpect(status().isBadRequest());
        getJson(token(ORG_A_CODE, EMAIL_RECEPTION_A1), URL + "/invoices").andExpect(status().isForbidden());
        getJson(t, URL + "/invoices/" + UUID.randomUUID()).andExpect(status().isNotFound());
    }
}
