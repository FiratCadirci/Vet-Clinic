package org.codeflow.veterineryproject.auth.repository;

import org.codeflow.veterineryproject.auth.model.RefreshToken;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {

    Optional<RefreshToken> findByOrganizationIdAndTokenHash(UUID organizationId, String tokenHash);

    List<RefreshToken> findByOrganizationIdAndUserIdAndRevokedAtIsNull(UUID organizationId, UUID userId);

    List<RefreshToken> findByOrganizationIdAndTokenFamilyIdAndRevokedAtIsNull(UUID organizationId, UUID tokenFamilyId);
}

