package org.codeflow.veterineryproject.inventory.repository;

import org.codeflow.veterineryproject.inventory.allocation.StockBucket;
import org.codeflow.veterineryproject.inventory.dto.InventoryDto.MovementResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Stok verisi. Tüm sorgular RLS altında çalışır (lokasyon/hareketler şube grubu kapsamında).
 * Bakiye yalnız POSTED hareketlerden hesaplanır (vet.v_inventory_on_hand); negatif stok engeli DB tetikleyicisindedir.
 */
@Repository
public class InventoryJdbcRepository {

    /** Her şubenin stok hareketlerinin yazıldığı varsayılan lokasyon kodu. */
    public static final String DEFAULT_LOCATION_CODE = "MAIN";

    private static final RowMapper<MovementResponse> MOVEMENT = (rs, n) -> new MovementResponse(
            rs.getObject("id", UUID.class), rs.getObject("transaction_id", UUID.class),
            rs.getString("transaction_no"), rs.getString("transaction_type"), rs.getObject("clinic_id", UUID.class),
            rs.getObject("product_id", UUID.class), rs.getString("product_name"), rs.getObject("lot_id", UUID.class),
            rs.getString("lot_number"), rs.getBigDecimal("quantity"), rs.getString("reference_type"),
            rs.getObject("reference_id", UUID.class), rs.getString("notes"), rs.getObject("created_by", UUID.class),
            rs.getString("created_by_name"), rs.getObject("occurred_at", OffsetDateTime.class));

    private final JdbcTemplate jdbc;

    public InventoryJdbcRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // ---------------------------------------------------------------- ürün / lokasyon / lot

    public Optional<StockProduct> product(UUID productId) {
        return jdbc.query("""
                select id, name, is_active, is_stock_item, tracks_lot, minimum_stock, purchase_price
                from vet.products where id = ?
                """, (rs, n) -> new StockProduct(rs.getObject("id", UUID.class), rs.getString("name"),
                rs.getBoolean("is_active"), rs.getBoolean("is_stock_item"), rs.getBoolean("tracks_lot"),
                rs.getBigDecimal("minimum_stock"), rs.getBigDecimal("purchase_price")), productId).stream().findFirst();
    }

    public Optional<UUID> defaultLocation(UUID clinicId) {
        return jdbc.query("select id from vet.inventory_locations where clinic_id = ? and code = ?",
                (rs, n) -> rs.getObject("id", UUID.class), clinicId, DEFAULT_LOCATION_CODE).stream().findFirst();
    }

    /** Şubenin varsayılan lokasyonunu ilk stok işleminde oluşturur; eşzamanlı ilk işlemler çakışmaz. */
    public UUID ensureDefaultLocation(UUID orgId, UUID clinicId) {
        return defaultLocation(clinicId).orElseGet(() -> {
            jdbc.update("""
                    insert into vet.inventory_locations (organization_id, clinic_id, code, name, location_type)
                    values (?, ?, ?, 'Ana depo', 'STOCKROOM')
                    on conflict (organization_id, clinic_id, code) do nothing
                    """, orgId, clinicId, DEFAULT_LOCATION_CODE);
            return defaultLocation(clinicId).orElseThrow();
        });
    }

    public boolean sameClinicGroup(UUID orgId, UUID clinicA, UUID clinicB) {
        Boolean same = jdbc.queryForObject("select vet.clinics_in_same_group(?, ?, ?)", Boolean.class, orgId, clinicA,
                clinicB);
        return Boolean.TRUE.equals(same);
    }

    public Optional<LotRow> findLot(UUID productId, String lotNumber) {
        return jdbc.query("""
                select id, lot_number, expires_at from vet.inventory_lots where product_id = ? and lot_number = ?
                """, (rs, n) -> new LotRow(rs.getObject("id", UUID.class), rs.getString("lot_number"),
                rs.getObject("expires_at", LocalDate.class)), productId, lotNumber).stream().findFirst();
    }

    /** Lot organizasyon genelidir (ürün + lot no tekil); transferde aynı lot kaydı hedef şubede de kullanılır. */
    public LotRow ensureLot(UUID orgId, UUID productId, String lotNumber, LocalDate expiresAt, BigDecimal unitCost) {
        jdbc.update("""
                insert into vet.inventory_lots (organization_id, product_id, lot_number, expires_at, unit_cost)
                values (?, ?, ?, ?, ?)
                on conflict (organization_id, product_id, lot_number) do nothing
                """, orgId, productId, lotNumber, expiresAt, unitCost);
        return findLot(productId, lotNumber).orElseThrow();
    }

    // ---------------------------------------------------------------- hareket yazma

    public void insertTransaction(UUID id, UUID orgId, UUID locationId, String transactionNo, String type,
                                  String referenceType, UUID referenceId, String notes, UUID userId) {
        jdbc.update("""
                insert into vet.inventory_transactions (id, organization_id, location_id, transaction_no,
                                                        transaction_type, status, reference_type, reference_id,
                                                        notes, created_by)
                values (?, ?, ?, ?, ?, 'DRAFT', ?, ?, ?, ?)
                """, id, orgId, locationId, transactionNo, type, referenceType, referenceId, notes, userId);
    }

    public void insertLine(UUID orgId, UUID transactionId, UUID productId, UUID lotId, String direction,
                           BigDecimal quantity, BigDecimal unitCost) {
        jdbc.update("""
                insert into vet.inventory_transaction_lines (organization_id, transaction_id, product_id, lot_id,
                                                             direction, quantity, unit_cost)
                values (?, ?, ?, ?, ?, ?, ?)
                """, orgId, transactionId, productId, lotId, direction, quantity, unitCost);
    }

    /** DRAFT → POSTED. Stok guard tetikleyicisi burada çalışır (advisory lock + yetersiz stokta 23514). */
    public void post(UUID transactionId, UUID userId) {
        jdbc.update("""
                update vet.inventory_transactions set status = 'POSTED', posted_by = ?, posted_at = now()
                where id = ?
                """, userId, transactionId);
    }

    // ---------------------------------------------------------------- bakiye

    public BigDecimal onHand(UUID locationId, UUID productId) {
        BigDecimal v = jdbc.queryForObject("""
                select coalesce(sum(quantity_on_hand), 0) from vet.v_inventory_on_hand
                where location_id = ? and product_id = ?
                """, BigDecimal.class, locationId, productId);
        return v == null ? BigDecimal.ZERO : v;
    }

    /** FEFO adayları: lokasyondaki lot bazlı bakiyeler (lotsuz stok lotId = null). */
    public List<StockBucket> buckets(UUID locationId, UUID productId) {
        return jdbc.query("""
                select v.lot_id, lot.lot_number, lot.expires_at, lot.received_at, v.quantity_on_hand
                from vet.v_inventory_on_hand v
                left join vet.inventory_lots lot on lot.organization_id = v.organization_id and lot.id = v.lot_id
                where v.location_id = ? and v.product_id = ? and v.quantity_on_hand > 0
                """, (rs, n) -> new StockBucket(rs.getObject("lot_id", UUID.class), rs.getString("lot_number"),
                rs.getObject("expires_at", LocalDate.class), rs.getObject("received_at", OffsetDateTime.class),
                rs.getBigDecimal("quantity_on_hand")), locationId, productId);
    }

    /**
     * Şubelerdeki stok durumu, şube × ürün × lot satırları. Şubede hareketi olan veya minimum stoğu tanımlı
     * aktif stok ürünleri döner; hiç hareketi olmayan ürünlerde lot alanları ve qty null'dır.
     */
    public List<OnHandRow> clinicStock(Collection<UUID> clinicIds) {
        String clinics = uuidArray(clinicIds);
        return jdbc.query("""
                with oh as (
                    select loc.clinic_id, v.product_id, v.lot_id, sum(v.quantity_on_hand) as qty
                    from vet.v_inventory_on_hand v
                    join vet.inventory_locations loc on loc.organization_id = v.organization_id and loc.id = v.location_id
                    where loc.clinic_id = any (?::uuid[])
                    group by loc.clinic_id, v.product_id, v.lot_id
                ), c as (select unnest(?::uuid[]) as clinic_id)
                select c.clinic_id, p.id as product_id, p.name, p.product_type, p.barcode, p.base_unit,
                       p.minimum_stock, p.tracks_lot, oh.lot_id, lot.lot_number, lot.expires_at, oh.qty
                from c
                cross join vet.products p
                left join oh on oh.clinic_id = c.clinic_id and oh.product_id = p.id
                left join vet.inventory_lots lot on lot.organization_id = p.organization_id and lot.id = oh.lot_id
                where p.is_active and p.is_stock_item and (oh.product_id is not null or p.minimum_stock > 0)
                order by c.clinic_id, p.name, p.id, lot.expires_at nulls last, lot.lot_number nulls last
                """, (rs, n) -> new OnHandRow(rs.getObject("clinic_id", UUID.class),
                rs.getObject("product_id", UUID.class), rs.getString("name"), rs.getString("product_type"),
                rs.getString("barcode"), rs.getString("base_unit"), rs.getBigDecimal("minimum_stock"),
                rs.getBoolean("tracks_lot"), rs.getObject("lot_id", UUID.class), rs.getString("lot_number"),
                rs.getObject("expires_at", LocalDate.class), rs.getBigDecimal("qty")), clinics, clinics);
    }

    // ---------------------------------------------------------------- hareket geçmişi

    private static final String MOVEMENT_FROM = """
            from vet.inventory_transactions t
            join vet.inventory_locations loc on loc.organization_id = t.organization_id and loc.id = t.location_id
            join vet.inventory_transaction_lines l on l.organization_id = t.organization_id and l.transaction_id = t.id
            """;

    public List<MovementResponse> movements(MovementFilter f, int limit, long offset) {
        List<Object> args = new ArrayList<>();
        String where = movementWhere(f, args);
        args.add(limit);
        args.add(offset);
        return jdbc.query("""
                select l.id, t.id as transaction_id, t.transaction_no, t.transaction_type, loc.clinic_id,
                       l.product_id, p.name as product_name, l.lot_id, lot.lot_number,
                       case when l.direction = 'IN' then l.quantity else -l.quantity end as quantity,
                       t.reference_type, t.reference_id, t.notes, t.created_by, u.display_name as created_by_name,
                       t.occurred_at
                """ + MOVEMENT_FROM + """
                join vet.products p on p.organization_id = l.organization_id and p.id = l.product_id
                left join vet.inventory_lots lot on lot.organization_id = l.organization_id and lot.id = l.lot_id
                left join vet.app_users u on u.organization_id = t.organization_id and u.id = t.created_by
                """ + where + " order by t.occurred_at desc, t.id, l.created_at, l.id limit ? offset ?",
                MOVEMENT, args.toArray());
    }

    public long countMovements(MovementFilter f) {
        List<Object> args = new ArrayList<>();
        String where = movementWhere(f, args);
        Long n = jdbc.queryForObject("select count(*) " + MOVEMENT_FROM + where, Long.class, args.toArray());
        return n == null ? 0 : n;
    }

    private static String movementWhere(MovementFilter f, List<Object> args) {
        StringBuilder sb = new StringBuilder(" where t.status = 'POSTED' and loc.clinic_id = any (?::uuid[])");
        args.add(uuidArray(f.clinicIds()));
        if (f.productId() != null) {
            sb.append(" and l.product_id = ?");
            args.add(f.productId());
        }
        if (f.type() != null) {
            sb.append(" and t.transaction_type = ?");
            args.add(f.type());
        }
        if (f.from() != null) {
            sb.append(" and t.occurred_at >= (?::date)::timestamp at time zone 'Europe/Istanbul'");
            args.add(f.from().toString());
        }
        if (f.to() != null) {
            sb.append(" and t.occurred_at < (?::date + 1)::timestamp at time zone 'Europe/Istanbul'");
            args.add(f.to().toString());
        }
        return sb.toString();
    }

    private static String uuidArray(Collection<UUID> ids) {
        return ids.stream().map(UUID::toString).collect(Collectors.joining(",", "{", "}"));
    }

    // ---------------------------------------------------------------- satır kayıtları

    public record StockProduct(UUID id, String name, boolean active, boolean stockItem, boolean tracksLot,
                               BigDecimal minimumStock, BigDecimal purchasePrice) {
    }

    public record LotRow(UUID id, String lotNumber, LocalDate expiresAt) {
    }

    public record OnHandRow(UUID clinicId, UUID productId, String name, String productType, String barcode,
                            String baseUnit, BigDecimal minimumStock, boolean tracksLot, UUID lotId,
                            String lotNumber, LocalDate expiresAt, BigDecimal quantity) {
    }

    public record MovementFilter(Collection<UUID> clinicIds, UUID productId, String type, LocalDate from,
                                 LocalDate to) {
    }
}
