package org.codeflow.veterineryproject.staff.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.UUID;

@Entity
@Table(name = "veterinarians", schema = "vet")
public class Veterinarians {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "organization_id")
    private UUID organizationId;

    @Column(name = "staff_member_id")
    private UUID staffMemberId;

    @Column(name = "license_no")
    private String licenseNo;

    @Column(name = "license_authority")
    private String licenseAuthority;

    @Column(name = "graduation_year")
    private Short graduationYear;

    @Column(name = "specialty")
    private String specialty;

    @Column(name = "is_active")
    private Boolean isActive;

    @Column(name = "created_at")
    private OffsetDateTime createdAt;

    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getOrganizationId() { return organizationId; }
    public void setOrganizationId(UUID organizationId) { this.organizationId = organizationId; }
    public UUID getStaffMemberId() { return staffMemberId; }
    public void setStaffMemberId(UUID staffMemberId) { this.staffMemberId = staffMemberId; }
    public String getLicenseNo() { return licenseNo; }
    public void setLicenseNo(String licenseNo) { this.licenseNo = licenseNo; }
    public String getLicenseAuthority() { return licenseAuthority; }
    public void setLicenseAuthority(String licenseAuthority) { this.licenseAuthority = licenseAuthority; }
    public Short getGraduationYear() { return graduationYear; }
    public void setGraduationYear(Short graduationYear) { this.graduationYear = graduationYear; }
    public String getSpecialty() { return specialty; }
    public void setSpecialty(String specialty) { this.specialty = specialty; }
    public Boolean getIsActive() { return isActive; }
    public void setIsActive(Boolean isActive) { this.isActive = isActive; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }
}
