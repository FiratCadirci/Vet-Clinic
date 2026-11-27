package org.codeflow.veterineryproject.subscription.repository;

import org.codeflow.veterineryproject.subscription.dto.PlanDto.PlanOption;
import org.codeflow.veterineryproject.subscription.dto.PlanDto.PlanUsage;
import org.codeflow.veterineryproject.subscription.service.PlanLimitDecision;
import org.codeflow.veterineryproject.subscription.service.PlanResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/** Paket limitleri ve kullanım; sayımlar organizasyon geneli olduğu için SECURITY DEFINER fonksiyonlar üzerinden yapılır. */
@Repository
public class PlanLimitRepository {

    private final JdbcTemplate jdbc;

    public PlanLimitRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Organizasyon satırını transaction sonuna kadar kilitler (eşzamanlı ekleme işlemleri sıraya girer). */
    public PlanLimitDecision lockAndCheck(PlanResource resource) {
        return jdbc.queryForObject("select * from vet.check_plan_limit(?)", (rs, i) -> new PlanLimitDecision(
                rs.getBoolean("allowed"),
                rs.getObject("max_allowed", Integer.class),
                rs.getObject("used", Integer.class),
                rs.getString("plan_name"),
                rs.getString("reason")
        ), resource.name());
    }

    public PlanUsage currentUsage() {
        return jdbc.queryForObject("select * from vet.current_plan_usage()",
                (rs, i) -> new PlanUsage(rs.getInt("active_branches"), rs.getInt("active_users")));
    }

    /** Paket kataloğu platform geneli bir referans tablosudur (RLS yok); kayıt ekranında oturumsuz listelenir. */
    public List<PlanOption> activePlans() {
        return jdbc.query("""
                select id, code, name, billing_period, price, currency, max_branches, max_users
                from vet.subscription_plans
                where is_active
                order by price, code
                """, (rs, i) -> new PlanOption(
                rs.getObject("id", UUID.class),
                rs.getString("code"),
                rs.getString("name"),
                rs.getString("billing_period"),
                rs.getObject("price", BigDecimal.class),
                rs.getString("currency"),
                rs.getInt("max_branches"),
                rs.getInt("max_users")
        ));
    }
}
