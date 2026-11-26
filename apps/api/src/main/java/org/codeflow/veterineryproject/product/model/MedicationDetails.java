package org.codeflow.veterineryproject.product.model;

import jakarta.persistence.*;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.*;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "medication_details", schema = "vet")
public class MedicationDetails {

    @EmbeddedId
    private MedicationDetailsId id;

    @Column(name = "active_ingredient")
    private String activeIngredient;

    @Column(name = "strength")
    private String strength;

    @Column(name = "dosage_form")
    private String dosageForm;

    @Column(name = "default_route")
    private String defaultRoute;

    @Column(name = "storage_instructions")
    private String storageInstructions;

    @Column(name = "withdrawal_period_days")
    private Integer withdrawalPeriodDays;

    @Column(name = "species_warnings")
    private String speciesWarnings;

    public MedicationDetailsId getId() { return id; }
    public void setId(MedicationDetailsId id) { this.id = id; }
    public String getActiveIngredient() { return activeIngredient; }
    public void setActiveIngredient(String activeIngredient) { this.activeIngredient = activeIngredient; }
    public String getStrength() { return strength; }
    public void setStrength(String strength) { this.strength = strength; }
    public String getDosageForm() { return dosageForm; }
    public void setDosageForm(String dosageForm) { this.dosageForm = dosageForm; }
    public String getDefaultRoute() { return defaultRoute; }
    public void setDefaultRoute(String defaultRoute) { this.defaultRoute = defaultRoute; }
    public String getStorageInstructions() { return storageInstructions; }
    public void setStorageInstructions(String storageInstructions) { this.storageInstructions = storageInstructions; }
    public Integer getWithdrawalPeriodDays() { return withdrawalPeriodDays; }
    public void setWithdrawalPeriodDays(Integer withdrawalPeriodDays) { this.withdrawalPeriodDays = withdrawalPeriodDays; }
    public String getSpeciesWarnings() { return speciesWarnings; }
    public void setSpeciesWarnings(String speciesWarnings) { this.speciesWarnings = speciesWarnings; }

    @Embeddable
    public static class MedicationDetailsId implements Serializable {
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
            if (!(o instanceof MedicationDetailsId)) return false;
            MedicationDetailsId that = (MedicationDetailsId) o;
            return Objects.equals(organizationId, that.organizationId) && Objects.equals(productId, that.productId);
        }
        @Override
        public int hashCode() {
            return Objects.hash(organizationId, productId);
        }
    }
}
