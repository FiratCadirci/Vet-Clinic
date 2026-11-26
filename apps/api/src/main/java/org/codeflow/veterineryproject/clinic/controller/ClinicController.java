package org.codeflow.veterineryproject.clinic.controller;

import jakarta.persistence.EntityNotFoundException;
import org.codeflow.veterineryproject.clinic.dto.ClinicResponse;
import org.codeflow.veterineryproject.clinic.model.Clinic;
import org.codeflow.veterineryproject.clinic.service.ClinicService;
import org.codeflow.veterineryproject.common.api.ApiResponse;
import org.springframework.beans.BeanUtils;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/clinics")
public class ClinicController {

    private final ClinicService clinicService;

    public ClinicController(ClinicService clinicService) {
        this.clinicService = clinicService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<ClinicResponse>>> list() {
        List<ClinicResponse> clinics = clinicService.getAll().stream().map(ClinicController::toResponse).toList();
        return ResponseEntity.ok(ApiResponse.success("Clinics listed successfully", clinics));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<ClinicResponse>> get(@PathVariable UUID id) {
        Clinic clinic = clinicService.getById(id);
        if (clinic == null) {
            throw new EntityNotFoundException("Clinic not found");
        }
        return ResponseEntity.ok(ApiResponse.success("Clinic fetched successfully", toResponse(clinic)));
    }

    private static ClinicResponse toResponse(Clinic clinic) {
        ClinicResponse response = new ClinicResponse();
        BeanUtils.copyProperties(clinic, response);
        return response;
    }
}
