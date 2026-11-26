package org.codeflow.veterineryproject.auth.repository;

import org.codeflow.veterineryproject.auth.model.PlatformRefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PlatformRefreshTokenRepository extends JpaRepository<PlatformRefreshToken, UUID> {

    Optional<PlatformRefreshToken> findByTokenHash(String tokenHash);

    List<PlatformRefreshToken> findByTokenFamilyIdAndRevokedAtIsNull(UUID tokenFamilyId);
}
