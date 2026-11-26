package org.codeflow.veterineryproject.user.model;

import jakarta.persistence.*;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.*;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "user_roles", schema = "vet")
public class UserRoles {

    @EmbeddedId
    private UserRolesId id;

    @Column(name = "assigned_at")
    private OffsetDateTime assignedAt;

    @Column(name = "assigned_by")
    private UUID assignedBy;

    public UserRolesId getId() { return id; }
    public void setId(UserRolesId id) { this.id = id; }
    public OffsetDateTime getAssignedAt() { return assignedAt; }
    public void setAssignedAt(OffsetDateTime assignedAt) { this.assignedAt = assignedAt; }
    public UUID getAssignedBy() { return assignedBy; }
    public void setAssignedBy(UUID assignedBy) { this.assignedBy = assignedBy; }

    @Embeddable
    public static class UserRolesId implements Serializable {
        private static final long serialVersionUID = 1L;

        @Column(name = "organization_id")
        private UUID organizationId;

        @Column(name = "user_id")
        private UUID userId;

        @Column(name = "role_id")
        private UUID roleId;

        public UUID getOrganizationId() { return organizationId; }
        public void setOrganizationId(UUID organizationId) { this.organizationId = organizationId; }
        public UUID getUserId() { return userId; }
        public void setUserId(UUID userId) { this.userId = userId; }
        public UUID getRoleId() { return roleId; }
        public void setRoleId(UUID roleId) { this.roleId = roleId; }
        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof UserRolesId)) return false;
            UserRolesId that = (UserRolesId) o;
            return Objects.equals(organizationId, that.organizationId) && Objects.equals(userId, that.userId) && Objects.equals(roleId, that.roleId);
        }
        @Override
        public int hashCode() {
            return Objects.hash(organizationId, userId, roleId);
        }
    }
}
