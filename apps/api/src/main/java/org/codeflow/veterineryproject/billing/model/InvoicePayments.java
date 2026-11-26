package org.codeflow.veterineryproject.billing.model;

import jakarta.persistence.*;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.*;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "invoice_payments", schema = "vet")
public class InvoicePayments {

    @EmbeddedId
    private InvoicePaymentsId id;

    @Column(name = "applied_amount")
    private BigDecimal appliedAmount;

    @Column(name = "applied_at")
    private OffsetDateTime appliedAt;

    public InvoicePaymentsId getId() { return id; }
    public void setId(InvoicePaymentsId id) { this.id = id; }
    public BigDecimal getAppliedAmount() { return appliedAmount; }
    public void setAppliedAmount(BigDecimal appliedAmount) { this.appliedAmount = appliedAmount; }
    public OffsetDateTime getAppliedAt() { return appliedAt; }
    public void setAppliedAt(OffsetDateTime appliedAt) { this.appliedAt = appliedAt; }

    @Embeddable
    public static class InvoicePaymentsId implements Serializable {
        private static final long serialVersionUID = 1L;

        @Column(name = "organization_id")
        private UUID organizationId;

        @Column(name = "invoice_id")
        private UUID invoiceId;

        @Column(name = "payment_id")
        private UUID paymentId;

        public UUID getOrganizationId() { return organizationId; }
        public void setOrganizationId(UUID organizationId) { this.organizationId = organizationId; }
        public UUID getInvoiceId() { return invoiceId; }
        public void setInvoiceId(UUID invoiceId) { this.invoiceId = invoiceId; }
        public UUID getPaymentId() { return paymentId; }
        public void setPaymentId(UUID paymentId) { this.paymentId = paymentId; }
        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof InvoicePaymentsId)) return false;
            InvoicePaymentsId that = (InvoicePaymentsId) o;
            return Objects.equals(organizationId, that.organizationId) && Objects.equals(invoiceId, that.invoiceId) && Objects.equals(paymentId, that.paymentId);
        }
        @Override
        public int hashCode() {
            return Objects.hash(organizationId, invoiceId, paymentId);
        }
    }
}
