package org.codeflow.veterineryproject.report.repository;

import org.codeflow.veterineryproject.report.dto.ReportDto.ExaminationRow;
import org.codeflow.veterineryproject.report.dto.ReportDto.OverdueVaccination;
import org.codeflow.veterineryproject.report.dto.ReportDto.SalesDayRow;
import org.codeflow.veterineryproject.report.dto.ReportDto.SalesItemRow;
import org.codeflow.veterineryproject.report.dto.ReportDto.SalesTotals;
import org.codeflow.veterineryproject.report.dto.ReportDto.StockMovementRow;
import org.codeflow.veterineryproject.report.model.BookedMinutes;
import org.codeflow.veterineryproject.report.model.ReportPeriod;
import org.codeflow.veterineryproject.report.model.WorkingHoursSlot;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Rapor sorguları (FG-L1). Diğer modüllerin servislerine değil doğrudan temel tablolara bakar; organizasyon ve
 * klinik grubu sınırı RLS'tedir, şube kapsamı (kullanıcının şubeleri ya da seçilen şube) her sorguya verilir.
 * Zaman aralıkları {@code from} dahil, {@code to} hariçtir; günler Türkiye saatine göre gruplanır.
 */
@Repository
public class ReportJdbcRepository {

    /** Satış sayılan fatura durumları; DRAFT ve VOID satış değildir. */
    public static final List<String> SOLD_INVOICE_STATUSES = List.of("ISSUED", "PARTIALLY_PAID", "PAID", "OVERDUE");
    /** Doluluğa sayılmayan randevu durumları (slotu boşaltır). */
    public static final List<String> FREE_SLOT_STATUSES = List.of("CANCELLED", "NO_SHOW");

    private static final String LOCAL_DAY = "at time zone 'Europe/Istanbul')::date";
    private static final String VET_NAME_JOIN = """
            left join vet.veterinarians v on v.organization_id = %1$s.organization_id and v.id = %1$s.%2$s
            left join vet.staff_members sm on sm.organization_id = v.organization_id and sm.id = v.staff_member_id
            """;
    private static final String SOLD_INVOICE = """
            i.clinic_id = any(?::uuid[]) and i.invoice_type = 'SALES' and i.status in (%s)
            and i.issued_at >= ? and i.issued_at < ?
            """.formatted(SOLD_INVOICE_STATUSES.stream().map(s -> "'" + s + "'").collect(Collectors.joining(",")));
    private static final String FREE_SLOT = FREE_SLOT_STATUSES.stream().map(s -> "'" + s + "'")
            .collect(Collectors.joining(","));

    private final JdbcTemplate jdbc;

    public ReportJdbcRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // ---------------------------------------------------------------- muayene

    public Map<UUID, Long> examinationsByClinic(Collection<UUID> clinics, ReportPeriod p) {
        return countByClinic("""
                select clinic_id, count(*) as n from vet.encounters
                where clinic_id = any(?::uuid[]) and started_at >= ? and started_at < ?
                group by clinic_id
                """, clinics, p);
    }

    public List<ExaminationRow> examinationsByDay(Collection<UUID> clinics, ReportPeriod p) {
        return jdbc.query("""
                select (e.started_at %s as day, e.clinic_id, e.attending_vet_id, sm.full_name as vet_name,
                       e.encounter_type, count(*) as n
                from vet.encounters e
                %s
                where e.clinic_id = any(?::uuid[]) and e.started_at >= ? and e.started_at < ?
                group by 1, 2, 3, 4, 5
                order by 1, 4, 5
                """.formatted(LOCAL_DAY, VET_NAME_JOIN.formatted("e", "attending_vet_id")),
                (rs, n) -> new ExaminationRow(rs.getObject("day", LocalDate.class), rs.getObject("clinic_id", UUID.class),
                        rs.getObject("attending_vet_id", UUID.class), rs.getString("vet_name"),
                        rs.getString("encounter_type"), rs.getLong("n")),
                uuidArray(clinics), p.from(), p.to());
    }

    // ---------------------------------------------------------------- randevu + doluluk

    public Map<String, Long> appointmentsByStatus(Collection<UUID> clinics, ReportPeriod p) {
        Map<String, Long> result = new LinkedHashMap<>();
        jdbc.query("""
                select status, count(*) as n from vet.appointments
                where clinic_id = any(?::uuid[]) and starts_at >= ? and starts_at < ?
                group by status order by status
                """, rs -> {
            result.put(rs.getString("status"), rs.getLong("n"));
        }, uuidArray(clinics), p.from(), p.to());
        return result;
    }

    public Map<UUID, Long> appointmentsByClinic(Collection<UUID> clinics, ReportPeriod p) {
        return countByClinic("""
                select clinic_id, count(*) as n from vet.appointments
                where clinic_id = any(?::uuid[]) and starts_at >= ? and starts_at < ?
                group by clinic_id
                """, clinics, p);
    }

    /** Aralıkla kesişen aktif çalışma saatleri (geçerlilik tarihleri hesapta gün gün uygulanır). */
    public List<WorkingHoursSlot> workingHours(Collection<UUID> clinics, LocalDate firstDay, LocalDate endDayExclusive) {
        return jdbc.query("""
                select wh.clinic_id, wh.veterinarian_id, sm.full_name as vet_name, wh.day_of_week, wh.starts_at,
                       wh.ends_at, wh.effective_from, wh.effective_to
                from vet.veterinarian_working_hours wh
                %s
                where wh.clinic_id = any(?::uuid[]) and wh.is_active
                  and wh.effective_from < ? and (wh.effective_to is null or wh.effective_to >= ?)
                order by sm.full_name, wh.clinic_id, wh.day_of_week, wh.starts_at
                """.formatted(VET_NAME_JOIN.formatted("wh", "veterinarian_id")),
                (rs, n) -> new WorkingHoursSlot(rs.getObject("clinic_id", UUID.class),
                        rs.getObject("veterinarian_id", UUID.class), rs.getString("vet_name"), rs.getInt("day_of_week"),
                        rs.getObject("starts_at", LocalTime.class), rs.getObject("ends_at", LocalTime.class),
                        rs.getObject("effective_from", LocalDate.class), rs.getObject("effective_to", LocalDate.class)),
                uuidArray(clinics), endDayExclusive, firstDay);
    }

    public List<BookedMinutes> bookedMinutes(Collection<UUID> clinics, ReportPeriod p) {
        return jdbc.query("""
                select clinic_id, veterinarian_id,
                       coalesce(sum(extract(epoch from (ends_at - starts_at)) / 60), 0)::bigint as minutes
                from vet.appointments
                where clinic_id = any(?::uuid[]) and veterinarian_id is not null and status not in (%s)
                  and starts_at >= ? and starts_at < ?
                group by clinic_id, veterinarian_id
                """.formatted(FREE_SLOT),
                (rs, n) -> new BookedMinutes(rs.getObject("clinic_id", UUID.class),
                        rs.getObject("veterinarian_id", UUID.class), rs.getLong("minutes")),
                uuidArray(clinics), p.from(), p.to());
    }

    // ---------------------------------------------------------------- aşı

    /**
     * Hastanın aynı aşıya ait son kaydının sonraki doz tarihi verilen günden önce olan kayıtlar (ölen hastalar hariç).
     * Parametreler: şube dizisi, hariç üst tarih. Pano (yaklaşan aşılar) da aynı koşulu kullanır.
     */
    public static final String LATEST_DOSE_FROM = """
            from vet.vaccine_records vr
            join vet.animals a on a.organization_id = vr.organization_id and a.id = vr.animal_id
            join vet.species sp on sp.id = a.species_id
            """;
    public static final String LATEST_DOSE_WHERE = """
            where vr.clinic_id = any(?::uuid[]) and vr.next_due_at < ? and a.deceased_at is null
              and not exists (select 1 from vet.vaccine_records later
                              where later.organization_id = vr.organization_id and later.animal_id = vr.animal_id
                                and lower(later.vaccine_name) = lower(vr.vaccine_name)
                                and later.administered_at > vr.administered_at)
            """;
    public static final String LATEST_DOSE_DUE_BEFORE = LATEST_DOSE_FROM + LATEST_DOSE_WHERE;

    public long overdueVaccinationCount(Collection<UUID> clinics, LocalDate today) {
        Long n = jdbc.queryForObject("select count(*) " + LATEST_DOSE_DUE_BEFORE, Long.class, uuidArray(clinics), today);
        return n == null ? 0 : n;
    }

    public List<OverdueVaccination> overdueVaccinations(Collection<UUID> clinics, LocalDate today, int limit) {
        return jdbc.query("""
                select vr.id, vr.animal_id, a.name as animal_name, sp.name as species_name, vr.vaccine_name,
                       vr.next_due_at, vr.clinic_id, (?::date - vr.next_due_at) as days_overdue
                """ + LATEST_DOSE_DUE_BEFORE + " order by vr.next_due_at, a.name limit ?",
                (rs, n) -> new OverdueVaccination(rs.getObject("id", UUID.class), rs.getObject("animal_id", UUID.class),
                        rs.getString("animal_name"), rs.getString("species_name"), rs.getString("vaccine_name"),
                        rs.getObject("next_due_at", LocalDate.class), rs.getObject("clinic_id", UUID.class),
                        rs.getLong("days_overdue")),
                today, uuidArray(clinics), today, limit);
    }

    // ---------------------------------------------------------------- stok

    public List<StockMovementRow> stockMovements(Collection<UUID> clinics, ReportPeriod p) {
        return jdbc.query("""
                select l.product_id, pr.name as product_name, pr.product_type, t.transaction_type,
                       sum(case when l.direction = 'IN' then l.quantity else -l.quantity end) as quantity,
                       count(*) as movements
                from vet.inventory_transaction_lines l
                join vet.inventory_transactions t on t.organization_id = l.organization_id and t.id = l.transaction_id
                join vet.inventory_locations loc on loc.organization_id = t.organization_id and loc.id = t.location_id
                join vet.products pr on pr.organization_id = l.organization_id and pr.id = l.product_id
                where loc.clinic_id = any(?::uuid[]) and t.status = 'POSTED'
                  and t.occurred_at >= ? and t.occurred_at < ?
                group by l.product_id, pr.name, pr.product_type, t.transaction_type
                order by pr.name, t.transaction_type
                """,
                (rs, n) -> new StockMovementRow(rs.getObject("product_id", UUID.class), rs.getString("product_name"),
                        rs.getString("product_type"), rs.getString("transaction_type"), rs.getBigDecimal("quantity"),
                        rs.getLong("movements")),
                uuidArray(clinics), p.from(), p.to());
    }

    // ---------------------------------------------------------------- satış

    public SalesTotals salesTotals(Collection<UUID> clinics, ReportPeriod p) {
        return jdbc.queryForObject("""
                with sold as (select i.id from vet.invoices i where %s)
                select (select count(*) from sold) as cnt,
                       (select coalesce(sum(l.line_total), 0) from vet.invoice_lines l
                        where l.invoice_id in (select id from sold)) as total,
                       (select coalesce(sum(ip.applied_amount), 0) from vet.invoice_payments ip
                        join vet.payments pay on pay.organization_id = ip.organization_id and pay.id = ip.payment_id
                        where ip.invoice_id in (select id from sold) and pay.status = 'COMPLETED') as paid
                """.formatted(SOLD_INVOICE),
                (rs, n) -> new SalesTotals(rs.getLong("cnt"), rs.getBigDecimal("total"), rs.getBigDecimal("paid")),
                uuidArray(clinics), p.from(), p.to());
    }

    public List<SalesItemRow> salesByItem(Collection<UUID> clinics, ReportPeriod p) {
        return jdbc.query("""
                select l.line_type, l.product_id, l.service_id,
                       coalesce(pr.name, sc.name, l.description) as description, pr.product_type as category,
                       sum(l.quantity) as quantity, sum(l.line_total) as total
                from vet.invoice_lines l
                join vet.invoices i on i.organization_id = l.organization_id and i.id = l.invoice_id
                left join vet.products pr on pr.organization_id = l.organization_id and pr.id = l.product_id
                left join vet.service_catalog sc on sc.organization_id = l.organization_id and sc.id = l.service_id
                where %s
                group by 1, 2, 3, 4, 5
                order by total desc, description
                """.formatted(SOLD_INVOICE),
                (rs, n) -> new SalesItemRow(rs.getString("line_type"), rs.getObject("product_id", UUID.class),
                        rs.getObject("service_id", UUID.class), rs.getString("description"), rs.getString("category"),
                        rs.getBigDecimal("quantity"), rs.getBigDecimal("total")),
                uuidArray(clinics), p.from(), p.to());
    }

    public List<SalesDayRow> salesByDay(Collection<UUID> clinics, ReportPeriod p) {
        return jdbc.query("""
                select (i.issued_at %s as day, count(distinct i.id) as cnt, coalesce(sum(l.line_total), 0) as total
                from vet.invoices i
                left join vet.invoice_lines l on l.organization_id = i.organization_id and l.invoice_id = i.id
                where %s
                group by 1 order by 1
                """.formatted(LOCAL_DAY, SOLD_INVOICE),
                (rs, n) -> new SalesDayRow(rs.getObject("day", LocalDate.class), rs.getLong("cnt"),
                        rs.getBigDecimal("total")),
                uuidArray(clinics), p.from(), p.to());
    }

    public Map<UUID, BigDecimal> revenueByClinic(Collection<UUID> clinics, ReportPeriod p) {
        Map<UUID, BigDecimal> result = new LinkedHashMap<>();
        jdbc.query("""
                select i.clinic_id, coalesce(sum(l.line_total), 0) as total
                from vet.invoices i
                left join vet.invoice_lines l on l.organization_id = i.organization_id and l.invoice_id = i.id
                where %s
                group by i.clinic_id
                """.formatted(SOLD_INVOICE), rs -> {
            result.put(rs.getObject("clinic_id", UUID.class), rs.getBigDecimal("total"));
        }, uuidArray(clinics), p.from(), p.to());
        return result;
    }

    // ---------------------------------------------------------------- şube

    /** Kapsamdaki şubelerin adları (RLS: yalnız kullanıcının klinik gruplarındaki şubeler görünür). */
    public Map<UUID, String> clinicNames(Collection<UUID> clinics) {
        Map<UUID, String> result = new LinkedHashMap<>();
        jdbc.query("select id, name from vet.clinics where id = any(?::uuid[]) order by name",
                rs -> {
                    result.put(rs.getObject("id", UUID.class), rs.getString("name"));
                }, uuidArray(clinics));
        return result;
    }

    private Map<UUID, Long> countByClinic(String sql, Collection<UUID> clinics, ReportPeriod p) {
        Map<UUID, Long> result = new LinkedHashMap<>();
        jdbc.query(sql, rs -> {
            result.put(rs.getObject("clinic_id", UUID.class), rs.getLong("n"));
        }, uuidArray(clinics), p.from(), p.to());
        return result;
    }

    public static String uuidArray(Collection<UUID> ids) {
        return ids.stream().map(UUID::toString).collect(Collectors.joining(",", "{", "}"));
    }
}
