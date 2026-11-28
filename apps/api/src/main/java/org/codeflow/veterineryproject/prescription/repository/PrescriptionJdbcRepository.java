package org.codeflow.veterineryproject.prescription.repository;

import org.codeflow.veterineryproject.prescription.dto.PrescriptionItemResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** RLS: reçete ve kalemleri yalnızca muayenesi görünen (aynı klinik grubu) kullanıcıya görünür. */
@Repository
public class PrescriptionJdbcRepository {

    public record ProductRow(UUID id, String name, String productType) {
    }

    private static final String ITEM_SELECT = """
            select i.id, i.prescription_id, p.prescription_no, p.encounter_id, i.line_no, i.product_id, pr.name as product_name,
                   i.dose, i.dose_unit, i.route, i.frequency_text, i.duration_days, i.quantity_prescribed, i.instructions
            from vet.prescription_items i
            join vet.prescriptions p on p.organization_id = i.organization_id and p.id = i.prescription_id
            join vet.products pr on pr.organization_id = i.organization_id and pr.id = i.product_id
            """;

    private static final RowMapper<PrescriptionItemResponse> ITEM_MAPPER = (rs, n) -> new PrescriptionItemResponse(
            rs.getObject("id", UUID.class), rs.getObject("prescription_id", UUID.class), rs.getString("prescription_no"),
            rs.getObject("encounter_id", UUID.class), rs.getInt("line_no"), rs.getObject("product_id", UUID.class),
            rs.getString("product_name"), stripped(rs.getBigDecimal("dose")), rs.getString("dose_unit"), rs.getString("route"),
            rs.getString("frequency_text"), rs.getObject("duration_days", Integer.class),
            stripped(rs.getBigDecimal("quantity_prescribed")), rs.getString("instructions"));

    private final JdbcTemplate jdbc;

    public PrescriptionJdbcRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<ProductRow> findProduct(UUID productId) {
        return jdbc.query("select id, name, product_type from vet.products where id = ? and is_active",
                (rs, n) -> new ProductRow(rs.getObject("id", UUID.class), rs.getString("name"), rs.getString("product_type")),
                productId).stream().findFirst();
    }

    public Optional<UUID> findPrescriptionId(UUID encounterId) {
        return jdbc.queryForList("select id from vet.prescriptions where encounter_id = ? order by prescribed_at limit 1",
                UUID.class, encounterId).stream().findFirst();
    }

    public void insertPrescription(UUID id, UUID organizationId, UUID encounterId, UUID animalId, UUID veterinarianId,
                                   String number) {
        jdbc.update("""
                insert into vet.prescriptions (id, organization_id, encounter_id, animal_id, prescribed_by, prescription_no, status)
                values (?, ?, ?, ?, ?, ?, 'ACTIVE')
                """, id, organizationId, encounterId, animalId, veterinarianId, number);
    }

    public int nextLineNo(UUID prescriptionId) {
        Integer n = jdbc.queryForObject("select coalesce(max(line_no), 0) + 1 from vet.prescription_items where prescription_id = ?",
                Integer.class, prescriptionId);
        return n == null ? 1 : n;
    }

    public void insertItem(UUID id, UUID organizationId, UUID prescriptionId, int lineNo, UUID productId, BigDecimal dose,
                           String doseUnit, String route, String frequencyText, Integer durationDays, BigDecimal quantity,
                           String instructions) {
        jdbc.update("""
                insert into vet.prescription_items (id, organization_id, prescription_id, product_id, line_no, dose, dose_unit,
                    route, frequency_text, duration_days, quantity_prescribed, instructions)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, id, organizationId, prescriptionId, productId, lineNo, dose, doseUnit, route, frequencyText,
                durationDays, quantity, instructions);
    }

    public Optional<PrescriptionItemResponse> findItem(UUID itemId) {
        return jdbc.query(ITEM_SELECT + " where i.id = ?", ITEM_MAPPER, itemId).stream().findFirst();
    }

    public List<PrescriptionItemResponse> findItemsByEncounter(UUID encounterId) {
        return jdbc.query(ITEM_SELECT + " where p.encounter_id = ? order by p.prescribed_at, i.line_no", ITEM_MAPPER, encounterId);
    }

    public void deleteItem(UUID itemId) {
        jdbc.update("delete from vet.prescription_items where id = ?", itemId);
    }

    /** Kalemi kalmayan reçete başlığı silinir; böylece boş muayene silinebilir kalır. */
    public void deleteIfEmpty(UUID prescriptionId) {
        jdbc.update("delete from vet.prescriptions p where p.id = ? and not exists "
                + "(select 1 from vet.prescription_items i where i.prescription_id = p.id)", prescriptionId);
    }

    private static BigDecimal stripped(BigDecimal value) {
        if (value == null) {
            return null;
        }
        BigDecimal plain = value.stripTrailingZeros();
        return plain.scale() < 0 ? plain.setScale(0) : plain;
    }
}
