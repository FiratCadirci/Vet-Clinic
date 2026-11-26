package org.codeflow.veterineryproject.product.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public class ProductResponse {

    private UUID id;
    private UUID organizationId;
    private String sku;
    private String barcode;
    private String name;
    private String productType;
    private String brand;
    private String baseUnit;
    private Boolean isStockItem;
    private Boolean tracksLot;
    private Boolean tracksExpiry;
    private BigDecimal minimumStock;
    private BigDecimal salePrice;
    private BigDecimal taxRate;
    private Boolean isActive;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
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
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }
}
