package org.codeflow.veterineryproject.commission.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.UUID;

@Entity
@Table(name = "commission_records", schema = "vet")
public class CommissionRecords {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "organization_id")
    private UUID organizationId;

    @Column(name = "clinic_id")
    private UUID clinicId;

    @Column(name = "invoice_id")
    private UUID invoiceId;

    @Column(name = "invoice_line_id")
    private UUID invoiceLineId;

    @Column(name = "commission_rule_id")
    private UUID commissionRuleId;

    @Column(name = "eligible_amount")
    private BigDecimal eligibleAmount;

    @Column(name = "rate_percent")
    private BigDecimal ratePercent;

    @Column(name = "commission_amount")
    private BigDecimal commissionAmount;

    @Column(name = "calculated_at")
    private OffsetDateTime calculatedAt;

    @Column(name = "settled_at")
    private OffsetDateTime settledAt;

    @Column(name = "settlement_reference")
    private String settlementReference;

    @Column(name = "created_at")
    private OffsetDateTime createdAt;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getOrganizationId() { return organizationId; }
    public void setOrganizationId(UUID organizationId) { this.organizationId = organizationId; }
    public UUID getClinicId() { return clinicId; }
    public void setClinicId(UUID clinicId) { this.clinicId = clinicId; }
    public UUID getInvoiceId() { return invoiceId; }
    public void setInvoiceId(UUID invoiceId) { this.invoiceId = invoiceId; }
    public UUID getInvoiceLineId() { return invoiceLineId; }
    public void setInvoiceLineId(UUID invoiceLineId) { this.invoiceLineId = invoiceLineId; }
    public UUID getCommissionRuleId() { return commissionRuleId; }
    public void setCommissionRuleId(UUID commissionRuleId) { this.commissionRuleId = commissionRuleId; }
    public BigDecimal getEligibleAmount() { return eligibleAmount; }
    public void setEligibleAmount(BigDecimal eligibleAmount) { this.eligibleAmount = eligibleAmount; }
    public BigDecimal getRatePercent() { return ratePercent; }
    public void setRatePercent(BigDecimal ratePercent) { this.ratePercent = ratePercent; }
    public BigDecimal getCommissionAmount() { return commissionAmount; }
    public void setCommissionAmount(BigDecimal commissionAmount) { this.commissionAmount = commissionAmount; }
    public OffsetDateTime getCalculatedAt() { return calculatedAt; }
    public void setCalculatedAt(OffsetDateTime calculatedAt) { this.calculatedAt = calculatedAt; }
    public OffsetDateTime getSettledAt() { return settledAt; }
    public void setSettledAt(OffsetDateTime settledAt) { this.settledAt = settledAt; }
    public String getSettlementReference() { return settlementReference; }
    public void setSettlementReference(String settlementReference) { this.settlementReference = settlementReference; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
}
