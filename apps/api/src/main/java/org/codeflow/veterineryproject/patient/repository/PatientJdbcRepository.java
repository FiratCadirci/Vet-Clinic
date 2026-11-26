package org.codeflow.veterineryproject.patient.repository;

import org.codeflow.veterineryproject.common.sql.SqlLike;
import org.codeflow.veterineryproject.patient.dto.PatientHistoryItem;
import org.codeflow.veterineryproject.patient.dto.PatientHistoryResponse.HiddenRecordCounts;
import org.codeflow.veterineryproject.patient.dto.PatientSummaryResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Hasta kartı okuma sorguları. Tümü RLS altında çalışır (yalnızca çağıranın klinik grubunun kayıtları);
 * tek istisna {@link #totalHistoryCounts(UUID)}: RLS'i aşan SECURITY DEFINER fonksiyondan yalnızca sayı okur.
 */
@Repository
public class PatientJdbcRepository {

    private static final String SUMMARY_SELECT = """
            select a.id, a.patient_no, a.name, a.species_id, s.code as species_code, s.name as species_name,
                   a.breed_id, b.name as breed_name, a.sex, a.birth_date, a.deceased_at, a.profile_notes, a.created_at,
                   (select ai.identifier_value from vet.animal_identifiers ai
                     where ai.animal_id = a.id and ai.identifier_type = 'MICROCHIP'
                     order by ai.created_at, ai.id limit 1) as microchip_no,
                   (select aa.clinic_id from vet.animal_clinic_access aa
                     where aa.animal_id = a.id and aa.revoked_at is null
                     order by aa.granted_at, aa.id limit 1) as registered_clinic_id,
                   o.client_id as owner_id, c.display_name as owner_name,
                   (select cc.contact_value from vet.client_contacts cc
                     where cc.client_id = o.client_id and cc.contact_type = 'PHONE' and cc.is_primary limit 1) as owner_phone
            from vet.animals a
            join vet.species s on s.id = a.species_id
            left join vet.breeds b on b.id = a.breed_id
            left join lateral (
                select ao.client_id from vet.animal_owners ao
                where ao.animal_id = a.id and ao.valid_to is null
                order by ao.is_primary_contact desc, ao.is_legal_owner desc, ao.created_at, ao.id limit 1
            ) o on true
            left join vet.clients c on c.organization_id = a.organization_id and c.id = o.client_id
            """;

    private static final RowMapper<PatientSummaryResponse> SUMMARY = (rs, i) -> new PatientSummaryResponse(
            rs.getObject("id", UUID.class), rs.getString("patient_no"), rs.getString("name"),
            rs.getObject("species_id", UUID.class), rs.getString("species_code"), rs.getString("species_name"),
            rs.getObject("breed_id", UUID.class), rs.getString("breed_name"), rs.getString("sex"),
            rs.getObject("birth_date", LocalDate.class), rs.getObject("deceased_at", LocalDate.class),
            rs.getString("profile_notes"), rs.getString("microchip_no"),
            rs.getObject("registered_clinic_id", UUID.class), rs.getObject("owner_id", UUID.class),
            rs.getString("owner_name"), rs.getString("owner_phone"), rs.getObject("created_at", OffsetDateTime.class));

    private static final String ENCOUNTERS = """
            select 'ENCOUNTER' as type, e.id, e.started_at as occurred_at, e.ended_at, e.clinic_id, cl.name as clinic_name,
                   e.status::varchar as status, e.encounter_no as title, sm.full_name as veterinarian_name,
                   e.chief_complaint, e.assessment_text as assessment, e.treatment_plan,
                   null::integer as dose_number, null::varchar as batch_number, null::date as next_due_at,
                   null::varchar as appointment_type, e.id as encounter_id
            from vet.encounters e
            left join vet.clinics cl on cl.organization_id = e.organization_id and cl.id = e.clinic_id
            left join vet.veterinarians v on v.organization_id = e.organization_id and v.id = e.attending_vet_id
            left join vet.staff_members sm on sm.organization_id = v.organization_id and sm.id = v.staff_member_id
            where e.animal_id = ?
            """;

    private static final String VACCINATIONS = """
            select 'VACCINATION' as type, vr.id, vr.administered_at as occurred_at, null::timestamptz as ended_at,
                   vr.clinic_id, cl.name as clinic_name, null::varchar as status, vr.vaccine_name as title,
                   sm.full_name as veterinarian_name, null::text as chief_complaint, null::text as assessment,
                   null::text as treatment_plan, pd.dose_number::integer as dose_number, vr.batch_number::varchar as batch_number,
                   vr.next_due_at, null::varchar as appointment_type, vr.encounter_id
            from vet.vaccine_records vr
            left join vet.clinics cl on cl.organization_id = vr.organization_id and cl.id = vr.clinic_id
            left join vet.veterinarians v on v.organization_id = vr.organization_id and v.id = vr.administered_by
            left join vet.staff_members sm on sm.organization_id = v.organization_id and sm.id = v.staff_member_id
            left join vet.vaccine_protocol_doses pd on pd.organization_id = vr.organization_id and pd.id = vr.protocol_dose_id
            where vr.animal_id = ?
            """;

    private static final String APPOINTMENTS = """
            select 'APPOINTMENT' as type, ap.id, ap.starts_at as occurred_at, ap.ends_at as ended_at,
                   ap.clinic_id, cl.name as clinic_name, ap.status::varchar as status, ap.appointment_no as title,
                   sm.full_name as veterinarian_name, null::text as chief_complaint, null::text as assessment,
                   null::text as treatment_plan, null::integer as dose_number, null::varchar as batch_number,
                   null::date as next_due_at, ap.appointment_type::varchar as appointment_type, null::uuid as encounter_id
            from vet.appointments ap
            left join vet.clinics cl on cl.organization_id = ap.organization_id and cl.id = ap.clinic_id
            left join vet.veterinarians v on v.organization_id = ap.organization_id and v.id = ap.veterinarian_id
            left join vet.staff_members sm on sm.organization_id = v.organization_id and sm.id = v.staff_member_id
            where ap.animal_id = ?
            """;

    private static final RowMapper<PatientHistoryItem> HISTORY = (rs, i) -> new PatientHistoryItem(
            rs.getString("type"), rs.getObject("id", UUID.class), rs.getObject("occurred_at", OffsetDateTime.class),
            rs.getObject("ended_at", OffsetDateTime.class), rs.getObject("clinic_id", UUID.class),
            rs.getString("clinic_name"), rs.getString("status"), rs.getString("title"),
            rs.getString("veterinarian_name"), rs.getString("chief_complaint"), rs.getString("assessment"),
            rs.getString("treatment_plan"), rs.getObject("dose_number", Integer.class), rs.getString("batch_number"),
            rs.getObject("next_due_at", LocalDate.class), rs.getString("appointment_type"),
            rs.getObject("encounter_id", UUID.class));

    private final JdbcTemplate jdbc;

    public PatientJdbcRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // ---- Hasta listesi -----------------------------------------------------------------------------------------

    public List<PatientSummaryResponse> search(String query, UUID ownerId, int limit, long offset) {
        List<Object> args = new ArrayList<>();
        String where = searchWhere(query, ownerId, args);
        args.add(limit);
        args.add(offset);
        return jdbc.query(SUMMARY_SELECT + where + " order by lower(a.name), a.id limit ? offset ?", SUMMARY, args.toArray());
    }

    public long count(String query, UUID ownerId) {
        List<Object> args = new ArrayList<>();
        String where = searchWhere(query, ownerId, args);
        Long n = jdbc.queryForObject("select count(*) from vet.animals a" + where, Long.class, args.toArray());
        return n == null ? 0 : n;
    }

    public Optional<PatientSummaryResponse> findById(UUID id) {
        return jdbc.query(SUMMARY_SELECT + " where a.id = ?", SUMMARY, id).stream().findFirst();
    }

    /**
     * Ad, hasta no, güncel sahip adı, kimlik/çip no veya sahibin telefonu (yalnız rakamlar) ile arar.
     * {@code ownerId}: yalnızca bu müşterinin güncel hayvanları (sahip kartı).
     */
    private static String searchWhere(String query, UUID ownerId, List<Object> args) {
        String where = queryWhere(query, args);
        if (ownerId == null) {
            return where;
        }
        args.add(ownerId);
        return where + " and exists (select 1 from vet.animal_owners ow"
                + " where ow.animal_id = a.id and ow.client_id = ? and ow.valid_to is null)";
    }

    private static String queryWhere(String query, List<Object> args) {
        if (query == null || query.isBlank()) {
            return " where true";
        }
        String like = SqlLike.contains(query);
        StringBuilder where = new StringBuilder("""
                 where (lower(a.name) like ? escape '\\' or lower(a.patient_no) like ? escape '\\'
                    or exists (select 1 from vet.animal_owners ao
                               join vet.clients c2 on c2.organization_id = ao.organization_id and c2.id = ao.client_id
                               where ao.animal_id = a.id and ao.valid_to is null and lower(c2.display_name) like ? escape '\\')
                    or exists (select 1 from vet.animal_identifiers ai
                               where ai.animal_id = a.id and lower(ai.identifier_value) like ? escape '\\')
                """);
        for (int i = 0; i < 4; i++) {
            args.add(like);
        }
        String digits = phoneDigits(query);
        if (digits != null) {
            where.append("""
                        or exists (select 1 from vet.animal_owners ao
                                   join vet.client_contacts cc on cc.client_id = ao.client_id and cc.contact_type = 'PHONE'
                                   where ao.animal_id = a.id and ao.valid_to is null
                                     and regexp_replace(cc.contact_value, '[^0-9]', '', 'g') like ?)
                    """);
            args.add("%" + digits + "%");
        }
        return where.append(")").toString();
    }

    /** Sorgu telefon gibi görünüyorsa (en az 3 rakam, harf yok) ülke/alan öneki farkını yok sayan rakam dizisi. */
    static String phoneDigits(String query) {
        String trimmed = query.trim();
        if (!trimmed.matches("[+0-9 ()\\-]+")) {
            return null;
        }
        String digits = trimmed.replaceAll("[^0-9]", "");
        if (digits.startsWith("90") && digits.length() > 10) {
            digits = digits.substring(2);
        }
        if (digits.startsWith("0")) {
            digits = digits.substring(1);
        }
        return digits.length() >= 3 ? digits : null;
    }

    // ---- Hasta geçmişi -----------------------------------------------------------------------------------------

    public boolean animalVisible(UUID animalId) {
        return !jdbc.queryForList("select 1 from vet.animals where id = ?", animalId).isEmpty();
    }

    /** Görünür kayıtlar, yeniden eskiye. {@code clinical = false} ise muayene ve aşı satırları sorguya hiç girmez. */
    public List<PatientHistoryItem> history(UUID animalId, boolean clinical, int limit, long offset) {
        List<Object> args = new ArrayList<>();
        String union = historyUnion(animalId, clinical, args);
        args.add(limit);
        args.add(offset);
        return jdbc.query("select * from (" + union + ") h order by h.occurred_at desc, h.id limit ? offset ?",
                HISTORY, args.toArray());
    }

    /** RLS altında görünen kayıt sayıları (yetkiye göre muayene/aşı sıfır sayılır). */
    public HiddenRecordCounts visibleCounts(UUID animalId, boolean clinical) {
        long encounters = clinical ? countVisible("vet.encounters", animalId) : 0;
        long vaccinations = clinical ? countVisible("vet.vaccine_records", animalId) : 0;
        return new HiddenRecordCounts(encounters, vaccinations, countVisible("vet.appointments", animalId));
    }

    /** Organizasyondaki toplam kayıt sayıları (SECURITY DEFINER; içerik değil yalnızca sayı). */
    public HiddenRecordCounts totalHistoryCounts(UUID animalId) {
        return jdbc.queryForObject("select encounters, vaccinations, appointments from vet.count_animal_history_records(?)",
                (rs, i) -> new HiddenRecordCounts(rs.getLong("encounters"), rs.getLong("vaccinations"),
                        rs.getLong("appointments")), animalId);
    }

    private long countVisible(String table, UUID animalId) {
        Long n = jdbc.queryForObject("select count(*) from " + table + " where animal_id = ?", Long.class, animalId);
        return n == null ? 0 : n;
    }

    private static String historyUnion(UUID animalId, boolean clinical, List<Object> args) {
        List<String> parts = new ArrayList<>();
        if (clinical) {
            parts.add(ENCOUNTERS);
            parts.add(VACCINATIONS);
            args.add(animalId);
            args.add(animalId);
        }
        parts.add(APPOINTMENTS);
        args.add(animalId);
        return String.join(" union all ", parts);
    }
}
