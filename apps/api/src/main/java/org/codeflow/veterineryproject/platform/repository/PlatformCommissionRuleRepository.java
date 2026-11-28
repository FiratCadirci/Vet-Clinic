package org.codeflow.veterineryproject.platform.repository;

import org.codeflow.veterineryproject.platform.dto.PlatformDto.CommissionRuleResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Komisyon kuralları (platform düzeyi). Kuralı uygulayan ve oranı kayda kopyalayan fatura trigger'ıdır
 * (vet.post_invoice_inventory_and_commission); burada yalnız kural yönetimi yapılır. Klinik bu tabloya yazamaz (V29).
 */
@Repository
public class PlatformCommissionRuleRepository {

    private static final String SELECT = """
            select id, code, name, product_type, rate_percent, valid_from, valid_to, is_active, created_at, updated_at,
                   (is_active and valid_from <= now() and (valid_to is null or valid_to > now())) as in_effect
            from vet.commission_rules
            """;

    private static final RowMapper<CommissionRuleResponse> RULE = (rs, i) -> new CommissionRuleResponse(
            rs.getObject("id", UUID.class), rs.getString("code"), rs.getString("name"), rs.getString("product_type"),
            rs.getBigDecimal("rate_percent"), rs.getObject("valid_from", OffsetDateTime.class),
            rs.getObject("valid_to", OffsetDateTime.class), rs.getBoolean("is_active"), rs.getBoolean("in_effect"),
            rs.getObject("created_at", OffsetDateTime.class), rs.getObject("updated_at", OffsetDateTime.class));

    /** Yeni kural alanları. */
    public record NewRule(String code, String name, String productType, BigDecimal ratePercent,
                          OffsetDateTime validFrom, OffsetDateTime validTo, boolean active, UUID createdByAdminId) {
    }

    private final JdbcTemplate jdbc;

    public PlatformCommissionRuleRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<CommissionRuleResponse> findAll() {
        return jdbc.query(SELECT + " order by product_type, valid_from desc, created_at desc", RULE);
    }

    public Optional<CommissionRuleResponse> findById(UUID id) {
        return jdbc.query(SELECT + " where id = ?", RULE, id).stream().findFirst();
    }

    public UUID insert(NewRule r) {
        return jdbc.queryForObject("""
                insert into vet.commission_rules (code, name, product_type, rate_percent, valid_from, valid_to, is_active,
                                                  created_by_admin_id)
                values (?, ?, ?, ?, ?, ?, ?, ?)
                returning id
                """, UUID.class, r.code(), r.name(), r.productType(), r.ratePercent(), r.validFrom(), r.validTo(),
                r.active(), r.createdByAdminId());
    }

    public int update(UUID id, String name, BigDecimal ratePercent, OffsetDateTime validFrom, OffsetDateTime validTo,
                      boolean active) {
        return jdbc.update("""
                update vet.commission_rules
                set name = ?, rate_percent = ?, valid_from = ?, valid_to = ?, is_active = ?,
                    updated_at = now()
                where id = ?
                """, name, ratePercent, validFrom, validTo, active, id);
    }
}
