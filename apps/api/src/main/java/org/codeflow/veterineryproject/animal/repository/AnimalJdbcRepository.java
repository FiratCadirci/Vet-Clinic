package org.codeflow.veterineryproject.animal.repository;

import org.codeflow.veterineryproject.animal.dto.AnimalOwnerResponse;
import org.codeflow.veterineryproject.animal.dto.AnimalResponse;
import org.codeflow.veterineryproject.animal.dto.BreedResponse;
import org.codeflow.veterineryproject.animal.dto.SpeciesResponse;
import org.codeflow.veterineryproject.common.sql.SqlLike;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.Date;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Tüm sorgular RLS altında çalışır: yalnızca çağıranın klinik grubuna açılmış hastalar görünür. */
@Repository
public class AnimalJdbcRepository {

    private static final String SELECT = """
            select a.id, a.patient_no, a.name, a.species_id, s.name as species_name, a.breed_id, b.name as breed_name,
                   a.sex, a.birth_date, a.birth_date_estimated, a.color, a.distinguishing_marks, a.sterilized,
                   a.sterilization_date, a.deceased_at, a.profile_notes, a.created_at, a.updated_at
            from vet.animals a
            join vet.species s on s.id = a.species_id
            left join vet.breeds b on b.id = a.breed_id
            """;

    private static final RowMapper<AnimalResponse> MAPPER = (rs, i) -> new AnimalResponse(
            rs.getObject("id", UUID.class),
            rs.getString("patient_no"),
            rs.getString("name"),
            rs.getObject("species_id", UUID.class),
            rs.getString("species_name"),
            rs.getObject("breed_id", UUID.class),
            rs.getString("breed_name"),
            rs.getString("sex"),
            rs.getObject("birth_date", LocalDate.class),
            rs.getBoolean("birth_date_estimated"),
            rs.getString("color"),
            rs.getString("distinguishing_marks"),
            rs.getBoolean("sterilized"),
            rs.getObject("sterilization_date", LocalDate.class),
            rs.getObject("deceased_at", LocalDate.class),
            rs.getString("profile_notes"),
            rs.getObject("created_at", OffsetDateTime.class),
            rs.getObject("updated_at", OffsetDateTime.class),
            List.of()
    );

    private final JdbcTemplate jdbc;

    public AnimalJdbcRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Sayaç satırı bu UPDATE ile transaction sonuna kadar kilitlenir; eşzamanlı kayıtlar sıraya girer. */
    public long nextPatientSequence(UUID organizationId) {
        Long value = jdbc.queryForObject("""
                insert into vet.patient_number_counters (organization_id, last_value) values (?, 1)
                on conflict (organization_id)
                do update set last_value = vet.patient_number_counters.last_value + 1, updated_at = now()
                returning last_value
                """, Long.class, organizationId);
        return value == null ? 1 : value;
    }

    public void insert(UUID id, UUID organizationId, String patientNo, AnimalResponse v) {
        jdbc.update("""
                insert into vet.animals (id, organization_id, patient_no, name, species_id, breed_id, sex, birth_date,
                                         birth_date_estimated, color, distinguishing_marks, sterilized, sterilization_date,
                                         profile_notes)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, id, organizationId, patientNo, v.name(), v.speciesId(), v.breedId(), v.sex(), date(v.birthDate()),
                v.birthDateEstimated(), v.color(), v.distinguishingMarks(), v.sterilized(), date(v.sterilizationDate()),
                v.profileNotes());
    }

    public void update(UUID id, AnimalResponse v) {
        jdbc.update("""
                update vet.animals
                set name = ?, breed_id = ?, sex = ?, birth_date = ?, birth_date_estimated = ?, color = ?,
                    distinguishing_marks = ?, sterilized = ?, sterilization_date = ?, profile_notes = ?, updated_at = now()
                where id = ?
                """, v.name(), v.breedId(), v.sex(), date(v.birthDate()), v.birthDateEstimated(), v.color(),
                v.distinguishingMarks(), v.sterilized(), date(v.sterilizationDate()), v.profileNotes(), id);
    }

    public int markDeceased(UUID id, LocalDate deceasedAt) {
        return jdbc.update("update vet.animals set deceased_at = ?, updated_at = now() where id = ? and deceased_at is null",
                Date.valueOf(deceasedAt), id);
    }

    public Optional<AnimalResponse> findById(UUID id) {
        return jdbc.query(SELECT + " where a.id = ?", MAPPER, id).stream().findFirst();
    }

    public List<AnimalResponse> findByIdentifier(String value) {
        return jdbc.query(SELECT + " where exists (select 1 from vet.animal_identifiers ai where ai.animal_id = a.id"
                + " and upper(ai.identifier_value) = upper(?)) order by lower(a.name), a.id", MAPPER, value);
    }

    public List<AnimalResponse> search(String query, UUID speciesId, UUID clientId, int limit, long offset) {
        List<Object> args = new ArrayList<>();
        String where = searchWhere(query, speciesId, clientId, args);
        args.add(limit);
        args.add(offset);
        return jdbc.query(SELECT + where + " order by lower(a.name), a.id limit ? offset ?", MAPPER, args.toArray());
    }

    public long count(String query, UUID speciesId, UUID clientId) {
        List<Object> args = new ArrayList<>();
        String where = searchWhere(query, speciesId, clientId, args);
        Long total = jdbc.queryForObject("select count(*) from vet.animals a" + where, Long.class, args.toArray());
        return total == null ? 0 : total;
    }

    public boolean speciesActive(UUID speciesId) {
        return !jdbc.queryForList("select 1 from vet.species where id = ? and is_active", speciesId).isEmpty();
    }

    public boolean breedBelongsToSpecies(UUID breedId, UUID speciesId) {
        return !jdbc.queryForList("select 1 from vet.breeds where id = ? and species_id = ?", breedId, speciesId).isEmpty();
    }

    /** Müşteri RLS ile görünür değilse boş döner. */
    public Optional<Boolean> clientActive(UUID clientId) {
        return jdbc.query("select is_active from vet.clients where id = ?", (rs, i) -> rs.getBoolean(1), clientId)
                .stream().findFirst();
    }

    public void insertOwner(UUID organizationId, UUID animalId, UUID clientId, String relationshipType,
                            boolean primaryContact, boolean legalOwner) {
        jdbc.update("""
                insert into vet.animal_owners (organization_id, animal_id, client_id, relationship_type, is_primary_contact, is_legal_owner)
                values (?, ?, ?, ?, ?, ?)
                """, organizationId, animalId, clientId, relationshipType, primaryContact, legalOwner);
    }

    public boolean hasCurrentOwner(UUID animalId, UUID clientId) {
        return !jdbc.queryForList(
                "select 1 from vet.animal_owners where animal_id = ? and client_id = ? and valid_to is null",
                animalId, clientId).isEmpty();
    }

    public void clearPrimaryContact(UUID animalId) {
        jdbc.update("update vet.animal_owners set is_primary_contact = false where animal_id = ? and valid_to is null and is_primary_contact",
                animalId);
    }

    public Optional<AnimalOwnerResponse> findCurrentOwner(UUID animalId, UUID ownerId) {
        return jdbc.query(ownerSelect() + " where ao.animal_id = ? and ao.id = ? and ao.valid_to is null",
                OWNER_MAPPER, animalId, ownerId).stream().findFirst();
    }

    public int countCurrentOwners(UUID animalId) {
        Integer total = jdbc.queryForObject(
                "select count(*) from vet.animal_owners where animal_id = ? and valid_to is null", Integer.class, animalId);
        return total == null ? 0 : total;
    }

    public void setPrimaryContact(UUID ownerId) {
        jdbc.update("update vet.animal_owners set is_primary_contact = true where id = ?", ownerId);
    }

    public void endOwner(UUID ownerId) {
        jdbc.update("update vet.animal_owners set valid_to = greatest(valid_from, current_date) where id = ?", ownerId);
    }

    public Map<UUID, List<AnimalOwnerResponse>> findOwners(Collection<UUID> animalIds) {
        Map<UUID, List<AnimalOwnerResponse>> result = new LinkedHashMap<>();
        if (animalIds.isEmpty()) {
            return result;
        }
        String placeholders = String.join(",", animalIds.stream().map(x -> "?").toList());
        jdbc.query(ownerSelect() + " where ao.animal_id in (" + placeholders + ") order by ao.valid_from, ao.created_at, ao.id",
                rs -> {
                    result.computeIfAbsent(rs.getObject("animal_id", UUID.class), k -> new ArrayList<>())
                            .add(OWNER_MAPPER.mapRow(rs, 0));
                }, animalIds.toArray());
        return result;
    }

    public List<SpeciesResponse> findSpecies() {
        return jdbc.query("select id, code, name, scientific_name from vet.species where is_active order by name",
                (rs, i) -> new SpeciesResponse(rs.getObject("id", UUID.class), rs.getString("code"),
                        rs.getString("name"), rs.getString("scientific_name")));
    }

    public boolean speciesExists(UUID speciesId) {
        return !jdbc.queryForList("select 1 from vet.species where id = ?", speciesId).isEmpty();
    }

    public List<BreedResponse> findBreeds(UUID speciesId) {
        return jdbc.query("select id, species_id, name, is_mixed from vet.breeds where species_id = ? order by name",
                (rs, i) -> new BreedResponse(rs.getObject("id", UUID.class), rs.getObject("species_id", UUID.class),
                        rs.getString("name"), rs.getBoolean("is_mixed")), speciesId);
    }

    private static final RowMapper<AnimalOwnerResponse> OWNER_MAPPER = (rs, i) -> new AnimalOwnerResponse(
            rs.getObject("id", UUID.class),
            rs.getObject("client_id", UUID.class),
            rs.getString("client_name"),
            rs.getString("relationship_type"),
            rs.getBoolean("is_primary_contact"),
            rs.getBoolean("is_legal_owner"),
            rs.getObject("valid_from", LocalDate.class),
            rs.getObject("valid_to", LocalDate.class)
    );

    private String ownerSelect() {
        return """
                select ao.id, ao.animal_id, ao.client_id, c.display_name as client_name, ao.relationship_type,
                       ao.is_primary_contact, ao.is_legal_owner, ao.valid_from, ao.valid_to
                from vet.animal_owners ao
                join vet.clients c on c.organization_id = ao.organization_id and c.id = ao.client_id
                """;
    }

    private String searchWhere(String query, UUID speciesId, UUID clientId, List<Object> args) {
        StringBuilder where = new StringBuilder(" where true");
        if (query != null && !query.isBlank()) {
            String like = SqlLike.contains(query);
            where.append("""
                     and (lower(a.name) like ? or lower(a.patient_no) like ?
                          or exists (select 1 from vet.animal_owners ao
                                     join vet.clients c on c.organization_id = ao.organization_id and c.id = ao.client_id
                                     where ao.animal_id = a.id and ao.valid_to is null and lower(c.display_name) like ?)
                          or exists (select 1 from vet.animal_identifiers ai
                                     where ai.animal_id = a.id and lower(ai.identifier_value) like ?))
                    """);
            args.add(like);
            args.add(like);
            args.add(like);
            args.add(like);
        }
        if (speciesId != null) {
            where.append(" and a.species_id = ?");
            args.add(speciesId);
        }
        if (clientId != null) {
            where.append(" and exists (select 1 from vet.animal_owners ao where ao.animal_id = a.id and ao.client_id = ? and ao.valid_to is null)");
            args.add(clientId);
        }
        return where.toString();
    }

    private Date date(LocalDate value) {
        return value == null ? null : Date.valueOf(value);
    }
}
