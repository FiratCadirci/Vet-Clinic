package org.codeflow.veterineryproject.user.repository;

import org.codeflow.veterineryproject.user.model.AppUser;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface AppUserRepository extends JpaRepository<AppUser, UUID> {

    Optional<AppUser> findByOrganizationIdAndEmailIgnoreCase(UUID organizationId, String email);
}

