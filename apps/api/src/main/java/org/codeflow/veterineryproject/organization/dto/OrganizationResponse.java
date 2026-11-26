package org.codeflow.veterineryproject.organization.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public class OrganizationResponse {

    private UUID id;
    private Integer code;
    private String name;
    private String legalName;
    private String taxOffice;
    private String taxNumber;
    private String defaultCurrency;
    private String timezoneName;
    private Boolean isActive;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public Integer getCode() { return code; }
    public void setCode(Integer code) { this.code = code; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getLegalName() { return legalName; }
    public void setLegalName(String legalName) { this.legalName = legalName; }
    public String getTaxOffice() { return taxOffice; }
    public void setTaxOffice(String taxOffice) { this.taxOffice = taxOffice; }
    public String getTaxNumber() { return taxNumber; }
    public void setTaxNumber(String taxNumber) { this.taxNumber = taxNumber; }
    public String getDefaultCurrency() { return defaultCurrency; }
    public void setDefaultCurrency(String defaultCurrency) { this.defaultCurrency = defaultCurrency; }
    public String getTimezoneName() { return timezoneName; }
    public void setTimezoneName(String timezoneName) { this.timezoneName = timezoneName; }
    public Boolean getIsActive() { return isActive; }
    public void setIsActive(Boolean isActive) { this.isActive = isActive; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }
}
