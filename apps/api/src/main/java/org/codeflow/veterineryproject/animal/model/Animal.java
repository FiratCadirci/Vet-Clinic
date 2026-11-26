package org.codeflow.veterineryproject.animal.model;

import jakarta.persistence.*;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "animals", schema = "vet")
public class Animal {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Column(name = "patient_no", nullable = false)
    private String patientNo;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "species_id", nullable = false)
    private UUID speciesId;

    @Column(name = "breed_id")
    private UUID breedId;

    @Column(name = "sex", nullable = false)
    private String sex;

    @Column(name = "birth_date")
    private LocalDate birthDate;

    @Column(name = "birth_date_estimated", nullable = false)
    private Boolean birthDateEstimated = false;

    @Column(name = "color")
    private String color;

    @Column(name = "distinguishing_marks")
    private String distinguishingMarks;

    @Column(name = "sterilized", nullable = false)
    private Boolean sterilized = false;

    @Column(name = "sterilization_date")
    private LocalDate sterilizationDate;

    @Column(name = "deceased_at")
    private LocalDate deceasedAt;

    @Column(name = "profile_notes")
    private String profileNotes;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getOrganizationId() { return organizationId; }
    public void setOrganizationId(UUID organizationId) { this.organizationId = organizationId; }
    public String getPatientNo() { return patientNo; }
    public void setPatientNo(String patientNo) { this.patientNo = patientNo; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public UUID getSpeciesId() { return speciesId; }
    public void setSpeciesId(UUID speciesId) { this.speciesId = speciesId; }
    public UUID getBreedId() { return breedId; }
    public void setBreedId(UUID breedId) { this.breedId = breedId; }
    public String getSex() { return sex; }
    public void setSex(String sex) { this.sex = sex; }
    public LocalDate getBirthDate() { return birthDate; }
    public void setBirthDate(LocalDate birthDate) { this.birthDate = birthDate; }
    public Boolean getBirthDateEstimated() { return birthDateEstimated; }
    public void setBirthDateEstimated(Boolean birthDateEstimated) { this.birthDateEstimated = birthDateEstimated; }
    public String getColor() { return color; }
    public void setColor(String color) { this.color = color; }
    public String getDistinguishingMarks() { return distinguishingMarks; }
    public void setDistinguishingMarks(String distinguishingMarks) { this.distinguishingMarks = distinguishingMarks; }
    public Boolean getSterilized() { return sterilized; }
    public void setSterilized(Boolean sterilized) { this.sterilized = sterilized; }
    public LocalDate getSterilizationDate() { return sterilizationDate; }
    public void setSterilizationDate(LocalDate sterilizationDate) { this.sterilizationDate = sterilizationDate; }
    public LocalDate getDeceasedAt() { return deceasedAt; }
    public void setDeceasedAt(LocalDate deceasedAt) { this.deceasedAt = deceasedAt; }
    public String getProfileNotes() { return profileNotes; }
    public void setProfileNotes(String profileNotes) { this.profileNotes = profileNotes; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }
}
