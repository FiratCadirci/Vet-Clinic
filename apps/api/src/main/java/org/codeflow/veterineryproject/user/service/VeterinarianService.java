package org.codeflow.veterineryproject.user.service;

import jakarta.persistence.EntityNotFoundException;
import org.codeflow.veterineryproject.common.api.BusinessRuleException;
import org.codeflow.veterineryproject.common.api.ConflictException;
import org.codeflow.veterineryproject.common.tenant.TenantContext;
import org.codeflow.veterineryproject.user.dto.CreateVeterinarianRequest;
import org.codeflow.veterineryproject.user.dto.UserResponse;
import org.codeflow.veterineryproject.user.dto.VeterinarianResponse;
import org.codeflow.veterineryproject.user.repository.UserAdminRepository;
import org.codeflow.veterineryproject.user.repository.VeterinarianRepository;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class VeterinarianService {

    private final VeterinarianRepository repository;
    private final UserAdminRepository userRepository;

    public VeterinarianService(VeterinarianRepository repository, UserAdminRepository userRepository) {
        this.repository = repository;
        this.userRepository = userRepository;
    }

    @Transactional
    public VeterinarianResponse create(UUID userId, CreateVeterinarianRequest request) {
        UUID organizationId = TenantContext.require().organizationId();
        UserResponse user = userRepository.findById(userId).orElseThrow(() -> new EntityNotFoundException("Kullanıcı bulunamadı."));
        if (repository.findByUser(userId).isPresent()) {
            throw new ConflictException("Kullanıcının zaten bir hekim profili var.");
        }
        try {
            UUID staffId = repository.findOrCreateStaffMember(organizationId, userId, user.displayName(), user.email(), user.phone());
            repository.insert(organizationId, staffId, request.licenseNo().trim(), request.licenseAuthority(),
                    request.specialty(), request.graduationYear());
        } catch (DuplicateKeyException ex) {
            throw new ConflictException("Bu lisans numarası zaten kayıtlı.");
        }
        return repository.findByUser(userId).orElseThrow();
    }

    @Transactional(readOnly = true)
    public VeterinarianResponse get(UUID userId) {
        userRepository.findById(userId).orElseThrow(() -> new EntityNotFoundException("Kullanıcı bulunamadı."));
        return repository.findByUser(userId).orElseThrow(() -> new EntityNotFoundException("Hekim profili bulunamadı."));
    }

    /** Muayene/aşı yazan kullanıcının aktif hekim profili; yoksa iş kuralı hatası (422). */
    @Transactional(readOnly = true)
    public UUID requireCurrentVeterinarianId() {
        UUID userId = TenantContext.require().userId();
        return repository.findActiveIdByUser(userId)
                .orElseThrow(() -> new BusinessRuleException("Bu işlem için aktif bir veteriner hekim profili gerekir."));
    }
}
