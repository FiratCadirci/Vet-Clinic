package org.codeflow.veterineryproject.patient.dto;

import org.codeflow.veterineryproject.common.pagination.PageResponse;

import java.util.List;

/**
 * Kronolojik hasta geçmişi (yeniden eskiye, sayfalı) + kullanıcının göremediği kayıtların sayısı (FG-C5, FG-J1).
 * Gizli kayıtların içeriği, kimliği ve şubesi dönmez; yalnızca türe göre sayısı döner.
 * {@code clinicalAccess = false}: kullanıcının {@code clinical.read} yetkisi yok, muayene ve aşı satırları bu yüzden gizli.
 */
public record PatientHistoryResponse(
        List<PatientHistoryItem> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean first,
        boolean last,
        long hiddenCount,
        HiddenRecordCounts hiddenByType,
        boolean clinicalAccess
) {
    public record HiddenRecordCounts(long encounters, long vaccinations, long appointments) {
        public long total() {
            return encounters + vaccinations + appointments;
        }
    }

    public static PatientHistoryResponse of(PageResponse<PatientHistoryItem> page, HiddenRecordCounts hidden,
                                            boolean clinicalAccess) {
        return new PatientHistoryResponse(page.content(), page.page(), page.size(), page.totalElements(),
                page.totalPages(), page.first(), page.last(), hidden.total(), hidden, clinicalAccess);
    }
}
