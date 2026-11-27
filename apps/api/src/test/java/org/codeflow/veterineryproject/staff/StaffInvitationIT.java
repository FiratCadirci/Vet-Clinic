package org.codeflow.veterineryproject.staff;

import com.fasterxml.jackson.databind.JsonNode;
import org.codeflow.veterineryproject.common.event.DomainEvents.StaffInvitedEvent;
import org.codeflow.veterineryproject.common.test.OrgApiTestSupport;
import org.codeflow.veterineryproject.common.test.OrgTestData;
import org.codeflow.veterineryproject.staff.service.InvitationToken;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** FG-A2 personel daveti, FG-A5 şube yöneticisi kısıtı, FG-K1 kullanıcı limiti (eşzamanlı kabul dahil). */
@SpringBootTest
@AutoConfigureMockMvc
@RecordApplicationEvents
class StaffInvitationIT extends OrgApiTestSupport {

    @Autowired
    private ApplicationEvents events;

    private Map<String, Object> invite(String email, UUID roleId, UUID... clinics) {
        return Map.of("email", email, "roleId", roleId, "clinicIds", List.of(clinics));
    }

    private UUID role(Session s, String code) {
        return OrgTestData.roleId(superUserDataSource(), s.organizationId(), code);
    }

    @Test
    void create_publishesEventWithRawToken_responseHasNoToken_thenPreviewAndAcceptOpenSession() throws Exception {
        Session admin = signup(OrgTestData.createPlan(superUserDataSource(), 2, 5));
        String email = uniqueEmail("hekim");

        JsonNode created = body(postJson(admin.token(), admin.clinic(), "/api/v1/staff-invitations",
                invite(email.toUpperCase(java.util.Locale.ROOT), role(admin, "veterinarian"), admin.clinicId())).andExpect(status().isCreated()));
        assertThat(created.get("status").asText()).isEqualTo("PENDING");
        assertThat(created.get("email").asText()).isEqualTo(email);
        assertThat(created.toString()).doesNotContainIgnoringCase("token");

        List<StaffInvitedEvent> published = events.stream(StaffInvitedEvent.class).toList();
        assertThat(published).hasSize(1);
        StaffInvitedEvent event = published.get(0);
        assertThat(event.invitationId().toString()).isEqualTo(created.get("id").asText());
        assertThat(event.organizationId()).isEqualTo(admin.organizationId());
        assertThat(event.email()).isEqualTo(email);
        assertThat(event.inviteToken()).hasSizeGreaterThanOrEqualTo(40);
        // DB'de yalnızca özet saklanır.
        assertThat(OrgTestData.count(superUserDataSource(), "SELECT count(*) FROM vet.staff_invitations WHERE token_hash = ?",
                new InvitationToken(event.inviteToken()).hash())).isEqualTo(1);
        assertThat(OrgTestData.count(superUserDataSource(), "SELECT count(*) FROM vet.staff_invitations WHERE token_hash = ?",
                event.inviteToken())).isZero();
        // Geçerlilik 7 gün.
        assertThat(OrgTestData.count(superUserDataSource(), """
                SELECT count(*) FROM vet.staff_invitations WHERE id = ?::uuid
                  AND expires_at BETWEEN now() + interval '6 days 23 hours' AND now() + interval '7 days 1 minute'
                """, created.get("id").asText())).isEqualTo(1);

        mvc.perform(post("/api/auth/invitations/preview").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("token", event.inviteToken()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.roleCode").value("veterinarian"))
                .andExpect(jsonPath("$.roleName").value("Veteriner hekim"))
                .andExpect(jsonPath("$.organizationName").value(event.organizationName()));
        assertThat(event.organizationName()).startsWith(OrgTestData.ORG_PREFIX);

        Session vet = accept(event.inviteToken(), email);
        assertThat(vet.organizationId()).isEqualTo(admin.organizationId());
        assertThat(vet.clinicId()).isEqualTo(admin.clinicId());
        assertThat(vet.body().get("roles").get(0).get("code").asText()).isEqualTo("veterinarian");
        assertThat(vet.body().get("mustChangePassword").asBoolean()).isFalse();
        getJson(vet.token(), "/api/auth/me").andExpect(status().isOk());

        acceptRaw(event.inviteToken()).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("INVITATION_ALREADY_ACCEPTED"));
        getJson(admin.token(), "/api/v1/staff-invitations?status=ACCEPTED").andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(created.get("id").asText()));
    }

    @Test
    void expiredRevokedAndUnknownInvitationsAreRejected() throws Exception {
        Session admin = signup(OrgTestData.createPlan(superUserDataSource(), 2, 10));
        String expired = OrgTestData.insertInvitation(superUserDataSource(), admin.organizationId(), uniqueEmail("eski"),
                "receptionist", admin.userId(), List.of(admin.clinicId()));
        OrgTestData.expireInvitation(superUserDataSource(), expired);

        mvc.perform(post("/api/auth/invitations/preview").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("token", expired))))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("INVITATION_EXPIRED"));
        acceptRaw(expired).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("INVITATION_EXPIRED"));

        JsonNode pending = body(postJson(admin.token(), admin.clinic(), "/api/v1/staff-invitations",
                invite(uniqueEmail("iptal"), role(admin, "receptionist"), admin.clinicId())).andExpect(status().isCreated()));
        String token = events.stream(StaffInvitedEvent.class).toList().get(0).inviteToken();
        postJson(admin.token(), admin.clinic(), "/api/v1/staff-invitations/" + pending.get("id").asText() + "/revoke", Map.of())
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("REVOKED"));
        postJson(admin.token(), admin.clinic(), "/api/v1/staff-invitations/" + pending.get("id").asText() + "/revoke", Map.of())
                .andExpect(status().isConflict());
        acceptRaw(token).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("INVITATION_REVOKED"));

        acceptRaw("bilinmeyen-davet-kodu-0000000000").andExpect(status().isNotFound());
        assertThat(OrgTestData.count(superUserDataSource(), "SELECT count(*) FROM vet.app_users WHERE organization_id = ?",
                admin.organizationId())).isEqualTo(1);
    }

    @Test
    void reinvitingSameEmailRevokesOldLink_existingUserEmailIs409_validationIs400() throws Exception {
        Session admin = signup(OrgTestData.createPlan(superUserDataSource(), 2, 10));
        String email = uniqueEmail("tekrar");
        postJson(admin.token(), admin.clinic(), "/api/v1/staff-invitations", invite(email, role(admin, "receptionist"), admin.clinicId()))
                .andExpect(status().isCreated());
        String first = events.stream(StaffInvitedEvent.class).toList().get(0).inviteToken();
        postJson(admin.token(), admin.clinic(), "/api/v1/staff-invitations", invite(email, role(admin, "receptionist"), admin.clinicId()))
                .andExpect(status().isCreated());
        acceptRaw(first).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("INVITATION_REVOKED"));

        postJson(admin.token(), admin.clinic(), "/api/v1/staff-invitations", invite(admin.email(), role(admin, "receptionist"), admin.clinicId()))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("EMAIL_TAKEN"));
        postJson(admin.token(), admin.clinic(), "/api/v1/staff-invitations", Map.of("email", "gecersiz", "roleId", role(admin, "receptionist"),
                "clinicIds", List.of())).andExpect(status().isBadRequest());
        postJson(admin.token(), null, "/api/v1/staff-invitations", invite(uniqueEmail("x"), role(admin, "receptionist"), admin.clinicId()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void branchManagerCannotInviteClinicAdminOrIntoOtherBranches_receptionistIs403_otherOrganizationIs404() throws Exception {
        Session admin = signup(OrgTestData.createPlan(superUserDataSource(), 3, 10));
        UUID second = UUID.fromString(body(postJson(admin.token(), admin.clinic(), "/api/v1/clinics", Map.of("name", "Beşiktaş Şubesi"))
                .andExpect(status().isCreated())).get("id").asText());
        Session manager = staff(admin, "branch_manager", admin.clinicId());
        Session reception = staff(admin, "receptionist", admin.clinicId());

        postJson(manager.token(), manager.clinic(), "/api/v1/staff-invitations",
                invite(uniqueEmail("yonetici"), role(admin, "clinic_admin"), admin.clinicId())).andExpect(status().isForbidden());
        postJson(manager.token(), manager.clinic(), "/api/v1/staff-invitations",
                invite(uniqueEmail("baska-sube"), role(admin, "veterinarian"), second)).andExpect(status().isForbidden());
        postJson(manager.token(), manager.clinic(), "/api/v1/staff-invitations",
                invite(uniqueEmail("kendi-sube"), role(admin, "veterinarian"), admin.clinicId())).andExpect(status().isCreated());
        // Klinik yöneticisi yönetici daveti oluşturabilir.
        postJson(admin.token(), admin.clinic(), "/api/v1/staff-invitations",
                invite(uniqueEmail("ikinci-yonetici"), role(admin, "clinic_admin"), admin.clinicId(), second)).andExpect(status().isCreated());

        postJson(reception.token(), reception.clinic(), "/api/v1/staff-invitations",
                invite(uniqueEmail("res"), role(admin, "veterinarian"), admin.clinicId())).andExpect(status().isForbidden());
        getJson(reception.token(), "/api/v1/staff-invitations").andExpect(status().isForbidden());

        JsonNode own = body(getJson(admin.token(), "/api/v1/staff-invitations").andExpect(status().isOk()));
        String inviteId = own.get(0).get("id").asText();
        Session other = signup(null);
        postJson(other.token(), other.clinic(), "/api/v1/staff-invitations/" + inviteId + "/revoke", Map.of())
                .andExpect(status().isNotFound());
        assertThat(body(getJson(other.token(), "/api/v1/staff-invitations"))).isEmpty();
        // Başka organizasyonun şubesine/rolüne davet oluşturulamaz.
        postJson(other.token(), other.clinic(), "/api/v1/staff-invitations",
                invite(uniqueEmail("yabanci"), role(admin, "veterinarian"), other.clinicId())).andExpect(status().isUnprocessableEntity());
        postJson(other.token(), other.clinic(), "/api/v1/staff-invitations",
                invite(uniqueEmail("yabanci2"), role(other, "veterinarian"), admin.clinicId())).andExpect(status().isUnprocessableEntity());
    }

    @Test
    void userLimit_appliesToInvitationAcceptanceAndActivation() throws Exception {
        Session admin = signup(OrgTestData.createPlan(superUserDataSource(), 1, 3));
        Session first = staff(admin, "receptionist", admin.clinicId());
        // İki davet limit dolmadan oluşturulur; ilki kabul edilince limit (3) dolar, ikincisi reddedilir.
        String a = OrgTestData.insertInvitation(superUserDataSource(), admin.organizationId(), uniqueEmail("a"), "veterinarian",
                admin.userId(), List.of(admin.clinicId()));
        String b = OrgTestData.insertInvitation(superUserDataSource(), admin.organizationId(), uniqueEmail("b"), "veterinarian",
                admin.userId(), List.of(admin.clinicId()));
        acceptRaw(a).andExpect(status().isCreated());
        acceptRaw(b).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("PLAN_USER_LIMIT"))
                .andExpect(jsonPath("$.details.limit").value(3));
        postJson(admin.token(), admin.clinic(), "/api/v1/staff-invitations",
                invite(uniqueEmail("c"), role(admin, "veterinarian"), admin.clinicId()))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("PLAN_USER_LIMIT"));

        // Pasifleştirme yer açar; yer dolunca pasif personel yeniden aktifleştirilemez.
        postJson(admin.token(), "/api/v1/users/" + first.userId() + "/deactivate", Map.of()).andExpect(status().isOk());
        acceptRaw(b).andExpect(status().isCreated());
        postJson(admin.token(), "/api/v1/users/" + first.userId() + "/activate", Map.of())
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("PLAN_USER_LIMIT"));
    }

    /** Organizasyon satırı kilidi sayesinde aynı anda gelen iki kabul, son kullanıcı yerini birlikte dolduramaz. */
    @Test
    void concurrentAcceptances_cannotExceedUserLimit() throws Exception {
        Session admin = signup(OrgTestData.createPlan(superUserDataSource(), 1, 2));
        List<String> tokens = new ArrayList<>();
        for (int i = 0; i < 2; i++) {
            tokens.add(OrgTestData.insertInvitation(superUserDataSource(), admin.organizationId(), uniqueEmail("es" + i),
                    "receptionist", admin.userId(), List.of(admin.clinicId())));
        }

        DataSource pool = pooledVetAppDataSource(2);
        CyclicBarrier start = new CyclicBarrier(2);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            List<Future<String>> results = new ArrayList<>();
            for (String token : tokens) {
                results.add(executor.submit(() -> {
                    try (Connection c = pool.getConnection()) {
                        start.await(5, TimeUnit.SECONDS);
                        try (PreparedStatement ps = c.prepareStatement("SELECT result FROM vet.accept_staff_invitation(?, 'Eş Zamanlı', 'x')")) {
                            ps.setString(1, new InvitationToken(token).hash());
                            try (ResultSet rs = ps.executeQuery()) {
                                rs.next();
                                String result = rs.getString(1);
                                Thread.sleep(200); // kilit tutulurken diğer işlem beklemeli
                                c.commit();
                                return result;
                            }
                        }
                    }
                }));
            }
            List<String> outcomes = new ArrayList<>();
            for (Future<String> f : results) {
                outcomes.add(f.get(20, TimeUnit.SECONDS));
            }
            assertThat(outcomes).containsExactlyInAnyOrder("OK", "USER_LIMIT");
        } finally {
            executor.shutdownNow();
            ((AutoCloseable) pool).close();
        }
        assertThat(OrgTestData.count(superUserDataSource(), "SELECT count(*) FROM vet.app_users WHERE organization_id = ? AND is_active",
                admin.organizationId())).isEqualTo(2);
    }
}
