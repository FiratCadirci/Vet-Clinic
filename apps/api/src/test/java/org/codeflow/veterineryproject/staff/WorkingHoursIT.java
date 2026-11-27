package org.codeflow.veterineryproject.staff;

import com.fasterxml.jackson.databind.JsonNode;
import org.codeflow.veterineryproject.common.test.OrgApiTestSupport;
import org.codeflow.veterineryproject.common.test.OrgTestData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** FG-E1: hekim ve şube bazında haftalık çalışma saatleri; hekim aynı saatte iki şubede çalışamaz. */
@SpringBootTest
@AutoConfigureMockMvc
class WorkingHoursIT extends OrgApiTestSupport {

    private Session admin;
    private UUID first;
    private UUID second;
    private Session vet;
    private UUID vetId;

    private static Map<String, Object> hours(UUID vetId, Object... intervals) {
        return Map.of("veterinarianId", vetId, "hours", List.of(intervals));
    }

    private static Map<String, Object> slot(int day, String start, String end) {
        return Map.of("dayOfWeek", day, "startTime", start, "endTime", end);
    }

    @BeforeEach
    void setUp() throws Exception {
        admin = signup(OrgTestData.createPlan(superUserDataSource(), 3, 10));
        first = admin.clinicId();
        second = UUID.fromString(body(postJson(admin.token(), first.toString(), "/api/v1/clinics", Map.of("name", "Beşiktaş Şubesi"))
                .andExpect(status().isCreated())).get("id").asText());
        // Yeni şube token'a yeniden girişte eklenir (clinicIds sırası değişebilir; "first" sabit tutulur).
        admin = relogin(admin);
        vet = staff(admin, "veterinarian", first, second);
        vetId = makeVeterinarian(admin, vet.userId());
    }

    @Test
    void sameVeterinarianCannotWorkAtTwoBranchesAtTheSameTime() throws Exception {
        
        JsonNode saved = body(putJson(admin.token(), first.toString(), "/api/v1/working-hours",
                hours(vetId, slot(1, "09:00", "12:00"), slot(1, "13:00", "17:00"))).andExpect(status().isOk()));
        assertThat(saved).hasSize(2);
        assertThat(saved.get(0).get("startTime").asText()).isEqualTo("09:00");
        assertThat(saved.get(0).get("clinicId").asText()).isEqualTo(first.toString());

        putJson(admin.token(), second.toString(), "/api/v1/working-hours", hours(vetId, slot(1, "11:00", "14:00")))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("WORKING_HOURS_CONFLICT"));
        // Öğle arası ve mesai sonrası diğer şubede çalışabilir; farklı gün serbesttir.
        putJson(admin.token(), second.toString(), "/api/v1/working-hours",
                hours(vetId, slot(1, "12:00", "13:00"), slot(1, "17:00", "20:00"), slot(2, "09:00", "17:00"))).andExpect(status().isOk());

        // Programı değiştirmek eski aralıkları siler (tümden değiştirme).
        putJson(admin.token(), first.toString(), "/api/v1/working-hours", hours(vetId, slot(3, "09:00", "18:00"))).andExpect(status().isOk());
        JsonNode list = body(getJson(admin.token(), "/api/v1/working-hours?veterinarianId=" + vetId).andExpect(status().isOk()));
        assertThat(list).hasSize(4);
        assertThat(body(getJson(admin.token(), "/api/v1/working-hours?clinicId=" + first))).hasSize(1);
    }

    @Test
    void databaseConstraintRejectsOverlapEvenWithoutTheApplication() throws Exception {
        putJson(admin.token(), first.toString(), "/api/v1/working-hours", hours(vetId, slot(4, "09:00", "17:00"))).andExpect(status().isOk());

        assertThatThrownBy(() -> {
            try (Connection c = superUserDataSource().getConnection();
                 PreparedStatement ps = c.prepareStatement("""
                         INSERT INTO vet.veterinarian_working_hours (organization_id, clinic_id, veterinarian_id, day_of_week, starts_at, ends_at)
                         VALUES (?, ?, ?, 4, '16:00', '18:00')
                         """)) {
                ps.setObject(1, admin.organizationId());
                ps.setObject(2, second);
                ps.setObject(3, vetId);
                ps.executeUpdate();
            }
        }).isInstanceOfSatisfying(SQLException.class, ex -> assertThat(ex.getSQLState()).isEqualTo("23P01"));
    }

    @Test
    void invalidIntervalsAre400_unassignedVetIs422_otherOrganizationVetIs404() throws Exception {
        
        putJson(admin.token(), first.toString(), "/api/v1/working-hours", hours(vetId, slot(1, "09:00", "12:00"), slot(1, "11:00", "13:00")))
                .andExpect(status().isBadRequest());
        putJson(admin.token(), first.toString(), "/api/v1/working-hours", hours(vetId, slot(1, "12:00", "09:00"))).andExpect(status().isBadRequest());
        putJson(admin.token(), first.toString(), "/api/v1/working-hours", hours(vetId, slot(7, "09:00", "12:00"))).andExpect(status().isBadRequest());
        putJson(admin.token(), first.toString(), "/api/v1/working-hours", hours(vetId, slot(1, "9:00", "12:00"))).andExpect(status().isBadRequest());
        putJson(admin.token(), null, "/api/v1/working-hours", hours(vetId, slot(1, "09:00", "12:00"))).andExpect(status().isBadRequest());

        Session onlyFirst = staff(admin, "veterinarian", first);
        UUID onlyFirstVet = makeVeterinarian(admin, onlyFirst.userId());
        putJson(admin.token(), second.toString(), "/api/v1/working-hours", hours(onlyFirstVet, slot(1, "09:00", "12:00")))
                .andExpect(status().isUnprocessableEntity());

        Session other = signup(null);
        putJson(other.token(), other.clinic(), "/api/v1/working-hours", hours(vetId, slot(1, "09:00", "12:00")))
                .andExpect(status().isNotFound());
        assertThat(body(getJson(other.token(), "/api/v1/working-hours?veterinarianId=" + vetId))).isEmpty();
    }

    @Test
    void receptionCanReadButNotChangeWorkingHours() throws Exception {
        Session reception = staff(admin, "receptionist", first);
        putJson(admin.token(), first.toString(), "/api/v1/working-hours", hours(vetId, slot(5, "09:00", "17:00"))).andExpect(status().isOk());

        putJson(reception.token(), reception.clinic(), "/api/v1/working-hours", hours(vetId, slot(5, "09:00", "18:00")))
                .andExpect(status().isForbidden());
        getJson(reception.token(), "/api/v1/working-hours?clinicId=" + first).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].veterinarianId").value(vetId.toString()))
                .andExpect(jsonPath("$[0].veterinarianName").value("Yeni Personel"));
        getJson(reception.token(), "/api/v1/working-hours/veterinarians?clinicId=" + second).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].veterinarianId").value(vetId.toString()));
    }
}
