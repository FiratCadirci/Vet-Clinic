package org.codeflow.veterineryproject.vaccine.repository;

import org.codeflow.veterineryproject.vaccine.dto.DueVaccineResponse;
import org.codeflow.veterineryproject.vaccine.dto.ProtocolDoseResponse;
import org.codeflow.veterineryproject.vaccine.dto.ProtocolResponse;
import org.codeflow.veterineryproject.vaccine.dto.VaccineRecordResponse;
import org.codeflow.veterineryproject.vaccine.factory.VaccineScheduleFactory.AnimalProfile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.Date;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Tüm sorgular RLS altında çalışır. */
@Repository
public class VaccineJdbcRepository {

    private static final String PROTOCOL_SELECT = """
            select p.id, p.code, p.name, p.species_id, s.code as species_code, p.version_no, p.status,
                   p.booster_interval_days, p.notes, p.approved_by, p.approved_at
            from vet.vaccine_protocols p join vet.species s on s.id = p.species_id
            """;

    private static final RowMapper<ProtocolResponse> PROTOCOL_MAPPER = (rs, i) -> new ProtocolResponse(
            rs.getObject("id", UUID.class), rs.getString("code"), rs.getString("name"), rs.getObject("species_id", UUID.class),
            rs.getString("species_code"), rs.getInt("version_no"), rs.getString("status"),
            rs.getObject("booster_interval_days", Integer.class), rs.getString("notes"),
            rs.getObject("approved_by", UUID.class), rs.getObject("approved_at", OffsetDateTime.class), List.of());

    private static final String RECORD_COLUMNS = """
            id, animal_id, encounter_id, clinic_id, protocol_dose_id, vaccine_name, batch_number, administered_at,
            administered_by, dose, dose_unit, route, administration_site, certificate_no, notes, next_due_at,
            next_due_source, created_by, created_at
            """;

    private static final RowMapper<VaccineRecordResponse> RECORD_MAPPER = (rs, i) -> new VaccineRecordResponse(
            rs.getObject("id", UUID.class), rs.getObject("animal_id", UUID.class), rs.getObject("encounter_id", UUID.class),
            rs.getObject("clinic_id", UUID.class), rs.getObject("protocol_dose_id", UUID.class), rs.getString("vaccine_name"),
            rs.getString("batch_number"), rs.getObject("administered_at", OffsetDateTime.class),
            rs.getObject("administered_by", UUID.class), rs.getObject("dose", BigDecimal.class), rs.getString("dose_unit"),
            rs.getString("route"), rs.getString("administration_site"), rs.getString("certificate_no"), rs.getString("notes"),
            rs.getObject("next_due_at", LocalDate.class), rs.getString("next_due_source"), rs.getObject("created_by", UUID.class),
            rs.getObject("created_at", OffsetDateTime.class));

    private final JdbcTemplate jdbc;

    public VaccineJdbcRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public boolean speciesExists(UUID speciesId) {
        Integer n = jdbc.queryForObject("select count(*) from vet.species where id = ?", Integer.class, speciesId);
        return n != null && n > 0;
    }

    public int nextVersion(String code) {
        Integer v = jdbc.queryForObject("select coalesce(max(version_no), 0) + 1 from vet.vaccine_protocols where code = ?", Integer.class, code);
        return v == null ? 1 : v;
    }

    public void insertProtocol(UUID id, UUID organizationId, String code, String name, UUID speciesId, int version,
                               Integer booster, String notes) {
        jdbc.update("""
                insert into vet.vaccine_protocols (id, organization_id, code, name, species_id, version_no, status, booster_interval_days, notes)
                values (?, ?, ?, ?, ?, ?, 'DRAFT', ?, ?)
                """, id, organizationId, code, name, speciesId, version, booster, notes);
    }

    public void insertDose(UUID organizationId, UUID protocolId, int doseNumber, String vaccineName, Integer age, Integer interval, String notes) {
        jdbc.update("""
                insert into vet.vaccine_protocol_doses (organization_id, protocol_id, dose_number, vaccine_name, recommended_age_days,
                                                        interval_from_previous_days, notes)
                values (?, ?, ?, ?, ?, ?, ?)
                """, organizationId, protocolId, doseNumber, vaccineName, age, interval, notes);
    }

    public Optional<ProtocolResponse> findProtocol(UUID id) {
        return jdbc.query(PROTOCOL_SELECT + " where p.id = ?", PROTOCOL_MAPPER, id).stream().findFirst().map(this::withDoses);
    }

    public Optional<ProtocolResponse> findProtocolByDose(UUID doseId) {
        List<UUID> ids = jdbc.queryForList("select protocol_id from vet.vaccine_protocol_doses where id = ?", UUID.class, doseId);
        return ids.stream().findFirst().flatMap(this::findProtocol);
    }

    public List<ProtocolResponse> findProtocols(UUID speciesId, String status, int limit, long offset) {
        List<Object> args = new ArrayList<>();
        String where = protocolWhere(speciesId, status, args);
        args.add(limit);
        args.add(offset);
        return jdbc.query(PROTOCOL_SELECT + where + " order by p.code, p.version_no desc limit ? offset ?", PROTOCOL_MAPPER, args.toArray())
                .stream().map(this::withDoses).toList();
    }

    public long countProtocols(UUID speciesId, String status) {
        List<Object> args = new ArrayList<>();
        String where = protocolWhere(speciesId, status, args);
        Long n = jdbc.queryForObject("select count(*) from vet.vaccine_protocols p" + where, Long.class, args.toArray());
        return n == null ? 0 : n;
    }

    public List<ProtocolResponse> findApprovedForSpecies(UUID speciesId) {
        return jdbc.query(PROTOCOL_SELECT + " where p.status = 'APPROVED' and p.species_id = ? order by p.code", PROTOCOL_MAPPER, speciesId)
                .stream().map(this::withDoses).toList();
    }

    private String protocolWhere(UUID speciesId, String status, List<Object> args) {
        StringBuilder sql = new StringBuilder(" where 1 = 1");
        if (speciesId != null) {
            sql.append(" and p.species_id = ?");
            args.add(speciesId);
        }
        if (status != null) {
            sql.append(" and p.status = ?");
            args.add(status);
        }
        return sql.toString();
    }

    public void approve(UUID id, UUID veterinarianId) {
        jdbc.update("""
                update vet.vaccine_protocols old set status = 'RETIRED', updated_at = now()
                from vet.vaccine_protocols cur
                where cur.id = ? and old.code = cur.code and old.status = 'APPROVED' and old.id <> cur.id
                """, id);
        jdbc.update("update vet.vaccine_protocols set status = 'APPROVED', approved_by = ?, approved_at = now(), updated_at = now() where id = ?",
                veterinarianId, id);
    }

    public void retire(UUID id) {
        jdbc.update("update vet.vaccine_protocols set status = 'RETIRED', updated_at = now() where id = ?", id);
    }

    private ProtocolResponse withDoses(ProtocolResponse protocol) {
        List<ProtocolDoseResponse> doses = jdbc.query("""
                select id, dose_number, vaccine_name, recommended_age_days, interval_from_previous_days, notes
                from vet.vaccine_protocol_doses where protocol_id = ? order by dose_number
                """, (rs, i) -> new ProtocolDoseResponse(rs.getObject("id", UUID.class), rs.getInt("dose_number"),
                rs.getString("vaccine_name"), rs.getObject("recommended_age_days", Integer.class),
                rs.getObject("interval_from_previous_days", Integer.class), rs.getString("notes")), protocol.id());
        return protocol.withDoses(doses);
    }

    public Optional<AnimalProfile> findAnimalProfile(UUID animalId) {
        return jdbc.query("""
                select a.id, s.code, a.birth_date from vet.animals a join vet.species s on s.id = a.species_id where a.id = ?
                """, (rs, i) -> new AnimalProfile(rs.getObject("id", UUID.class), rs.getString("code"),
                rs.getObject("birth_date", LocalDate.class)), animalId).stream().findFirst();
    }

    public Optional<UUID> findAnimalSpeciesId(UUID animalId) {
        return jdbc.queryForList("select species_id from vet.animals where id = ?", UUID.class, animalId).stream().findFirst();
    }

    public UUID insertRecord(UUID organizationId, UUID animalId, UUID encounterId, UUID clinicId, UUID protocolDoseId,
                             String vaccineName, String batchNumber, OffsetDateTime administeredAt, UUID administeredBy,
                             BigDecimal dose, String doseUnit, String route, String site, String certificateNo, String notes,
                             LocalDate nextDueAt, String nextDueSource, UUID createdBy) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                insert into vet.vaccine_records (id, organization_id, animal_id, encounter_id, clinic_id, protocol_dose_id,
                    vaccine_name, batch_number, administered_at, administered_by, dose, dose_unit, route, administration_site,
                    certificate_no, notes, next_due_at, next_due_source, created_by)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, id, organizationId, animalId, encounterId, clinicId, protocolDoseId, vaccineName, batchNumber,
                administeredAt, administeredBy, dose, doseUnit, route, site, certificateNo, notes,
                nextDueAt == null ? null : Date.valueOf(nextDueAt), nextDueSource, createdBy);
        return id;
    }

    public Optional<VaccineRecordResponse> findRecord(UUID id) {
        return jdbc.query("select " + RECORD_COLUMNS + " from vet.vaccine_records where id = ?", RECORD_MAPPER, id).stream().findFirst();
    }

    public List<VaccineRecordResponse> findRecordsByAnimal(UUID animalId, int limit, long offset) {
        return jdbc.query("select " + RECORD_COLUMNS + " from vet.vaccine_records where animal_id = ? "
                + "order by administered_at desc, id limit ? offset ?", RECORD_MAPPER, animalId, limit, offset);
    }

    public List<VaccineRecordResponse> findRecordsByEncounter(UUID encounterId) {
        return jdbc.query("select " + RECORD_COLUMNS + " from vet.vaccine_records where encounter_id = ? "
                + "order by administered_at, id", RECORD_MAPPER, encounterId);
    }

    public long countRecordsByAnimal(UUID animalId) {
        Long n = jdbc.queryForObject("select count(*) from vet.vaccine_records where animal_id = ?", Long.class, animalId);
        return n == null ? 0 : n;
    }

    public void deleteRecord(UUID id) {
        jdbc.update("delete from vet.vaccine_records where id = ?", id);
    }

    /** Aşı adı başına en son kayıt değerlendirilir; yeni doz kaydı eski vadeyi kapatır. */
    private static final String DUE_BASE = """
            select l.id, l.animal_id, a.name as animal_name, a.patient_no, l.clinic_id, l.vaccine_name, l.administered_at,
                   l.next_due_at, (current_date - l.next_due_at) as days_overdue
            from (
                select distinct on (vr.animal_id, lower(vr.vaccine_name)) vr.*
                from vet.vaccine_records vr
                order by vr.animal_id, lower(vr.vaccine_name), vr.administered_at desc, vr.created_at desc
            ) l
            join vet.animals a on a.organization_id = l.organization_id and a.id = l.animal_id
            where a.deceased_at is null and l.next_due_at is not null
            """;

    public List<DueVaccineResponse> findDue(UUID animalId, String status, int days, int limit, long offset) {
        List<Object> args = new ArrayList<>();
        String filter = dueFilter(animalId, status, days, args);
        args.add(limit);
        args.add(offset);
        return jdbc.query(DUE_BASE + filter + " order by l.next_due_at, l.id limit ? offset ?", (rs, i) -> {
            long overdue = rs.getLong("days_overdue");
            return new DueVaccineResponse(rs.getObject("id", UUID.class), rs.getObject("animal_id", UUID.class),
                    rs.getString("animal_name"), rs.getString("patient_no"), rs.getObject("clinic_id", UUID.class),
                    rs.getString("vaccine_name"), rs.getObject("administered_at", OffsetDateTime.class),
                    rs.getObject("next_due_at", LocalDate.class), overdue > 0 ? "OVERDUE" : "UPCOMING", overdue);
        }, args.toArray());
    }

    public long countDue(UUID animalId, String status, int days) {
        List<Object> args = new ArrayList<>();
        String filter = dueFilter(animalId, status, days, args);
        Long n = jdbc.queryForObject("select count(*) from (" + DUE_BASE + filter + ") d", Long.class, args.toArray());
        return n == null ? 0 : n;
    }

    private String dueFilter(UUID animalId, String status, int days, List<Object> args) {
        StringBuilder sql = new StringBuilder();
        if (animalId != null) {
            sql.append(" and l.animal_id = ?");
            args.add(animalId);
        }
        switch (status) {
            case "OVERDUE" -> sql.append(" and l.next_due_at < current_date");
            case "UPCOMING" -> {
                sql.append(" and l.next_due_at >= current_date and l.next_due_at <= current_date + ?");
                args.add(days);
            }
            default -> {
                sql.append(" and l.next_due_at <= current_date + ?");
                args.add(days);
            }
        }
        return sql.toString();
    }
}
