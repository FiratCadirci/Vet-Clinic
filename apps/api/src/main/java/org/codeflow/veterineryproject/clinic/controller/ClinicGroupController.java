package org.codeflow.veterineryproject.clinic.controller;

import jakarta.persistence.EntityNotFoundException;
import org.codeflow.veterineryproject.clinic.model.ClinicGroup;
import org.codeflow.veterineryproject.clinic.repository.ClinicGroupRepository;
import org.codeflow.veterineryproject.common.api.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/clinic-groups")
public class ClinicGroupController {

    private final ClinicGroupRepository clinicGroupRepository;

    public ClinicGroupController(ClinicGroupRepository clinicGroupRepository) {
        this.clinicGroupRepository = clinicGroupRepository;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<ClinicGroup>>> list() {
        return ResponseEntity.ok(ApiResponse.success("Clinic groups listed successfully", clinicGroupRepository.findAll()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<ClinicGroup>> get(@PathVariable UUID id) {
        ClinicGroup group = clinicGroupRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Clinic group not found"));
        return ResponseEntity.ok(ApiResponse.success("Clinic group fetched successfully", group));
    }
}
