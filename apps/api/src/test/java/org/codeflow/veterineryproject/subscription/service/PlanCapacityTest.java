package org.codeflow.veterineryproject.subscription.service;

import org.codeflow.veterineryproject.common.api.ConflictException;
import org.codeflow.veterineryproject.subscription.dto.PlanDto.PlanUsage;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** FG-K2 paket düşürme kuralı (Specification), DB'siz. */
class PlanCapacityTest {

    private final PlanCapacity starter = new PlanCapacity("Başlangıç", 1, 5);

    @Test
    void usageWithinOrEqualToLimits_isAccepted() {
        assertThat(starter.accepts(new PlanUsage(1, 5))).isTrue();
        assertThatCode(() -> starter.assertAccepts(new PlanUsage(0, 1))).doesNotThrowAnyException();
    }

    @Test
    void branchesOrUsersAboveLimit_isRejectedWithCodeAndDetails() {
        assertThat(starter.accepts(new PlanUsage(2, 1))).isFalse();
        assertThat(starter.accepts(new PlanUsage(1, 6))).isFalse();

        assertThatThrownBy(() -> starter.assertAccepts(new PlanUsage(2, 6)))
                .isInstanceOfSatisfying(ConflictException.class, ex -> {
                    assertThat(ex.code()).isEqualTo(PlanCapacity.DOWNGRADE_BLOCKED);
                    assertThat(ex.getMessage()).contains("Başlangıç", "2 şube", "6 kullanıcı");
                    assertThat(ex.details()).containsEntry("activeBranches", 2).containsEntry("maxBranches", 1)
                            .containsEntry("activeUsers", 6).containsEntry("maxUsers", 5);
                });
    }
}
