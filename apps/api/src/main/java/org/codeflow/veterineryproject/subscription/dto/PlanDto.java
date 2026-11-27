package org.codeflow.veterineryproject.subscription.dto;

import java.math.BigDecimal;
import java.util.UUID;

/** Paket kataloğu ve kullanım özetleri. */
public final class PlanDto {

    private PlanDto() {
    }

    public record PlanOption(UUID id, String code, String name, String billingPeriod, BigDecimal price, String currency,
                             int maxBranches, int maxUsers) {
    }

    public record PlanUsage(int activeBranches, int activeUsers) {
    }
}
