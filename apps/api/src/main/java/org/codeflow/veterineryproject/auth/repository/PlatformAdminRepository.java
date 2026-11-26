package org.codeflow.veterineryproject.auth.repository;

import org.codeflow.veterineryproject.auth.model.PlatformAdmin;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface PlatformAdminRepository extends JpaRepository<PlatformAdmin, UUID> {

    Optional<PlatformAdmin> findByEmailIgnoreCase(String email);
}

