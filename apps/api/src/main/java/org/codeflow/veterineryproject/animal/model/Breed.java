package org.codeflow.veterineryproject.animal.model;

import jakarta.persistence.*;

import java.util.UUID;

@Entity
@Table(name = "breeds", schema = "vet")
public class Breed {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "species_id", nullable = false)
    private UUID speciesId;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "is_mixed", nullable = false)
    private Boolean isMixed = false;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getSpeciesId() { return speciesId; }
    public void setSpeciesId(UUID speciesId) { this.speciesId = speciesId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public Boolean getIsMixed() { return isMixed; }
    public void setIsMixed(Boolean isMixed) { this.isMixed = isMixed; }
}
