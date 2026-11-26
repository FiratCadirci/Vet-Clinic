package org.codeflow.veterineryproject.encounter.repository;

import org.codeflow.veterineryproject.encounter.dto.AddendumResponse;
import org.codeflow.veterineryproject.encounter.dto.EncounterResponse;
import org.codeflow.veterineryproject.encounter.dto.EncounterStatus;
import org.codeflow.veterineryproject.encounter.dto.HistoryItemResponse;
import org.codeflow.veterineryproject.encounter.dto.TreatmentResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Tüm sorgular RLS altında çalışır: yalnızca çağıranın klinik grubundaki muayeneler görünür. */
@Repository
public class EncounterJdbcRepository {

    private static final String SELECT = """
            select e.id, e.encounter_no, e.animal_id, a.name as animal_name, a.patient_no, e.clinic_id, e.appointment_id,
                   e.attending_vet_id, e.encounter_type, e.status, e.started_at, e.ended_at, e.chief_complaint,
                   e.history_text, e.examination_summary, e.assessment_text, e.treatment_plan, e.vet_notes,
                   e.follow_up_at, e.created_by, e.created_at, e.updated_at
            from vet.encounters e
            join vet.animals a on a.organization_id = e.organization_id and a.id = e.animal_id
            """;

    private static final RowMapper<EncounterResponse> MAPPER = (rs, i) -> new EncounterResponse(
            rs.getObject("id", UUID.class),
            rs.getString("encounter_no"),
            rs.getObject("animal_id", UUID.class),
            rs.getString("animal_name"),
            rs.getString("patient_no"),
            rs.getObject("clinic_id", UUID.class),
            rs.getObject("appointment_id", UUID.class),
            rs.getObject("attending_vet_id", UUID.class),
            rs.getString("encounter_type"),
            EncounterStatus.valueOf(rs.getString("status")),
            rs.getObject("started_at", OffsetDateTime.class),
            rs.getObject("ended_at", OffsetDateTime.class),
            rs.getString("chief_complaint"),
            rs.getString("history_text"),
            rs.getString("examination_summary"),
            rs.getString("assessment_text"),
            rs.getString("treatment_plan"),
            rs.getString("vet_notes"),
            rs.getObject("follow_up_at", OffsetDateTime.class),
            rs.getObject("created_by", UUID.class),
            rs.getObject("created_at", OffsetDateTime.class),
            rs.getObject("updated_at", OffsetDateTime.class),
            List.of(),
            List.of()
    );

    private static final RowMapper<AddendumResponse> ADDENDUM_MAPPER = (rs, i) -> new AddendumResponse(
            rs.getObject("id", UUID.class), rs.getObject("encounter_id", UUID.class), rs.getObject("clinic_id", UUID.class),
            rs.getString("note"), rs.getObject("created_by", UUID.class), rs.getObject("created_at", OffsetDateTime.class));

    private static final RowMapper<TreatmentResponse> TREATMENT_MAPPER = (rs, i) -> new TreatmentResponse(
            rs.getObject("id", UUID.class), rs.getObject("encounter_id", UUID.class), rs.getString("item_type"),
            rs.getString("name"), rs.getObject("product_id", UUID.class), rs.getObject("dose", BigDecimal.class),
            rs.getString("dose_unit"), rs.getString("route"), rs.getString("frequency_text"),
            rs.getObject("duration_days", Integer.class), rs.getBoolean("prescribed"), rs.getString("notes"),
            rs.getObject("performed_at", OffsetDateTime.class), rs.getObject("created_by", UUID.class),
            rs.getObject("created_at", OffsetDateTime.class));

    private final JdbcTemplate jdbc;

    public EncounterJdbcRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<EncounterResponse> findById(UUID id) {
        return jdbc.query(SELECT + " where e.id = ?", MAPPER, id).stream().findFirst();
    }

    /** Yazma işlemleri için satırı kilitler; eşzamanlı adım geçişleri sıraya girer. */
    public Optional<EncounterResponse> findByIdForUpdate(UUID id) {
        return jdbc.query(SELECT + " where e.id = ? for update of e", MAPPER, id).stream().findFirst();
    }

    public List<EncounterResponse> findByAppointmentId(UUID appointmentId) {
        return jdbc.query(SELECT + " where e.appointment_id = ? order by e.started_at, e.id", MAPPER, appointmentId);
    }

    public List<EncounterResponse> search(UUID animalId, EncounterStatus status, int limit, long offset) {
        List<Object> args = new ArrayList<>();
        String where = where(animalId, status, args);
        args.add(limit);
        args.add(offset);
        return jdbc.query(SELECT + where + " order by e.started_at desc, e.id limit ? offset ?", MAPPER, args.toArray());
    }

    public long count(UUID animalId, EncounterStatus status) {
        List<Object> args = new ArrayList<>();
        String where = where(animalId, status, args);
        Long total = jdbc.queryForObject("select count(*) from vet.encounters e" + where, Long.class, args.toArray());
        return total == null ? 0 : total;
    }

    private String where(UUID animalId, EncounterStatus status, List<Object> args) {
        StringBuilder sql = new StringBuilder(" where 1 = 1");
        if (animalId != null) {
            sql.append(" and e.animal_id = ?");
            args.add(animalId);
        }
        if (status != null) {
            sql.append(" and e.status = ?");
            args.add(status.name());
        }
        return sql.toString();
    }

    public void insert(UUID organizationId, EncounterResponse v) {
        jdbc.update("""
                insert into vet.encounters (id, organization_id, clinic_id, animal_id, appointment_id, attending_vet_id,
                                            encounter_no, encounter_type, status, started_at, chief_complaint, history_text,
                                            created_by)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, v.id(), organizationId, v.clinicId(), v.animalId(), v.appointmentId(), v.attendingVetId(),
                v.encounterNo(), v.encounterType(), v.status().name(), v.startedAt(), v.chiefComplaint(), v.history(),
                v.createdBy());
    }

    public void updateContent(EncounterResponse v) {
        jdbc.update("""
                update vet.encounters
                set status = ?, ended_at = ?, chief_complaint = ?, history_text = ?, examination_summary = ?,
                    assessment_text = ?, treatment_plan = ?, vet_notes = ?, follow_up_at = ?, updated_at = now()
                where id = ?
                """, v.status().name(), v.endedAt(), v.chiefComplaint(), v.history(), v.examinationSummary(),
                v.assessment(), v.treatmentPlan(), v.vetNotes(), v.followUpAt(), v.id());
    }

    public void delete(UUID id) {
        jdbc.update("delete from vet.encounters where id = ?", id);
    }

    public boolean animalVisible(UUID animalId) {
        Integer n = jdbc.queryForObject("select count(*) from vet.animals where id = ?", Integer.class, animalId);
        return n != null && n > 0;
    }

    public boolean hasOpenEncounterForAppointment(UUID appointmentId) {
        Integer n = jdbc.queryForObject(
                "select count(*) from vet.encounters where appointment_id = ? and status <> 'CLOSED'", Integer.class, appointmentId);
        return n != null && n > 0;
    }

    public boolean productVisible(UUID productId) {
        Integer n = jdbc.queryForObject("select count(*) from vet.products where id = ?", Integer.class, productId);
        return n != null && n > 0;
    }

    public long countDependents(UUID encounterId) {
        Long n = jdbc.queryForObject("""
                select (select count(*) from vet.encounter_treatments where encounter_id = ?)
                     + (select count(*) from vet.encounter_addenda where encounter_id = ?)
                     + (select count(*) from vet.vaccine_records where encounter_id = ?)
                     + (select count(*) from vet.clinical_documents where encounter_id = ?)
                """, Long.class, encounterId, encounterId, encounterId, encounterId);
        return n == null ? 0 : n;
    }

    public AddendumResponse insertAddendum(UUID organizationId, UUID encounterId, UUID clinicId, String note, UUID userId) {
        return jdbc.queryForObject("""
                insert into vet.encounter_addenda (organization_id, encounter_id, clinic_id, note, created_by)
                values (?, ?, ?, ?, ?)
                returning id, encounter_id, clinic_id, note, created_by, created_at
                """, ADDENDUM_MAPPER, organizationId, encounterId, clinicId, note, userId);
    }

    public List<AddendumResponse> findAddenda(UUID encounterId) {
        return jdbc.query("select id, encounter_id, clinic_id, note, created_by, created_at from vet.encounter_addenda "
                + "where encounter_id = ? order by created_at, id", ADDENDUM_MAPPER, encounterId);
    }

    public TreatmentResponse insertTreatment(UUID organizationId, UUID encounterId, TreatmentResponse t) {
        return jdbc.queryForObject("""
                insert into vet.encounter_treatments (organization_id, encounter_id, item_type, name, product_id, dose,
                    dose_unit, route, frequency_text, duration_days, prescribed, notes, performed_at, created_by)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                returning id, encounter_id, item_type, name, product_id, dose, dose_unit, route, frequency_text,
                          duration_days, prescribed, notes, performed_at, created_by, created_at
                """, TREATMENT_MAPPER, organizationId, encounterId, t.itemType(), t.name(), t.productId(), t.dose(),
                t.doseUnit(), t.route(), t.frequencyText(), t.durationDays(), t.prescribed(), t.notes(),
                t.performedAt(), t.createdBy());
    }

    public List<TreatmentResponse> findTreatments(UUID encounterId) {
        return jdbc.query("""
                select id, encounter_id, item_type, name, product_id, dose, dose_unit, route, frequency_text,
                       duration_days, prescribed, notes, performed_at, created_by, created_at
                from vet.encounter_treatments where encounter_id = ? order by performed_at, id
                """, TREATMENT_MAPPER, encounterId);
    }

    public Optional<UUID> findTreatmentEncounterId(UUID treatmentId) {
        return jdbc.queryForList("select encounter_id from vet.encounter_treatments where id = ?", UUID.class, treatmentId)
                .stream().findFirst();
    }

    public void deleteTreatment(UUID treatmentId) {
        jdbc.update("delete from vet.encounter_treatments where id = ?", treatmentId);
    }

    public List<HistoryItemResponse> history(UUID animalId, int limit, long offset) {
        return jdbc.query(HISTORY_SQL + " order by occurred_at desc, id limit ? offset ?", (rs, i) -> new HistoryItemResponse(
                rs.getString("type"), rs.getObject("id", UUID.class), rs.getObject("occurred_at", OffsetDateTime.class),
                rs.getObject("clinic_id", UUID.class), rs.getString("title"), rs.getString("detail"),
                rs.getObject("encounter_id", UUID.class)), animalId, animalId, animalId, limit, offset);
    }

    public long historyCount(UUID animalId) {
        Long n = jdbc.queryForObject("select count(*) from (" + HISTORY_SQL + ") h", Long.class, animalId, animalId, animalId);
        return n == null ? 0 : n;
    }

    private static final String HISTORY_SQL = """
            select * from (
                select 'ENCOUNTER' as type, e.id, e.started_at as occurred_at, e.clinic_id, e.encounter_no as title,
                       e.status as detail, e.id as encounter_id
                from vet.encounters e where e.animal_id = ?
                union all
                select 'VACCINATION', vr.id, vr.administered_at, vr.clinic_id, vr.vaccine_name, vr.next_due_source, vr.encounter_id
                from vet.vaccine_records vr where vr.animal_id = ?
                union all
                select 'TREATMENT', t.id, t.performed_at, e.clinic_id, t.name, t.item_type, t.encounter_id
                from vet.encounter_treatments t
                join vet.encounters e on e.organization_id = t.organization_id and e.id = t.encounter_id
                where e.animal_id = ?
            ) x
            """;
}
