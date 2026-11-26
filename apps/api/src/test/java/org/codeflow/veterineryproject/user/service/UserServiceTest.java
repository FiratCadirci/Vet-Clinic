package org.codeflow.veterineryproject.user.service;

import org.codeflow.veterineryproject.auth.repository.AuthReadRepository;
import org.codeflow.veterineryproject.auth.service.RefreshTokenService;
import org.codeflow.veterineryproject.common.api.BusinessRuleException;
import org.codeflow.veterineryproject.common.api.ConflictException;
import org.codeflow.veterineryproject.user.dto.ClinicRoleAssignmentRequest;
import org.codeflow.veterineryproject.user.dto.CreateUserRequest;
import org.codeflow.veterineryproject.user.dto.UserResponse;
import org.codeflow.veterineryproject.user.repository.UserAdminRepository;
import org.codeflow.veterineryproject.common.tenant.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UserServiceTest {

    private final UUID org = UUID.randomUUID();
    private final UUID caller = UUID.randomUUID();
    private final UserAdminRepository repository = mock(UserAdminRepository.class);
    private final AuthReadRepository authRead = mock(AuthReadRepository.class);
    private final PasswordEncoder encoder = mock(PasswordEncoder.class);
    private final RefreshTokenService refreshTokens = mock(RefreshTokenService.class);
    private final UserService service = new UserService(repository, authRead, encoder, refreshTokens);

    @BeforeEach
    void setUp() {
        TenantContext.set(org, caller, UUID.randomUUID());
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void create_existingEmailIsConflictAndNothingIsWritten() {
        when(authRead.resolveLoginUserId(org, "a@b.com")).thenReturn(UUID.randomUUID());
        CreateUserRequest request = new CreateUserRequest(" A@B.com ", "Ad", null, "Gecici-Sifre-1",
                List.of(new ClinicRoleAssignmentRequest(UUID.randomUUID(), UUID.randomUUID(), null, null)));

        assertThatThrownBy(() -> service.create(request)).isInstanceOf(ConflictException.class);
        verify(repository, never()).insertUser(any(), any(), any(), any(), any(), any());
    }

    @Test
    void create_invisibleClinicIsBusinessRuleViolation() {
        when(repository.clinicVisible(any())).thenReturn(false);
        CreateUserRequest request = new CreateUserRequest("x@y.com", "Ad", null, "Gecici-Sifre-1",
                List.of(new ClinicRoleAssignmentRequest(UUID.randomUUID(), UUID.randomUUID(), null, null)));

        assertThatThrownBy(() -> service.create(request)).isInstanceOf(BusinessRuleException.class);
        verify(repository, never()).insertUser(any(), any(), any(), any(), any(), any());
    }

    @Test
    void deactivate_selfIsRejected() {
        when(repository.findById(caller)).thenReturn(Optional.of(user(caller)));

        assertThatThrownBy(() -> service.deactivate(caller)).isInstanceOf(BusinessRuleException.class);
        verify(repository, never()).setActive(any(), anyBoolean());
    }

    @Test
    void deactivate_lastManagerOfGroupIsRejected() {
        UUID target = UUID.randomUUID();
        when(repository.findById(target)).thenReturn(Optional.of(user(target)));
        when(repository.countGroupsLeftWithoutManager(target)).thenReturn(1);

        assertThatThrownBy(() -> service.deactivate(target)).isInstanceOf(BusinessRuleException.class);
        verify(repository, never()).setActive(any(), anyBoolean());
        verify(refreshTokens, never()).revokeAllForUser(any(), any());
    }

    @Test
    void deactivate_closesAssignmentsAndRevokesRefreshTokens() {
        UUID target = UUID.randomUUID();
        when(repository.findById(target)).thenReturn(Optional.of(user(target)));
        when(repository.countGroupsLeftWithoutManager(target)).thenReturn(0);

        service.deactivate(target);

        verify(repository).setActive(target, false);
        verify(repository).closeOpenAssignments(target);
        verify(refreshTokens).revokeAllForUser(org, target);
    }

    private UserResponse user(UUID id) {
        return new UserResponse(id, "u@x.com", "U", null, true, false, null, null, List.of());
    }
}
