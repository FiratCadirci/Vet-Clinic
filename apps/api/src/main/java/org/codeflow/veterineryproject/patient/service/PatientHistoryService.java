package org.codeflow.veterineryproject.patient.service;

import jakarta.persistence.EntityNotFoundException;
import org.codeflow.veterineryproject.common.pagination.PageResponse;
import org.codeflow.veterineryproject.patient.dto.PatientHistoryItem;
import org.codeflow.veterineryproject.patient.dto.PatientHistoryResponse;
import org.codeflow.veterineryproject.patient.dto.PatientHistoryResponse.HiddenRecordCounts;
import org.codeflow.veterineryproject.patient.repository.PatientJdbcRepository;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Kronolojik hasta geçmişi: muayene, aşı ve randevular tek listede (FG-C5, FG-J1, Senaryo 5).
 * Görünürlük iki katmanlıdır: (1) RLS — aynı klinik grubundaki şubeler veri paylaşır, başka grubun kayıtları görünmez;
 * (2) yetki — {@code clinical.read} olmayan rol (ör. resepsiyon) muayene/aşı satırlarını görmez.
 * Görünmeyenler listeye girmez; yalnızca türe göre sayısı ("gizlenen kayıt") bildirilir.
 */
@Service
public class PatientHistoryService {

    private final PatientJdbcRepository repository;

    public PatientHistoryService(PatientJdbcRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public PatientHistoryResponse history(UUID animalId, boolean clinicalAccess, Pageable pageable) {
        if (!repository.animalVisible(animalId)) {
            throw new EntityNotFoundException("Hasta bulunamadı.");
        }
        HiddenRecordCounts visible = repository.visibleCounts(animalId, clinicalAccess);
        List<PatientHistoryItem> rows = repository.history(animalId, clinicalAccess, pageable.getPageSize(),
                pageable.getOffset());
        long total = visible.encounters() + visible.vaccinations() + visible.appointments();
        PageResponse<PatientHistoryItem> page = PageResponse.from(new PageImpl<>(rows, pageable, total));
        return PatientHistoryResponse.of(page, hidden(repository.totalHistoryCounts(animalId), visible), clinicalAccess);
    }

    /** Gizli = toplam − görünen (tür bazında, negatif olamaz). */
    static HiddenRecordCounts hidden(HiddenRecordCounts total, HiddenRecordCounts visible) {
        return new HiddenRecordCounts(
                Math.max(0, total.encounters() - visible.encounters()),
                Math.max(0, total.vaccinations() - visible.vaccinations()),
                Math.max(0, total.appointments() - visible.appointments()));
    }
}
