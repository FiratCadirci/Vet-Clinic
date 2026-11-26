package org.codeflow.veterineryproject.patient.service;

import jakarta.persistence.EntityNotFoundException;
import org.codeflow.veterineryproject.common.pagination.PageResponse;
import org.codeflow.veterineryproject.patient.dto.PatientSummaryResponse;
import org.codeflow.veterineryproject.patient.repository.PatientJdbcRepository;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/** Hasta listesi ve hasta kartı başlığı (FG-B2, FOG-8: sayfalı, en fazla 200 kayıt). */
@Service
public class PatientQueryService {

    private final PatientJdbcRepository repository;

    public PatientQueryService(PatientJdbcRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public PageResponse<PatientSummaryResponse> list(String query, UUID ownerId, Pageable pageable) {
        String q = query == null || query.isBlank() ? null : query.trim();
        return PageResponse.from(new PageImpl<>(repository.search(q, ownerId, pageable.getPageSize(), pageable.getOffset()),
                pageable, repository.count(q, ownerId)));
    }

    @Transactional(readOnly = true)
    public PatientSummaryResponse get(UUID id) {
        return repository.findById(id).orElseThrow(() -> new EntityNotFoundException("Hasta bulunamadı."));
    }
}
