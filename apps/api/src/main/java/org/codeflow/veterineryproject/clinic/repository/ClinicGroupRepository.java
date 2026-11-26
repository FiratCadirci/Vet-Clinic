package org.codeflow.veterineryproject.clinic.repository;

import org.codeflow.veterineryproject.clinic.model.ClinicGroup;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface ClinicGroupRepository extends JpaRepository<ClinicGroup, UUID> {
}
