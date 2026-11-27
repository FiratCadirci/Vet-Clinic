package org.codeflow.veterineryproject.commission.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public final class CommissionDto {

    private CommissionDto() {
    }

    /** Klinik tarafı komisyon kaydı: hangi fatura satırından, hangi oranla, ne kadar (KDV hariç matrah). */
    public record CommissionRecordResponse(UUID id, UUID clinicId, UUID invoiceId, String invoiceNo, UUID invoiceLineId,
                                           String description, String productType, BigDecimal quantity,
                                           BigDecimal eligibleAmount, BigDecimal ratePercent,
                                           BigDecimal commissionAmount, String status, OffsetDateTime issuedAt,
                                           OffsetDateTime calculatedAt) {
    }
}
