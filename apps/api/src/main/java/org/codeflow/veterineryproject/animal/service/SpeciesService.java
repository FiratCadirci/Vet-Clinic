package org.codeflow.veterineryproject.animal.service;

import jakarta.persistence.EntityNotFoundException;
import org.codeflow.veterineryproject.animal.dto.BreedResponse;
import org.codeflow.veterineryproject.animal.dto.SpeciesResponse;
import org.codeflow.veterineryproject.animal.repository.AnimalJdbcRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class SpeciesService {

    private final AnimalJdbcRepository repository;

    public SpeciesService(AnimalJdbcRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public List<SpeciesResponse> listSpecies() {
        return repository.findSpecies();
    }

    @Transactional(readOnly = true)
    public List<BreedResponse> listBreeds(UUID speciesId) {
        if (!repository.speciesExists(speciesId)) {
            throw new EntityNotFoundException("Tür bulunamadı.");
        }
        return repository.findBreeds(speciesId);
    }
}
