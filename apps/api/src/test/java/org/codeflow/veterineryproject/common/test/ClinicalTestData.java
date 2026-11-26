package org.codeflow.veterineryproject.common.test;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

/** Klinik kayıt testleri için süper kullanıcı bağlantısıyla doğrudan veri hazırlama/okuma yardımcıları. */
public final class ClinicalTestData {

    private static final java.util.concurrent.atomic.AtomicInteger SLOT = new java.util.concurrent.atomic.AtomicInteger(10);

    private ClinicalTestData() {
    }

    /** Hasta ve müşteri aynı id ile tohumlanmıştır (TenantTestData.PATIENT_*). */
    public static UUID appointment(DataSource superUser, UUID org, UUID branch, UUID patient, String status) {
        UUID id = UUID.randomUUID();
        int day = SLOT.incrementAndGet();
        update(superUser, "INSERT INTO vet.appointments (id, organization_id, clinic_id, client_id, animal_id, appointment_no, status, starts_at, ends_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, now() + make_interval(days => ?), now() + make_interval(days => ?, hours => 1))",
                id, org, branch, patient, patient, "IT-" + id.toString().substring(0, 12), status, day, day);
        return id;
    }

    public static String appointmentStatus(DataSource superUser, UUID appointmentId) {
        return text(superUser, "SELECT status FROM vet.appointments WHERE id = ?", appointmentId);
    }

    public static long count(DataSource superUser, String sql, Object... params) {
        return Long.parseLong(text(superUser, sql, params));
    }

    public static String text(DataSource superUser, String sql, Object... params) {
        try (Connection c = superUser.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            bind(ps, params);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getString(1);
            }
        } catch (SQLException ex) {
            throw new IllegalStateException(ex.getMessage(), ex);
        }
    }

    public static void update(DataSource superUser, String sql, Object... params) {
        try (Connection c = superUser.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            bind(ps, params);
            ps.executeUpdate();
        } catch (SQLException ex) {
            throw new IllegalStateException(ex.getMessage(), ex);
        }
    }

    private static void bind(PreparedStatement ps, Object[] params) throws SQLException {
        for (int i = 0; i < params.length; i++) {
            ps.setObject(i + 1, params[i]);
        }
    }
}
