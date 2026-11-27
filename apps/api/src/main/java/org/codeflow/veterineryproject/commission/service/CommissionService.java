package org.codeflow.veterineryproject.commission.service;

import org.codeflow.veterineryproject.commission.dto.CommissionDto.CommissionRecordResponse;
import org.codeflow.veterineryproject.commission.policy.CommissionPolicy;
import org.codeflow.veterineryproject.commission.policy.CommissionPolicySelector;
import org.codeflow.veterineryproject.commission.repository.CommissionJdbcRepository;
import org.codeflow.veterineryproject.common.pagination.PageResponse;
import org.codeflow.veterineryproject.common.pagination.PaginationUtils;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Klinik tarafı komisyon: kesimde uygulanacak strateji ve kliniğin kendi komisyon kayıtları (FG-K4). */
@Service
public class CommissionService {

    private static final String ZONE = "Europe/Istanbul";

    private final CommissionJdbcRepository repository;
    private final CommissionPolicySelector selector;

    public CommissionService(CommissionJdbcRepository repository, CommissionPolicySelector selector) {
        this.repository = repository;
        this.selector = selector;
    }

    /** Çağıranın transaction'ında, oturumdaki organizasyonun abonelik durumuna göre strateji seçilir. */
    @Transactional(readOnly = true)
    public CommissionPolicy currentPolicy() {
        return selector.select(repository.currentSubscriptionStatus());
    }

    @Transactional(readOnly = true)
    public PageResponse<CommissionRecordResponse> list(UUID clinicId, UUID invoiceId, LocalDate from, LocalDate to,
                                                      int page, int size) {
        Pageable pageable = PaginationUtils.createPageable(page, size);
        long total = repository.count(clinicId, invoiceId, from, to, ZONE);
        List<CommissionRecordResponse> items = repository.search(clinicId, invoiceId, from, to, ZONE,
                pageable.getPageSize(), pageable.getOffset());
        return PageResponse.from(new PageImpl<>(items, pageable, total));
    }
}
