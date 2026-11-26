package org.codeflow.veterineryproject.subscription.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.UUID;

@Entity
@Table(name = "organization_subscriptions", schema = "vet")
public class OrganizationSubscriptions {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "organization_id")
    private UUID organizationId;

    @Column(name = "plan_id")
    private UUID planId;

    @Column(name = "billing_period")
    private String billingPeriod;

    @Column(name = "price_snapshot")
    private BigDecimal priceSnapshot;

    @Column(name = "currency")
    private String currency;

    @Column(name = "max_branches_snapshot")
    private Integer maxBranchesSnapshot;

    @Column(name = "max_users_snapshot")
    private Integer maxUsersSnapshot;

    @Column(name = "starts_at")
    private OffsetDateTime startsAt;

    @Column(name = "current_period_start")
    private OffsetDateTime currentPeriodStart;

    @Column(name = "current_period_end")
    private OffsetDateTime currentPeriodEnd;

    @Column(name = "next_renewal_at")
    private OffsetDateTime nextRenewalAt;

    @Column(name = "cancelled_at")
    private OffsetDateTime cancelledAt;

    @Column(name = "created_by")
    private UUID createdBy;

    @Column(name = "notes")
    private String notes;

    @Column(name = "created_at")
    private OffsetDateTime createdAt;

    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getOrganizationId() { return organizationId; }
    public void setOrganizationId(UUID organizationId) { this.organizationId = organizationId; }
    public UUID getPlanId() { return planId; }
    public void setPlanId(UUID planId) { this.planId = planId; }
    public String getBillingPeriod() { return billingPeriod; }
    public void setBillingPeriod(String billingPeriod) { this.billingPeriod = billingPeriod; }
    public BigDecimal getPriceSnapshot() { return priceSnapshot; }
    public void setPriceSnapshot(BigDecimal priceSnapshot) { this.priceSnapshot = priceSnapshot; }
    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }
    public Integer getMaxBranchesSnapshot() { return maxBranchesSnapshot; }
    public void setMaxBranchesSnapshot(Integer maxBranchesSnapshot) { this.maxBranchesSnapshot = maxBranchesSnapshot; }
    public Integer getMaxUsersSnapshot() { return maxUsersSnapshot; }
    public void setMaxUsersSnapshot(Integer maxUsersSnapshot) { this.maxUsersSnapshot = maxUsersSnapshot; }
    public OffsetDateTime getStartsAt() { return startsAt; }
    public void setStartsAt(OffsetDateTime startsAt) { this.startsAt = startsAt; }
    public OffsetDateTime getCurrentPeriodStart() { return currentPeriodStart; }
    public void setCurrentPeriodStart(OffsetDateTime currentPeriodStart) { this.currentPeriodStart = currentPeriodStart; }
    public OffsetDateTime getCurrentPeriodEnd() { return currentPeriodEnd; }
    public void setCurrentPeriodEnd(OffsetDateTime currentPeriodEnd) { this.currentPeriodEnd = currentPeriodEnd; }
    public OffsetDateTime getNextRenewalAt() { return nextRenewalAt; }
    public void setNextRenewalAt(OffsetDateTime nextRenewalAt) { this.nextRenewalAt = nextRenewalAt; }
    public OffsetDateTime getCancelledAt() { return cancelledAt; }
    public void setCancelledAt(OffsetDateTime cancelledAt) { this.cancelledAt = cancelledAt; }
    public UUID getCreatedBy() { return createdBy; }
    public void setCreatedBy(UUID createdBy) { this.createdBy = createdBy; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }
}
