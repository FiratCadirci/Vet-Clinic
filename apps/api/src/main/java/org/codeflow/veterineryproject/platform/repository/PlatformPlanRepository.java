package org.codeflow.veterineryproject.platform.repository;

import org.codeflow.veterineryproject.platform.dto.PlatformDto.CreatePlanRequest;
import org.codeflow.veterineryproject.platform.dto.PlatformDto.PlanResponse;
import org.codeflow.veterineryproject.platform.dto.PlatformDto.UpdatePlanRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Paket kataloğu yönetimi. Yazmalar yalnız platform bağlamında geçer (V29 politikaları) ve V6 log'una düşer. */
@Repository
public class PlatformPlanRepository {

    private static final String SELECT = """
            select p.id, p.code, p.name, p.billing_period, p.price, p.currency, p.max_branches, p.max_users,
                   p.is_active, p.created_at, p.updated_at,
                   (select count(*) from vet.organization_subscriptions s where s.plan_id = p.id) as subscription_count
            from vet.subscription_plans p
            """;

    private static final RowMapper<PlanResponse> PLAN = (rs, i) -> new PlanResponse(rs.getObject("id", UUID.class),
            rs.getString("code"), rs.getString("name"), rs.getString("billing_period"), rs.getBigDecimal("price"),
            rs.getString("currency"), rs.getInt("max_branches"), rs.getInt("max_users"), rs.getBoolean("is_active"),
            rs.getInt("subscription_count"), rs.getObject("created_at", OffsetDateTime.class),
            rs.getObject("updated_at", OffsetDateTime.class));

    private final JdbcTemplate jdbc;

    public PlatformPlanRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<PlanResponse> findAll() {
        return jdbc.query(SELECT + " order by p.name, p.billing_period, p.code", PLAN);
    }

    public Optional<PlanResponse> findById(UUID id) {
        return jdbc.query(SELECT + " where p.id = ?", PLAN, id).stream().findFirst();
    }

    public boolean codeExists(String code) {
        Integer count = jdbc.queryForObject("select count(*) from vet.subscription_plans where code = ?", Integer.class, code);
        return count != null && count > 0;
    }

    public UUID insert(CreatePlanRequest r, boolean active) {
        return jdbc.queryForObject("""
                insert into vet.subscription_plans (code, name, billing_period, price, max_branches, max_users, is_active)
                values (?, ?, ?, ?, ?, ?, ?)
                returning id
                """, UUID.class, r.code(), r.name(), r.billingPeriod(), r.price(), r.maxBranches(), r.maxUsers(), active);
    }

    public int update(UUID id, UpdatePlanRequest r) {
        return jdbc.update("""
                update vet.subscription_plans
                set name = ?, price = ?, max_branches = ?, max_users = ?, is_active = ?, updated_at = now()
                where id = ?
                """, r.name(), r.price(), r.maxBranches(), r.maxUsers(), r.active(), id);
    }

    public int delete(UUID id) {
        return jdbc.update("delete from vet.subscription_plans where id = ?", id);
    }
}
