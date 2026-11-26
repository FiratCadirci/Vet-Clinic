package org.codeflow.veterineryproject.product.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.UUID;

public class ProductRequest {

    @NotNull
    private UUID organizationId;

    @NotBlank
    private String sku;

    private String barcode;

    @NotBlank
    private String name;

    @NotBlank
    private String productType;

    private String brand;

    @NotBlank
    private String baseUnit;

    @NotNull
    private Boolean isStockItem = true;

    @NotNull
    private Boolean tracksLot = true;

    @NotNull
    private Boolean tracksExpiry = true;

    @NotNull
    private BigDecimal minimumStock = BigDecimal.ZERO;

    private BigDecimal salePrice;

    @NotNull
    private BigDecimal taxRate;

    @NotNull
    private Boolean isActive = true;

    public UUID getOrganizationId() { return organizationId; }
    public void setOrganizationId(UUID organizationId) { this.organizationId = organizationId; }
    public String getSku() { return sku; }
    public void setSku(String sku) { this.sku = sku; }
    public String getBarcode() { return barcode; }
    public void setBarcode(String barcode) { this.barcode = barcode; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getProductType() { return productType; }
    public void setProductType(String productType) { this.productType = productType; }
    public String getBrand() { return brand; }
    public void setBrand(String brand) { this.brand = brand; }
    public String getBaseUnit() { return baseUnit; }
    public void setBaseUnit(String baseUnit) { this.baseUnit = baseUnit; }
    public Boolean getIsStockItem() { return isStockItem; }
    public void setIsStockItem(Boolean isStockItem) { this.isStockItem = isStockItem; }
    public Boolean getTracksLot() { return tracksLot; }
    public void setTracksLot(Boolean tracksLot) { this.tracksLot = tracksLot; }
    public Boolean getTracksExpiry() { return tracksExpiry; }
    public void setTracksExpiry(Boolean tracksExpiry) { this.tracksExpiry = tracksExpiry; }
    public BigDecimal getMinimumStock() { return minimumStock; }
    public void setMinimumStock(BigDecimal minimumStock) { this.minimumStock = minimumStock; }
    public BigDecimal getSalePrice() { return salePrice; }
    public void setSalePrice(BigDecimal salePrice) { this.salePrice = salePrice; }
    public BigDecimal getTaxRate() { return taxRate; }
    public void setTaxRate(BigDecimal taxRate) { this.taxRate = taxRate; }
    public Boolean getIsActive() { return isActive; }
    public void setIsActive(Boolean isActive) { this.isActive = isActive; }
}
