package org.codeflow.veterineryproject.organization.repository;

import org.codeflow.veterineryproject.organization.dto.OrganizationDto.ProfileResponse;
import org.codeflow.veterineryproject.organization.dto.OrganizationDto.Registration;
import org.codeflow.veterineryproject.organization.dto.OrganizationDto.SubscriptionSummary;
import org.codeflow.veterineryproject.organization.dto.OrganizationDto.UpdateProfileRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

/** Klinik kaydı (SECURITY DEFINER vet.register_clinic) ve organizasyon profili. Profil sorguları RLS altındadır. */
@Repository
public class OrganizationJdbcRepository {

    private final JdbcTemplate jdbc;

    public OrganizationJdbcRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Registration registerClinic(String name, String phone, String email, String address, String taxNumber,
                                       String branchName, String adminName, String adminEmail, String passwordHash,
                                       String planCode, int trialDays) {
        return jdbc.queryForObject("select * from vet.register_clinic(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                (rs, i) -> new Registration(
                        rs.getString("result"),
                        rs.getObject("organization_id", UUID.class),
                        rs.getObject("organization_code", Integer.class),
                        rs.getObject("user_id", UUID.class),
                        rs.getObject("clinic_id", UUID.class)),
                name, phone, email, address, taxNumber, branchName, adminName, adminEmail, passwordHash, planCode, trialDays);
    }

    public Optional<ProfileResponse> findProfile(UUID organizationId) {
        return jdbc.query("""
                select id, code, name, legal_name, tax_office, tax_number, phone, email, address_line
                from vet.organizations where id = ?
                """, (rs, i) -> new ProfileResponse(
                rs.getObject("id", UUID.class),
                rs.getObject("code", Integer.class),
                rs.getString("name"),
                rs.getString("legal_name"),
                rs.getString("tax_office"),
                rs.getString("tax_number"),
                rs.getString("phone"),
                rs.getString("email"),
                rs.getString("address_line"),
                null,
                null), organizationId).stream().findFirst();
    }

    public Optional<SubscriptionSummary> currentSubscription(UUID organizationId) {
        return jdbc.query("""
                select s.status, p.code, p.name, s.billing_period, s.current_period_end,
                       s.max_branches_snapshot, s.max_users_snapshot
                from vet.organization_subscriptions s
                join vet.subscription_plans p on p.id = s.plan_id
                where s.organization_id = ? and s.status in ('TRIAL', 'ACTIVE', 'PAST_DUE')
                """, (rs, i) -> new SubscriptionSummary(
                rs.getString("status"),
                rs.getString("code"),
                rs.getString("name"),
                rs.getString("billing_period"),
                rs.getObject("current_period_end", OffsetDateTime.class),
                rs.getInt("max_branches_snapshot"),
                rs.getInt("max_users_snapshot")), organizationId).stream().findFirst();
    }

    public int updateProfile(UUID organizationId, UpdateProfileRequest r) {
        return jdbc.update("""
                update vet.organizations
                set name = ?, legal_name = ?, tax_office = ?, tax_number = ?, phone = ?, email = ?, address_line = ?,
                    updated_at = now()
                where id = ?
                """, r.name(), r.legalName(), r.taxOffice(), r.taxNumber(), r.phone(), r.email(), r.addressLine(),
                organizationId);
    }
}
