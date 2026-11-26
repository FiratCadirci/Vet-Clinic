package org.codeflow.veterineryproject.user.service;

import jakarta.persistence.EntityNotFoundException;
import org.codeflow.veterineryproject.auth.repository.AuthReadRepository;
import org.codeflow.veterineryproject.auth.service.RefreshTokenService;
import org.codeflow.veterineryproject.common.api.BusinessRuleException;
import org.codeflow.veterineryproject.common.api.ConflictException;
import org.codeflow.veterineryproject.common.pagination.PageResponse;
import org.codeflow.veterineryproject.common.tenant.TenantContext;
import org.codeflow.veterineryproject.user.dto.ClinicRoleAssignmentRequest;
import org.codeflow.veterineryproject.user.dto.ClinicRoleResponse;
import org.codeflow.veterineryproject.user.dto.CreateUserRequest;
import org.codeflow.veterineryproject.user.dto.UpdateUserRequest;
import org.codeflow.veterineryproject.user.dto.UserResponse;
import org.codeflow.veterineryproject.user.repository.UserAdminRepository;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
public class UserService {

    private static final String USER_NOT_FOUND = "Kullanıcı bulunamadı.";

    private final UserAdminRepository repository;
    private final AuthReadRepository authReadRepository;
    private final PasswordEncoder passwordEncoder;
    private final RefreshTokenService refreshTokenService;

    public UserService(
            UserAdminRepository repository,
            AuthReadRepository authReadRepository,
            PasswordEncoder passwordEncoder,
            RefreshTokenService refreshTokenService
    ) {
        this.repository = repository;
        this.authReadRepository = authReadRepository;
        this.passwordEncoder = passwordEncoder;
        this.refreshTokenService = refreshTokenService;
    }

    /** Kullanıcı ve en az bir rol ataması tek transaction'da oluşturulur; rolsüz kullanıcı kimseye görünmez. */
    @Transactional
    public UserResponse create(CreateUserRequest request) {
        TenantContext.ContextValues caller = TenantContext.require();
        String email = request.email().trim().toLowerCase(Locale.ROOT);

        if (authReadRepository.resolveLoginUserId(caller.organizationId(), email) != null) {
            throw new ConflictException("Bu e-posta adresi ile kayıtlı bir kullanıcı zaten var.");
        }
        request.assignments().forEach(this::validateAssignment);

        UUID userId = UUID.randomUUID();
        try {
            repository.insertUser(userId, caller.organizationId(), email,
                    passwordEncoder.encode(request.initialPassword()), request.displayName().trim(), blankToNull(request.phone()));
        } catch (DuplicateKeyException ex) {
            throw new ConflictException("Bu e-posta adresi ile kayıtlı bir kullanıcı zaten var.");
        }
        for (ClinicRoleAssignmentRequest assignment : request.assignments()) {
            insertAssignment(caller, userId, assignment);
        }
        return load(userId);
    }

    @Transactional(readOnly = true)
    public PageResponse<UserResponse> list(String query, Boolean active, Pageable pageable) {
        List<UserResponse> users = repository.search(query, active, pageable.getPageSize(), pageable.getOffset());
        long total = repository.count(query, active);
        Map<UUID, List<ClinicRoleResponse>> assignments = repository.findAssignments(users.stream().map(UserResponse::id).toList());
        List<UserResponse> content = users.stream()
                .map(u -> u.withClinicRoles(assignments.getOrDefault(u.id(), List.of())))
                .toList();
        return PageResponse.from(new PageImpl<>(content, pageable, total));
    }

    @Transactional(readOnly = true)
    public UserResponse get(UUID id) {
        return load(id);
    }

    @Transactional
    public UserResponse update(UUID id, UpdateUserRequest request) {
        TenantContext.require();
        if (request.displayName() != null && request.displayName().isBlank()) {
            throw new IllegalArgumentException("Görünen ad boş olamaz.");
        }
        requireExisting(id);
        repository.updateProfile(id,
                request.displayName() == null ? null : request.displayName().trim(),
                blankToNull(request.phone()),
                request.phone() != null);
        return load(id);
    }

    @Transactional
    public UserResponse addClinicRole(UUID id, ClinicRoleAssignmentRequest request) {
        TenantContext.ContextValues caller = TenantContext.require();
        requireExisting(id);
        validateAssignment(request);
        insertAssignment(caller, id, request);
        return load(id);
    }

    /** Silme yerine valid_to = bugün yazılır; atama bugün sonuna kadar geçerli kalır, geçmiş korunur. */
    @Transactional
    public UserResponse endClinicRole(UUID id, UUID assignmentId) {
        TenantContext.require();
        requireExisting(id);
        if (!repository.assignmentExists(id, assignmentId)) {
            throw new EntityNotFoundException("Rol ataması bulunamadı.");
        }
        if (repository.countActiveAssignments(id) <= 1) {
            throw new BusinessRuleException("Kullanıcının en az bir aktif rol ataması olmalı; kullanıcıyı pasife alın.");
        }
        repository.closeAssignment(id, assignmentId);
        return load(id);
    }

    @Transactional
    public UserResponse deactivate(UUID id) {
        TenantContext.ContextValues caller = TenantContext.require();
        requireExisting(id);
        if (caller.userId().equals(id)) {
            throw new BusinessRuleException("Kullanıcı kendisini pasife alamaz.");
        }
        if (repository.countGroupsLeftWithoutManager(id) > 0) {
            throw new BusinessRuleException("Klinik grubunun son aktif yöneticisi pasife alınamaz.");
        }
        repository.setActive(id, false);
        repository.closeOpenAssignments(id);
        refreshTokenService.revokeAllForUser(caller.organizationId(), id);
        return load(id);
    }

    @Transactional
    public UserResponse activate(UUID id) {
        TenantContext.require();
        requireExisting(id);
        repository.setActive(id, true);
        return load(id);
    }

    private void validateAssignment(ClinicRoleAssignmentRequest assignment) {
        if (assignment.validFrom() != null && assignment.validTo() != null && assignment.validTo().isBefore(assignment.validFrom())) {
            throw new IllegalArgumentException("Geçerlilik bitişi başlangıçtan önce olamaz.");
        }
        if (assignment.validTo() != null && assignment.validTo().isBefore(LocalDate.now())) {
            throw new IllegalArgumentException("Geçmişte biten bir rol ataması oluşturulamaz.");
        }
        if (!repository.clinicVisible(assignment.clinicId())) {
            throw new BusinessRuleException("Şube, çağıranın klinik grubunda bulunmalıdır.");
        }
        if (!repository.roleVisible(assignment.roleId())) {
            throw new BusinessRuleException("Rol bulunamadı.");
        }
    }

    private void insertAssignment(TenantContext.ContextValues caller, UUID userId, ClinicRoleAssignmentRequest assignment) {
        try {
            repository.insertAssignment(caller.organizationId(), userId, assignment.clinicId(), assignment.roleId(),
                    assignment.validFrom(), assignment.validTo(), caller.userId());
        } catch (DuplicateKeyException ex) {
            throw new ConflictException("Bu rol ataması zaten mevcut.");
        }
    }

    private void requireExisting(UUID id) {
        if (repository.findById(id).isEmpty()) {
            throw new EntityNotFoundException(USER_NOT_FOUND);
        }
    }

    private UserResponse load(UUID id) {
        UserResponse user = repository.findById(id).orElseThrow(() -> new EntityNotFoundException(USER_NOT_FOUND));
        return user.withClinicRoles(repository.findAssignments(List.of(id)).getOrDefault(id, List.of()));
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
