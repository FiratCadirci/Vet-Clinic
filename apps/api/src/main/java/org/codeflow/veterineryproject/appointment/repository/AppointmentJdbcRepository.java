package org.codeflow.veterineryproject.appointment.repository;

import org.codeflow.veterineryproject.appointment.dto.AppointmentResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Repository
public class AppointmentJdbcRepository {

    public record Filter(OffsetDateTime from, OffsetDateTime to, UUID clinicId, UUID veterinarianId,
                         List<String> statuses, String type, String bookingChannel, UUID ownerId, UUID animalId) {
    }

    private static final Map<String, String> SORT_COLUMNS = Map.of(
            "startsAt", "a.starts_at",
            "endsAt", "a.ends_at",
            "appointmentNo", "a.appointment_no",
            "status", "a.status",
            "appointmentType", "a.appointment_type",
            "bookingChannel", "a.booking_channel"
    );

    private static final String SELECT = """
            select a.id, a.appointment_no, a.clinic_id, a.client_id, c.display_name as client_name,
                   a.animal_id, an.name as animal_name, an.patient_no, a.veterinarian_id, sm.full_name as vet_name,
                   a.appointment_type, a.status, a.starts_at, a.ends_at, a.reason, a.internal_notes, a.booking_channel,
                   a.cancelled_at, a.cancellation_reason, a.created_at, a.updated_at
            from vet.appointments a
            join vet.clients c on c.organization_id = a.organization_id and c.id = a.client_id
            join vet.animals an on an.organization_id = a.organization_id and an.id = a.animal_id
            left join vet.veterinarians v on v.organization_id = a.organization_id and v.id = a.veterinarian_id
            left join vet.staff_members sm on sm.organization_id = v.organization_id and sm.id = v.staff_member_id
            """;

    private static final RowMapper<AppointmentResponse> MAPPER = (rs, i) -> new AppointmentResponse(
            rs.getObject("id", UUID.class), rs.getString("appointment_no"), rs.getObject("clinic_id", UUID.class),
            rs.getObject("client_id", UUID.class), rs.getString("client_name"), rs.getObject("animal_id", UUID.class),
            rs.getString("animal_name"), rs.getString("patient_no"), rs.getObject("veterinarian_id", UUID.class),
            rs.getString("vet_name"), rs.getString("appointment_type"), rs.getString("status"),
            rs.getObject("starts_at", OffsetDateTime.class), rs.getObject("ends_at", OffsetDateTime.class),
            rs.getString("reason"), rs.getString("internal_notes"), rs.getString("booking_channel"),
            rs.getObject("cancelled_at", OffsetDateTime.class), rs.getString("cancellation_reason"),
            rs.getObject("created_at", OffsetDateTime.class), rs.getObject("updated_at", OffsetDateTime.class));

    private final JdbcTemplate jdbc;

    public AppointmentJdbcRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<AppointmentResponse> search(Filter f, Pageable pageable) {
        List<Object> args = new ArrayList<>();
        String where = where(f, args);
        args.add(pageable.getPageSize());
        args.add(pageable.getOffset());
        return jdbc.query(SELECT + where + orderBy(pageable.getSort()) + " limit ? offset ?", MAPPER, args.toArray());
    }

    public long count(Filter f) {
        List<Object> args = new ArrayList<>();
        String where = where(f, args);
        Long n = jdbc.queryForObject("select count(*) from vet.appointments a " + where, Long.class, args.toArray());
        return n == null ? 0 : n;
    }

    public Optional<AppointmentResponse> findById(UUID id) {
        return jdbc.query(SELECT + " where a.id = ?", MAPPER, id).stream().findFirst();
    }

    public void insert(UUID id, UUID organizationId, UUID clinicId, UUID clientId, UUID animalId, UUID veterinarianId,
                       String appointmentNo, String type, OffsetDateTime startsAt, OffsetDateTime endsAt, String reason,
                       String notes, String channel, UUID createdBy) {
        jdbc.update("""
                insert into vet.appointments (id, organization_id, clinic_id, client_id, animal_id, veterinarian_id,
                    appointment_no, appointment_type, status, starts_at, ends_at, reason, internal_notes, booking_channel, created_by)
                values (?, ?, ?, ?, ?, ?, ?, ?, 'BOOKED', ?, ?, ?, ?, ?, ?)
                """, id, organizationId, clinicId, clientId, animalId, veterinarianId, appointmentNo, type, startsAt,
                endsAt, reason, notes, channel, createdBy);
    }

    public void reschedule(UUID id, OffsetDateTime startsAt, OffsetDateTime endsAt, UUID veterinarianId) {
        jdbc.update("update vet.appointments set starts_at = ?, ends_at = ?, veterinarian_id = ?, updated_at = now() where id = ?",
                startsAt, endsAt, veterinarianId, id);
    }

    public void markCancelled(UUID id, String reason) {
        jdbc.update("update vet.appointments set cancelled_at = now(), cancellation_reason = ?, updated_at = now() where id = ?",
                reason, id);
    }

    /** Hastanın güncel sahipleri arasından birincil iletişim kişisini, yoksa ilk sahibi döner. */
    public Optional<UUID> findOwnerClientId(UUID animalId) {
        return jdbc.query("""
                select client_id from vet.animal_owners where animal_id = ? and valid_to is null
                order by is_primary_contact desc, is_legal_owner desc, created_at limit 1
                """, (rs, i) -> rs.getObject("client_id", UUID.class), animalId).stream().findFirst();
    }

    public boolean isCurrentOwner(UUID animalId, UUID clientId) {
        return !jdbc.queryForList("select 1 from vet.animal_owners where animal_id = ? and client_id = ? and valid_to is null",
                animalId, clientId).isEmpty();
    }

    public boolean animalExists(UUID animalId) {
        return !jdbc.queryForList("select 1 from vet.animals where id = ?", animalId).isEmpty();
    }

    public boolean animalDeceased(UUID animalId) {
        return !jdbc.queryForList("select 1 from vet.animals where id = ? and deceased_at is not null", animalId).isEmpty();
    }

    public boolean veterinarianActive(UUID veterinarianId) {
        return !jdbc.queryForList("select 1 from vet.veterinarians where id = ? and is_active", veterinarianId).isEmpty();
    }

    private String where(Filter f, List<Object> args) {
        StringBuilder sb = new StringBuilder(" where true");
        if (f.from() != null) {
            sb.append(" and a.ends_at > ?");
            args.add(f.from());
        }
        if (f.to() != null) {
            sb.append(" and a.starts_at < ?");
            args.add(f.to());
        }
        if (f.clinicId() != null) {
            sb.append(" and a.clinic_id = ?");
            args.add(f.clinicId());
        }
        if (f.veterinarianId() != null) {
            sb.append(" and a.veterinarian_id = ?");
            args.add(f.veterinarianId());
        }
        if (!f.statuses().isEmpty()) {
            sb.append(" and a.status in (").append("?,".repeat(f.statuses().size()));
            sb.setLength(sb.length() - 1);
            sb.append(')');
            args.addAll(f.statuses());
        }
        if (f.type() != null) {
            sb.append(" and a.appointment_type = ?");
            args.add(f.type());
        }
        if (f.bookingChannel() != null) {
            sb.append(" and a.booking_channel = ?");
            args.add(f.bookingChannel());
        }
        if (f.ownerId() != null) {
            sb.append(" and a.client_id = ?");
            args.add(f.ownerId());
        }
        if (f.animalId() != null) {
            sb.append(" and a.animal_id = ?");
            args.add(f.animalId());
        }
        return sb.toString();
    }

    private String orderBy(Sort sort) {
        List<String> clauses = new ArrayList<>();
        for (Sort.Order order : sort) {
            String column = SORT_COLUMNS.get(order.getProperty());
            if (column == null) {
                throw new IllegalArgumentException("Desteklenmeyen randevu sıralama alanı: " + order.getProperty());
            }
            clauses.add(column + (order.isAscending() ? " asc" : " desc"));
        }
        if (clauses.isEmpty()) {
            clauses.add("a.starts_at asc");
            clauses.add("a.appointment_no asc");
        }
        clauses.add("a.id asc");
        return " order by " + String.join(", ", clauses);
    }
}
