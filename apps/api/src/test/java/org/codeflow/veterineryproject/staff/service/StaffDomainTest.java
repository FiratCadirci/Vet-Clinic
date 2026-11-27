package org.codeflow.veterineryproject.staff.service;

import org.codeflow.veterineryproject.common.api.ConflictException;
import org.codeflow.veterineryproject.common.text.CodeSlug;
import org.codeflow.veterineryproject.staff.dto.WorkingHoursDto.Interval;
import org.codeflow.veterineryproject.subscription.service.PlanLimitDecision;
import org.codeflow.veterineryproject.subscription.service.PlanResource;
import org.junit.jupiter.api.Test;

import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Davet token'ı (fabrika + değer nesnesi), davet durumu, haftalık program değer nesnesi, kod üretimi ve limit kararı. */
class StaffDomainTest {

    @Test
    void invitationToken_isRandomUrlSafe_andHashIsDeterministicSha256() {
        InvitationToken a = InvitationToken.generate();
        InvitationToken b = InvitationToken.generate();
        assertThat(a.raw()).isNotEqualTo(b.raw()).matches("[A-Za-z0-9_-]{43}");
        assertThat(a.hash()).hasSize(64).isEqualTo(new InvitationToken(" " + a.raw() + " ").hash()).isNotEqualTo(b.hash());
        assertThat(a.toString()).doesNotContain(a.raw());
        assertThatThrownBy(() -> new InvitationToken(" ")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void invitationStatus_isDerivedFromTimestamps() {
        OffsetDateTime now = OffsetDateTime.now();
        assertThat(InvitationStatus.of(null, null, now.plusDays(1), now)).isEqualTo(InvitationStatus.PENDING);
        assertThat(InvitationStatus.of(null, null, now.minusSeconds(1), now)).isEqualTo(InvitationStatus.EXPIRED);
        assertThat(InvitationStatus.of(now, null, now.plusDays(1), now)).isEqualTo(InvitationStatus.ACCEPTED);
        assertThat(InvitationStatus.of(null, now, now.plusDays(1), now)).isEqualTo(InvitationStatus.REVOKED);
        assertThatCode(InvitationStatus.PENDING::requirePending).doesNotThrowAnyException();
        assertThatThrownBy(InvitationStatus.EXPIRED::requirePending).isInstanceOfSatisfying(ConflictException.class,
                ex -> assertThat(ex.code()).isEqualTo("INVITATION_EXPIRED"));
    }

    @Test
    void weeklySchedule_sortsSlotsAndRejectsInvalidOrOverlappingIntervals() {
        WeeklySchedule schedule = WeeklySchedule.of(List.of(new Interval(1, "13:00", "17:00"), new Interval(0, "10:00", "12:00"),
                new Interval(1, "09:00", "13:00")));
        assertThat(schedule.slots()).extracting(WeeklySchedule.Slot::dayOfWeek).containsExactly(0, 1, 1);
        assertThat(schedule.slots().get(1).start()).isEqualTo(LocalTime.of(9, 0));

        assertThatThrownBy(() -> WeeklySchedule.of(List.of(new Interval(1, "12:00", "12:00")))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> WeeklySchedule.of(List.of(new Interval(2, "09:00", "17:00"), new Interval(2, "10:00", "11:00"))))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatCode(() -> WeeklySchedule.of(List.of(new Interval(2, "09:00", "12:00"), new Interval(3, "10:00", "11:00"))))
                .doesNotThrowAnyException();
    }

    @Test
    void codeSlug_foldsTurkishCharacters() {
        assertThat(CodeSlug.of("Kadıköy Şubesi", "SUBE")).isEqualTo("KADIKOY-SUBESI");
        assertThat(CodeSlug.of("Genel muayene (ilk)", "HIZMET")).isEqualTo("GENEL-MUAYENE-ILK");
        assertThat(CodeSlug.of("  ", "SUBE")).isEqualTo("SUBE");
        assertThat(CodeSlug.withRandomSuffix("Üsküdar", "SUBE")).matches("USKUDAR-[0-9A-F]{4}");
    }

    @Test
    void planLimitDecision_mapsToConflictWithMachineReadableCode() {
        assertThatCode(() -> new PlanLimitDecision(true, 3, 2, "P", null).orThrow(PlanResource.USERS)).doesNotThrowAnyException();
        assertThatThrownBy(() -> new PlanLimitDecision(false, 3, 3, "P", "LIMIT_REACHED").orThrow(PlanResource.USERS))
                .isInstanceOfSatisfying(ConflictException.class, ex -> {
                    assertThat(ex.code()).isEqualTo("PLAN_USER_LIMIT");
                    assertThat(ex.getMessage()).contains("3");
                });
        assertThatThrownBy(() -> new PlanLimitDecision(false, 1, 1, "P", "LIMIT_REACHED").orThrow(PlanResource.BRANCHES))
                .isInstanceOfSatisfying(ConflictException.class, ex -> assertThat(ex.code()).isEqualTo("PLAN_BRANCH_LIMIT"));
        assertThatThrownBy(() -> new PlanLimitDecision(false, null, null, null, "SUBSCRIPTION_INACTIVE").orThrow(PlanResource.BRANCHES))
                .isInstanceOfSatisfying(ConflictException.class, ex -> assertThat(ex.code()).isEqualTo("SUBSCRIPTION_INACTIVE"));
    }
}
