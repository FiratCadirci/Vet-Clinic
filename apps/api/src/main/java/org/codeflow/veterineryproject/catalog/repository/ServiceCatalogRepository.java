package org.codeflow.veterineryproject.catalog.repository;

import org.codeflow.veterineryproject.catalog.dto.ServiceCatalogDto.ServiceRequest;
import org.codeflow.veterineryproject.catalog.dto.ServiceCatalogDto.ServiceResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Hizmet kataloğu organizasyon geneli tablodur (RLS: tenant_isolation). */
@Repository
public class ServiceCatalogRepository {

    private static final RowMapper<ServiceResponse> MAPPER = (rs, i) -> new ServiceResponse(
            rs.getObject("id", UUID.class),
            rs.getString("code"),
            rs.getString("name"),
            rs.getString("category"),
            rs.getObject("duration_minutes", Integer.class),
            rs.getObject("default_price", BigDecimal.class),
            rs.getObject("tax_rate", BigDecimal.class),
            rs.getBoolean("is_active")
    );

    private static final String SELECT = """
            select id, code, name, category, duration_minutes, default_price, tax_rate, is_active
            from vet.service_catalog
            """;

    private final JdbcTemplate jdbc;

    public ServiceCatalogRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<ServiceResponse> list(boolean includeInactive) {
        return jdbc.query(SELECT + (includeInactive ? "" : " where is_active") + " order by lower(name), id", MAPPER);
    }

    public Optional<ServiceResponse> find(UUID id) {
        return jdbc.query(SELECT + " where id = ?", MAPPER, id).stream().findFirst();
    }

    public void insert(UUID id, UUID organizationId, String code, ServiceRequest r, boolean active) {
        jdbc.update("""
                insert into vet.service_catalog (id, organization_id, code, name, category, default_price, tax_rate,
                                                 duration_minutes, is_active)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, id, organizationId, code, r.name(), r.category(), r.price(), r.taxRate(), r.durationMinutes(), active);
    }

    public int update(UUID id, String code, ServiceRequest r, boolean active) {
        return jdbc.update("""
                update vet.service_catalog
                set code = ?, name = ?, category = ?, default_price = ?, tax_rate = ?, duration_minutes = ?, is_active = ?
                where id = ?
                """, code, r.name(), r.category(), r.price(), r.taxRate(), r.durationMinutes(), active, id);
    }

    public int delete(UUID id) {
        return jdbc.update("delete from vet.service_catalog where id = ?", id);
    }
}
