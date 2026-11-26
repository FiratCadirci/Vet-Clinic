package org.codeflow.veterineryproject.prescription.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.UUID;

@Entity
@Table(name = "prescription_items", schema = "vet")
public class PrescriptionItems {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "organization_id")
    private UUID organizationId;

    @Column(name = "prescription_id")
    private UUID prescriptionId;

    @Column(name = "product_id")
    private UUID productId;

    @Column(name = "line_no")
    private Integer lineNo;

    @Column(name = "dose")
    private BigDecimal dose;

    @Column(name = "dose_unit")
    private String doseUnit;

    @Column(name = "route")
    private String route;

    @Column(name = "frequency_text")
    private String frequencyText;

    @Column(name = "duration_days")
    private Integer durationDays;

    @Column(name = "quantity_prescribed")
    private BigDecimal quantityPrescribed;

    @Column(name = "refills_allowed")
    private Integer refillsAllowed;

    @Column(name = "instructions")
    private String instructions;

    @Column(name = "quantity_dispensed")
    private BigDecimal quantityDispensed;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getOrganizationId() { return organizationId; }
    public void setOrganizationId(UUID organizationId) { this.organizationId = organizationId; }
    public UUID getPrescriptionId() { return prescriptionId; }
    public void setPrescriptionId(UUID prescriptionId) { this.prescriptionId = prescriptionId; }
    public UUID getProductId() { return productId; }
    public void setProductId(UUID productId) { this.productId = productId; }
    public Integer getLineNo() { return lineNo; }
    public void setLineNo(Integer lineNo) { this.lineNo = lineNo; }
    public BigDecimal getDose() { return dose; }
    public void setDose(BigDecimal dose) { this.dose = dose; }
    public String getDoseUnit() { return doseUnit; }
    public void setDoseUnit(String doseUnit) { this.doseUnit = doseUnit; }
    public String getRoute() { return route; }
    public void setRoute(String route) { this.route = route; }
    public String getFrequencyText() { return frequencyText; }
    public void setFrequencyText(String frequencyText) { this.frequencyText = frequencyText; }
    public Integer getDurationDays() { return durationDays; }
    public void setDurationDays(Integer durationDays) { this.durationDays = durationDays; }
    public BigDecimal getQuantityPrescribed() { return quantityPrescribed; }
    public void setQuantityPrescribed(BigDecimal quantityPrescribed) { this.quantityPrescribed = quantityPrescribed; }
    public Integer getRefillsAllowed() { return refillsAllowed; }
    public void setRefillsAllowed(Integer refillsAllowed) { this.refillsAllowed = refillsAllowed; }
    public String getInstructions() { return instructions; }
    public void setInstructions(String instructions) { this.instructions = instructions; }
    public BigDecimal getQuantityDispensed() { return quantityDispensed; }
    public void setQuantityDispensed(BigDecimal quantityDispensed) { this.quantityDispensed = quantityDispensed; }
}
