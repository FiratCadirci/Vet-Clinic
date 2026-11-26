package org.codeflow.veterineryproject.product.repository;

import org.codeflow.veterineryproject.common.sql.SqlLike;
import org.codeflow.veterineryproject.product.dto.ProductResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Ürün kartı sorguları. RLS (tenant_isolation) organizasyonu sınırlar; ürünler organizasyon genelidir. */
@Repository
public class ProductJdbcRepository {

    private static final String SELECT = """
            select id, sku, barcode, name, product_type, brand, base_unit, is_stock_item, tracks_lot, minimum_stock,
                   purchase_price, sale_price, tax_rate, is_active, created_at, updated_at
            from vet.products p
            """;

    private static final RowMapper<ProductResponse> MAPPER = (rs, n) -> new ProductResponse(
            rs.getObject("id", UUID.class), rs.getString("sku"), rs.getString("barcode"), rs.getString("name"),
            rs.getString("product_type"), rs.getString("brand"), rs.getString("base_unit"),
            rs.getBoolean("is_stock_item"), rs.getBoolean("tracks_lot"), rs.getBigDecimal("minimum_stock"),
            rs.getBigDecimal("purchase_price"), rs.getBigDecimal("sale_price"), rs.getBigDecimal("tax_rate"),
            rs.getBoolean("is_active"), rs.getObject("created_at", OffsetDateTime.class),
            rs.getObject("updated_at", OffsetDateTime.class));

    private final JdbcTemplate jdbc;

    public ProductJdbcRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<ProductResponse> find(UUID id) {
        return jdbc.query(SELECT + " where p.id = ?", MAPPER, id).stream().findFirst();
    }

    public boolean barcodeTaken(String barcode, UUID exceptId) {
        Boolean taken = jdbc.queryForObject(
                "select exists (select 1 from vet.products where barcode = ? and id is distinct from ?)",
                Boolean.class, barcode, exceptId);
        return Boolean.TRUE.equals(taken);
    }

    public void insert(UUID id, UUID orgId, String sku, String barcode, String name, String productType, String brand,
                       String baseUnit, boolean stockItem, boolean tracksLot, BigDecimal minimumStock,
                       BigDecimal purchasePrice, BigDecimal salePrice, BigDecimal taxRate) {
        // Arayüz tek bir "parti/SKT takibi" seçeneği sunar; tracks_expiry aynı değeri izler.
        jdbc.update("""
                insert into vet.products (id, organization_id, sku, barcode, name, product_type, brand, base_unit,
                                          is_stock_item, tracks_lot, tracks_expiry, minimum_stock, purchase_price,
                                          sale_price, tax_rate)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, id, orgId, sku, barcode, name, productType, brand, baseUnit, stockItem, tracksLot, tracksLot,
                minimumStock, purchasePrice, salePrice, taxRate);
    }

    public int update(UUID id, String sku, String barcode, String name, String productType, String brand,
                      String baseUnit, boolean stockItem, boolean tracksLot, BigDecimal minimumStock,
                      BigDecimal purchasePrice, BigDecimal salePrice, BigDecimal taxRate, boolean active) {
        return jdbc.update("""
                update vet.products
                set sku = ?, barcode = ?, name = ?, product_type = ?, brand = ?, base_unit = ?, is_stock_item = ?,
                    tracks_lot = ?, tracks_expiry = ?, minimum_stock = ?, purchase_price = ?, sale_price = ?,
                    tax_rate = ?, is_active = ?
                where id = ?
                """, sku, barcode, name, productType, brand, baseUnit, stockItem, tracksLot, tracksLot, minimumStock,
                purchasePrice, salePrice, taxRate, active, id);
    }

    public int delete(UUID id) {
        return jdbc.update("delete from vet.products where id = ?", id);
    }

    public List<ProductResponse> search(String search, String productType, Boolean active, int limit, long offset) {
        List<Object> args = new ArrayList<>();
        String where = where(search, productType, active, args);
        args.add(limit);
        args.add(offset);
        return jdbc.query(SELECT + where + " order by p.name, p.id limit ? offset ?", MAPPER, args.toArray());
    }

    public long count(String search, String productType, Boolean active) {
        List<Object> args = new ArrayList<>();
        String where = where(search, productType, active, args);
        Long n = jdbc.queryForObject("select count(*) from vet.products p" + where, Long.class, args.toArray());
        return n == null ? 0 : n;
    }

    private static String where(String search, String productType, Boolean active, List<Object> args) {
        StringBuilder sb = new StringBuilder(" where 1 = 1");
        if (search != null && !search.isBlank()) {
            String like = SqlLike.contains(search);
            sb.append(" and (lower(p.name) like ? escape '\\' or lower(coalesce(p.barcode, '')) like ? escape '\\'"
                    + " or lower(p.sku) like ? escape '\\')");
            args.add(like);
            args.add(like);
            args.add(like);
        }
        if (productType != null) {
            sb.append(" and p.product_type = ?");
            args.add(productType);
        }
        if (active != null) {
            sb.append(" and p.is_active = ?");
            args.add(active);
        }
        return sb.toString();
    }
}
