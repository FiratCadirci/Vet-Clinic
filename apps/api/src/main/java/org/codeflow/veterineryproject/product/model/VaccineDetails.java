package org.codeflow.veterineryproject.product.model;

import jakarta.persistence.*;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.*;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "vaccine_details", schema = "vet")
public class VaccineDetails {

    @EmbeddedId
    private VaccineDetailsId id;

    @Column(name = "target_diseases")
    private String targetDiseases;

    @Column(name = "target_species_id")
    private UUID targetSpeciesId;

    @Column(name = "minimum_age_days")
    private Integer minimumAgeDays;

    @Column(name = "booster_interval_days")
    private Integer boosterIntervalDays;

    @Column(name = "storage_temperature")
    private String storageTemperature;

    public VaccineDetailsId getId() { return id; }
    public void setId(VaccineDetailsId id) { this.id = id; }
    public String getTargetDiseases() { return targetDiseases; }
    public void setTargetDiseases(String targetDiseases) { this.targetDiseases = targetDiseases; }
    public UUID getTargetSpeciesId() { return targetSpeciesId; }
    public void setTargetSpeciesId(UUID targetSpeciesId) { this.targetSpeciesId = targetSpeciesId; }
    public Integer getMinimumAgeDays() { return minimumAgeDays; }
    public void setMinimumAgeDays(Integer minimumAgeDays) { this.minimumAgeDays = minimumAgeDays; }
    public Integer getBoosterIntervalDays() { return boosterIntervalDays; }
    public void setBoosterIntervalDays(Integer boosterIntervalDays) { this.boosterIntervalDays = boosterIntervalDays; }
    public String getStorageTemperature() { return storageTemperature; }
    public void setStorageTemperature(String storageTemperature) { this.storageTemperature = storageTemperature; }

    @Embeddable
    public static class VaccineDetailsId implements Serializable {
        private static final long serialVersionUID = 1L;

        @Column(name = "organization_id")
        private UUID organizationId;

        @Column(name = "product_id")
        private UUID productId;

        public UUID getOrganizationId() { return organizationId; }
        public void setOrganizationId(UUID organizationId) { this.organizationId = organizationId; }
        public UUID getProductId() { return productId; }
        public void setProductId(UUID productId) { this.productId = productId; }
        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof VaccineDetailsId)) return false;
            VaccineDetailsId that = (VaccineDetailsId) o;
            return Objects.equals(organizationId, that.organizationId) && Objects.equals(productId, that.productId);
        }
        @Override
        public int hashCode() {
            return Objects.hash(organizationId, productId);
        }
    }
}
