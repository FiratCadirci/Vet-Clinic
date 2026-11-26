package org.codeflow.veterineryproject.product.service;

import jakarta.persistence.EntityNotFoundException;
import org.codeflow.veterineryproject.common.api.ConflictException;
import org.codeflow.veterineryproject.common.audit.AuditRecorder;
import org.codeflow.veterineryproject.common.pagination.PageResponse;
import org.codeflow.veterineryproject.common.pagination.PaginationUtils;
import org.codeflow.veterineryproject.common.sequence.SequenceCounterRepository;
import org.codeflow.veterineryproject.common.tenant.TenantContext;
import org.codeflow.veterineryproject.product.dto.ProductRequest;
import org.codeflow.veterineryproject.product.dto.ProductResponse;
import org.codeflow.veterineryproject.product.dto.ProductUpdateRequest;
import org.codeflow.veterineryproject.product.repository.ProductJdbcRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

/** Ürün kartı yönetimi (FG-F1). Barkod organizasyon içinde tekildir (DB: ux_products_org_barcode). */
@Service
public class ProductService {

    private static final String NOT_FOUND = "Ürün bulunamadı.";

    private final ProductJdbcRepository repository;
    private final SequenceCounterRepository sequences;
    private final AuditRecorder audit;

    public ProductService(ProductJdbcRepository repository, SequenceCounterRepository sequences, AuditRecorder audit) {
        this.repository = repository;
        this.sequences = sequences;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public PageResponse<ProductResponse> list(String search, String productType, Boolean active, int page, int size) {
        Pageable pageable = PaginationUtils.createPageable(page, size);
        long total = repository.count(search, productType, active);
        return PageResponse.from(new PageImpl<>(
                repository.search(search, productType, active, pageable.getPageSize(), pageable.getOffset()),
                pageable, total));
    }

    @Transactional(readOnly = true)
    public ProductResponse get(UUID id) {
        return repository.find(id).orElseThrow(() -> new EntityNotFoundException(NOT_FOUND));
    }

    @Transactional
    public ProductResponse create(ProductRequest r) {
        TenantContext.ContextValues ctx = TenantContext.require();
        String barcode = blankToNull(r.barcode());
        ensureBarcodeFree(barcode, null);
        String sku = blankToNull(r.sku());
        if (sku == null) {
            sku = "URN-%06d".formatted(sequences.next(ctx.organizationId(), "PRODUCT_SKU"));
        }
        UUID id = UUID.randomUUID();
        try {
            repository.insert(id, ctx.organizationId(), sku, barcode, r.name().trim(), r.productType(),
                    blankToNull(r.brand()), r.baseUnit().trim(), r.stockItem() == null || r.stockItem(),
                    Boolean.TRUE.equals(r.tracksLot()), orZero(r.minimumStock()), orZero(r.purchasePrice()),
                    r.salePrice(), r.taxRate() == null ? new BigDecimal("20") : r.taxRate());
        } catch (DuplicateKeyException ex) {
            throw duplicate(ex, barcode, sku);
        }
        audit.record("PRODUCT_CREATE", "PRODUCT", id, ctx.clinicId());
        return get(id);
    }

    @Transactional
    public ProductResponse update(UUID id, ProductUpdateRequest r) {
        TenantContext.ContextValues ctx = TenantContext.require();
        ProductResponse cur = get(id);
        String barcode = r.barcode() == null ? cur.barcode() : blankToNull(r.barcode());
        if (barcode != null && !barcode.equals(cur.barcode())) {
            ensureBarcodeFree(barcode, id);
        }
        String sku = r.sku() == null || r.sku().isBlank() ? cur.sku() : r.sku().trim();
        try {
            repository.update(id, sku, barcode,
                    r.name() == null ? cur.name() : r.name().trim(),
                    r.productType() == null ? cur.productType() : r.productType(),
                    r.brand() == null ? cur.brand() : blankToNull(r.brand()),
                    r.baseUnit() == null ? cur.baseUnit() : r.baseUnit().trim(),
                    r.stockItem() == null ? cur.stockItem() : r.stockItem(),
                    r.tracksLot() == null ? cur.tracksLot() : r.tracksLot(),
                    r.minimumStock() == null ? cur.minimumStock() : r.minimumStock(),
                    r.purchasePrice() == null ? cur.purchasePrice() : r.purchasePrice(),
                    r.salePrice() == null ? cur.salePrice() : r.salePrice(),
                    r.taxRate() == null ? cur.taxRate() : r.taxRate(),
                    r.active() == null ? cur.active() : r.active());
        } catch (DuplicateKeyException ex) {
            throw duplicate(ex, barcode, sku);
        }
        audit.record("PRODUCT_UPDATE", "PRODUCT", id, ctx.clinicId());
        return get(id);
    }

    /** Hareketi, partisi veya satış satırı olan ürün silinemez (FK); bu durumda ürün pasifleştirilir. */
    @Transactional
    public void delete(UUID id) {
        TenantContext.ContextValues ctx = TenantContext.require();
        get(id);
        try {
            repository.delete(id);
        } catch (DataIntegrityViolationException ex) {
            throw new ConflictException("Stok hareketi, partisi veya satışı olan ürün silinemez; ürünü pasifleştirin.",
                    "PRODUCT_IN_USE", Map.of("productId", id));
        }
        audit.record("PRODUCT_DELETE", "PRODUCT", id, ctx.clinicId());
    }

    private void ensureBarcodeFree(String barcode, UUID exceptId) {
        if (barcode != null && repository.barcodeTaken(barcode, exceptId)) {
            throw barcodeConflict(barcode);
        }
    }

    /** Ön kontrolü geçen eşzamanlı kayıtlar benzersiz indekse takılır; aynı 409'a çevrilir. */
    private static ConflictException duplicate(DuplicateKeyException ex, String barcode, String sku) {
        String msg = String.valueOf(ex.getMostSpecificCause().getMessage());
        if (msg.contains("barcode")) {
            return barcodeConflict(barcode);
        }
        return new ConflictException("Bu stok kodu (SKU) başka bir ürüne ait.", "PRODUCT_SKU_EXISTS", Map.of("sku", sku));
    }

    private static ConflictException barcodeConflict(String barcode) {
        return new ConflictException("Bu barkod başka bir ürüne ait.", "PRODUCT_BARCODE_EXISTS", Map.of("barcode", barcode));
    }

    private static BigDecimal orZero(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    private static String blankToNull(String v) {
        return v == null || v.isBlank() ? null : v.trim();
    }
}
