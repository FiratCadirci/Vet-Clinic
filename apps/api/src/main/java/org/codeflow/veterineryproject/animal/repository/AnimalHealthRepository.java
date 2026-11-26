package org.codeflow.veterineryproject.animal.repository;

import org.codeflow.veterineryproject.animal.dto.AnimalHealthDto.AllergyResponse;
import org.codeflow.veterineryproject.animal.dto.AnimalHealthDto.IdentifierResponse;
import org.codeflow.veterineryproject.animal.dto.AnimalHealthDto.WeightResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class AnimalHealthRepository {

    private static final RowMapper<WeightResponse> WEIGHT = (rs, i) -> new WeightResponse(
            rs.getObject("id", UUID.class), rs.getBigDecimal("weight_kg"), rs.getObject("measured_at", OffsetDateTime.class),
            rs.getString("measurement_source"), rs.getString("note"), rs.getObject("recorded_by", UUID.class));

    private static final RowMapper<AllergyResponse> ALLERGY = (rs, i) -> new AllergyResponse(
            rs.getObject("id", UUID.class), rs.getString("substance"), rs.getString("reaction"), rs.getString("severity"),
            rs.getObject("onset_date", LocalDate.class), rs.getObject("resolved_at", LocalDate.class),
            rs.getBoolean("is_active"));

    private static final RowMapper<IdentifierResponse> IDENTIFIER = (rs, i) -> new IdentifierResponse(
            rs.getObject("id", UUID.class), rs.getString("identifier_type"), rs.getString("identifier_value"),
            rs.getObject("issued_at", LocalDate.class), rs.getObject("expires_at", LocalDate.class),
            rs.getString("issuing_authority"), rs.getBoolean("is_verified"));

    private final JdbcTemplate jdbc;

    public AnimalHealthRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public boolean animalVisible(UUID animalId) {
        return !jdbc.queryForList("select 1 from vet.animals where id = ?", animalId).isEmpty();
    }

    public List<WeightResponse> weights(UUID animalId) {
        return jdbc.query("select * from vet.animal_weights where animal_id = ? order by measured_at desc, created_at desc",
                WEIGHT, animalId);
    }

    public Optional<WeightResponse> latestWeight(UUID animalId) {
        return weights(animalId).stream().findFirst();
    }

    public UUID insertWeight(UUID orgId, UUID animalId, BigDecimal kg, OffsetDateTime measuredAt, String source,
                             String note, UUID userId) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                insert into vet.animal_weights (id, organization_id, animal_id, weight_kg, measured_at, measurement_source,
                                                note, recorded_by)
                values (?, ?, ?, ?, coalesce(?, now()), ?, ?, ?)
                """, id, orgId, animalId, kg, measuredAt, source, note, userId);
        return id;
    }

    public Optional<WeightResponse> weight(UUID id) {
        return jdbc.query("select * from vet.animal_weights where id = ?", WEIGHT, id).stream().findFirst();
    }

    public List<AllergyResponse> allergies(UUID animalId, boolean activeOnly) {
        return jdbc.query("select * from vet.animal_allergies where animal_id = ?"
                + (activeOnly ? " and is_active" : "") + " order by is_active desc, created_at desc", ALLERGY, animalId);
    }

    public UUID insertAllergy(UUID orgId, UUID animalId, String substance, String reaction, String severity,
                              LocalDate onset, UUID userId) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                insert into vet.animal_allergies (id, organization_id, animal_id, substance, reaction, severity, onset_date,
                                                  recorded_by)
                values (?, ?, ?, ?, ?, ?, ?, ?)
                """, id, orgId, animalId, substance, reaction, severity, onset, userId);
        return id;
    }

    public Optional<AllergyResponse> allergy(UUID animalId, UUID id) {
        return jdbc.query("select * from vet.animal_allergies where animal_id = ? and id = ?", ALLERGY, animalId, id)
                .stream().findFirst();
    }

    public void resolveAllergy(UUID id) {
        jdbc.update("update vet.animal_allergies set is_active = false, resolved_at = current_date where id = ?", id);
    }

    public List<IdentifierResponse> identifiers(UUID animalId) {
        return jdbc.query("select * from vet.animal_identifiers where animal_id = ? order by identifier_type, created_at",
                IDENTIFIER, animalId);
    }

    public UUID insertIdentifier(UUID orgId, UUID animalId, String type, String value, LocalDate issuedAt,
                                 LocalDate expiresAt, String authority) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                insert into vet.animal_identifiers (id, organization_id, animal_id, identifier_type, identifier_value,
                                                    issued_at, expires_at, issuing_authority)
                values (?, ?, ?, ?, ?, ?, ?, ?)
                """, id, orgId, animalId, type, value, issuedAt, expiresAt, authority);
        return id;
    }

    public Optional<IdentifierResponse> identifier(UUID id) {
        return jdbc.query("select * from vet.animal_identifiers where id = ?", IDENTIFIER, id).stream().findFirst();
    }
}
