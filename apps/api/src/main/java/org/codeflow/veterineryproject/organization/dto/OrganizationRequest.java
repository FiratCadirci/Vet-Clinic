package org.codeflow.veterineryproject.organization.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public class OrganizationRequest {

    @NotBlank
    private Integer code;

    @NotBlank
    private String name;

    private String legalName;
    private String taxOffice;
    private String taxNumber;

    @NotBlank
    private String defaultCurrency;

    @NotBlank
    private String timezoneName;

    @NotNull
    private Boolean isActive = true;

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
}
