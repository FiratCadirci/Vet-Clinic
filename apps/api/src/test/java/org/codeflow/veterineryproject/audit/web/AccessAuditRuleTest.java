package org.codeflow.veterineryproject.audit.web;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Görüntüleme denetimi kuralları (FG-J4): hangi yolların kayda düştüğü, DB'siz. */
class AccessAuditRuleTest {

    private static final UUID ID = UUID.randomUUID();

    @Test
    void criticalRecordPaths_matchWithEntityId() {
        assertThat(AccessAuditRule.match("/api/v1/patients/" + ID)).hasValueSatisfying(m -> {
            assertThat(m.rule().action()).isEqualTo("ANIMAL_VIEW");
            assertThat(m.rule().entityType()).isEqualTo("ANIMAL");
            assertThat(m.entityId()).isEqualTo(ID);
        });
        assertThat(AccessAuditRule.match("/api/v1/animals/" + ID).orElseThrow().rule().action()).isEqualTo("ANIMAL_VIEW");
        assertThat(AccessAuditRule.match("/api/v1/patients/" + ID + "/history").orElseThrow().rule().action())
                .isEqualTo("ANIMAL_HISTORY_VIEW");
        assertThat(AccessAuditRule.match("/api/v1/animals/" + ID + "/history").orElseThrow().rule().action())
                .isEqualTo("ANIMAL_HISTORY_VIEW");
        assertThat(AccessAuditRule.match("/api/v1/clients/" + ID).orElseThrow().rule().action()).isEqualTo("CLIENT_VIEW");
        assertThat(AccessAuditRule.match("/api/v1/encounters/" + ID).orElseThrow().rule().action())
                .isEqualTo("ENCOUNTER_VIEW");
        assertThat(AccessAuditRule.match("/api/v1/attachments/" + ID + "/content").orElseThrow().rule().action())
                .isEqualTo("CLINICAL_DOCUMENT_DOWNLOAD");
    }

    @Test
    void listsSubResourcesAndNonUuidSegments_doNotMatch() {
        assertThat(AccessAuditRule.match("/api/v1/patients")).isEmpty();
        assertThat(AccessAuditRule.match("/api/v1/animals/by-chip/123")).isEmpty();
        assertThat(AccessAuditRule.match("/api/v1/animals/not-a-uuid")).isEmpty();
        assertThat(AccessAuditRule.match("/api/v1/animals/" + ID + "/weights")).isEmpty();
        assertThat(AccessAuditRule.match("/api/v1/encounters/" + ID + "/treatments")).isEmpty();
        assertThat(AccessAuditRule.match("/api/v1/products/" + ID)).isEmpty();
    }
}
