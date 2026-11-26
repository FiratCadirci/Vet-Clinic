package org.codeflow.veterineryproject.product.model;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "products", schema = "vet")
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Column(name = "sku", nullable = false)
    private String sku;

    @Column(name = "barcode")
    private String barcode;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "product_type", nullable = false)
    private String productType;

    @Column(name = "brand")
    private String brand;

    @Column(name = "base_unit", nullable = false)
    private String baseUnit;

    @Column(name = "is_stock_item", nullable = false)
    private Boolean isStockItem = true;

    @Column(name = "tracks_lot", nullable = false)
    private Boolean tracksLot = true;

    @Column(name = "tracks_expiry", nullable = false)
    private Boolean tracksExpiry = true;

    @Column(name = "minimum_stock", nullable = false)
    private BigDecimal minimumStock = BigDecimal.ZERO;

    @Column(name = "sale_price")
    private BigDecimal salePrice;

    @Column(name = "tax_rate", nullable = false)
    private BigDecimal taxRate;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
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
