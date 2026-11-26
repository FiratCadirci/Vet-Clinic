package org.codeflow.veterineryproject.clinic.service;

import org.codeflow.veterineryproject.clinic.model.Clinic;
import org.codeflow.veterineryproject.clinic.repository.ClinicRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class ClinicService {

    private final ClinicRepository clinicRepository;

    public ClinicService(ClinicRepository clinicRepository) {
        this.clinicRepository = clinicRepository;
    }

    public List<Clinic> getAll() {
        return clinicRepository.findAll();
    }

    public Clinic getById(UUID id) {
        return clinicRepository.findById(id).orElse(null);
    }

    public Clinic save(Clinic clinic) {
        return clinicRepository.save(clinic);
    }
}
