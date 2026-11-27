package org.codeflow.veterineryproject.notification;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.codeflow.veterineryproject.common.test.RegistrationTestData.EMAIL_FRONTDESK_A1;
import static org.codeflow.veterineryproject.common.test.TenantTestData.BRANCH_A1_S1;
import static org.codeflow.veterineryproject.common.test.TenantTestData.ORG_A_CODE;
import static org.codeflow.veterineryproject.notification.NotificationTestData.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** FG-I1 "satış": fatura kesilince müşteriye uygulama içi + e-posta + push; taslak ve iptal bildirim üretmez. */
class InvoiceIssuedNotificationIT extends NotificationItSupport {

    private static final String S1 = BRANCH_A1_S1.toString();
    private static final String URL = "/api/v1/billing/invoices";

    @Test
    void issuedInvoice_ownerGetsInAppEmailAndPush_withNumberAndTotal() throws Exception {
        String device = "ExponentPushToken[owner-invoice]";
        addPushDevice(superUserDataSource(), OWNER_USER_A, device);
        String t = token(ORG_A_CODE, EMAIL_FRONTDESK_A1);
        JsonNode draft = body(postJson(t, S1, URL, Map.of("clientId", OWNER_A, "animalId", PET_A, "lines",
                List.of(Map.of("lineType", "OTHER", "description", "Muayene", "quantity", 1, "unitPrice", "250.00",
                        "taxRate", 20)))).andExpect(status().isCreated()));
        String id = draft.path("invoice").get("id").asText();
        String voided = body(postJson(t, S1, URL, Map.of("clientId", OWNER_A, "lines",
                List.of(Map.of("lineType", "OTHER", "description", "Vazgeçildi", "quantity", 1, "unitPrice", "10.00"))))
                .andExpect(status().isCreated())).path("invoice").get("id").asText();
        postJson(t, S1, URL + "/" + voided + "/void", Map.of()).andExpect(status().isOk());

        String no = body(postJson(t, S1, URL + "/" + id + "/issue", Map.of()).andExpect(status().isOk()))
                .path("invoice").get("invoiceNo").asText();

        awaitDeliveries("notification_type = 'INVOICE_ISSUED' AND client_id = ?", OWNER_A);
        assertThat(count("SELECT count(*) FROM vet.notification_outbox WHERE notification_type = 'INVOICE_ISSUED' "
                + "AND client_id = ? AND channel IN ('IN_APP','EMAIL','PUSH')", OWNER_A)).isEqualTo(3);
        String inApp = text("SELECT message_body FROM vet.notification_outbox WHERE notification_type = 'INVOICE_ISSUED' "
                + "AND client_id = ? AND channel = 'IN_APP'", OWNER_A);
        assertThat(inApp).contains(no, "300,00 TL");
        assertThat(count("SELECT count(*) FROM vet.notification_outbox WHERE notification_type = 'INVOICE_ISSUED' "
                + "AND client_id = ? AND status = 'SENT'", OWNER_A)).isEqualTo(3);
        assertThat(mail.to(EMAIL_OWNER_A)).anySatisfy(m -> assertThat(m.getText()).contains(no));
        assertThat(push.to(device)).anySatisfy(m -> assertThat(m.data()).containsEntry("type", "INVOICE_ISSUED"));
        assertThat(count("SELECT count(*) FROM vet.notification_outbox WHERE notification_type = 'INVOICE_ISSUED' "
                + "AND message_body LIKE ?", "%Vazgeçildi%")).isZero();
    }
}
