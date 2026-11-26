package org.codeflow.veterineryproject.organization.service;

import org.codeflow.veterineryproject.organization.model.Organization;
import org.codeflow.veterineryproject.organization.repository.OrganizationRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class OrganizationService {

    private final OrganizationRepository organizationRepository;

    public OrganizationService(OrganizationRepository organizationRepository) {
        this.organizationRepository = organizationRepository;
    }

    public List<Organization> getAll() {
        return organizationRepository.findAll();
    }

    public Organization getById(UUID id) {
        return organizationRepository.findById(id).orElse(null);
    }

    public Organization save(Organization organization) {
        return organizationRepository.save(organization);
    }
}
