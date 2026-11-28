package org.codeflow.veterineryproject.platform.service;

import jakarta.persistence.EntityNotFoundException;
import org.codeflow.veterineryproject.common.tenant.TenantContext;
import org.codeflow.veterineryproject.platform.dto.PlatformDto.CommissionRuleResponse;
import org.codeflow.veterineryproject.platform.dto.PlatformDto.CreateCommissionRuleRequest;
import org.codeflow.veterineryproject.platform.dto.PlatformDto.UpdateCommissionRuleRequest;
import org.codeflow.veterineryproject.platform.repository.PlatformCommissionRuleRepository;
import org.codeflow.veterineryproject.platform.repository.PlatformCommissionRuleRepository.NewRule;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * FG-K5 komisyon kuralı yönetimi (ürün tipi + oran + geçerlilik). Kuralı seçen ve uygulayan fatura kesim trigger'ıdır:
 * aynı tipte birden çok geçerli kural varsa en son başlayan uygulanır. Komisyon hesabına burada dokunulmaz.
 */
@Service
public class PlatformCommissionRuleService {

    private static final Map<String, String> DEFAULT_NAMES = Map.of(
            "FOOD", "Mama komisyonu",
            "MEDICATION", "İlaç komisyonu");

    private final PlatformCommissionRuleRepository repository;

    public PlatformCommissionRuleService(PlatformCommissionRuleRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public List<CommissionRuleResponse> list() {
        return repository.findAll();
    }

    @Transactional
    public CommissionRuleResponse create(CreateCommissionRuleRequest request) {
        String code = request.productType() + "-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT);
        String name = blank(request.name()) ? DEFAULT_NAMES.get(request.productType()) : request.name().trim();
        OffsetDateTime validFrom = request.validFrom() == null ? OffsetDateTime.now() : request.validFrom();
        UUID adminId = TenantContext.get().map(TenantContext.ContextValues::platformAdminId).orElse(null);
        UUID id = repository.insert(new NewRule(code, name, request.productType(), request.ratePercent(), validFrom,
                request.validTo(), !Boolean.FALSE.equals(request.active()), adminId));
        return get(id);
    }

    @Transactional
    public CommissionRuleResponse update(UUID id, UpdateCommissionRuleRequest request) {
        CommissionRuleResponse existing = get(id);
        String name = blank(request.name()) ? existing.name() : request.name().trim();
        repository.update(id, name, request.ratePercent(), request.validFrom(), request.validTo(), request.active());
        return get(id);
    }

    private CommissionRuleResponse get(UUID id) {
        return repository.findById(id).orElseThrow(() -> new EntityNotFoundException("Komisyon kuralı bulunamadı."));
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
