package org.codeflow.veterineryproject.user.model;

import jakarta.persistence.*;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.*;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "role_permissions", schema = "vet")
public class RolePermissions {

    @EmbeddedId
    private RolePermissionsId id;

    @Column(name = "created_at")
    private OffsetDateTime createdAt;

    public RolePermissionsId getId() { return id; }
    public void setId(RolePermissionsId id) { this.id = id; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }

    @Embeddable
    public static class RolePermissionsId implements Serializable {
        private static final long serialVersionUID = 1L;

        @Column(name = "organization_id")
        private UUID organizationId;

        @Column(name = "role_id")
        private UUID roleId;

        @Column(name = "permission_id")
        private UUID permissionId;

        public UUID getOrganizationId() { return organizationId; }
        public void setOrganizationId(UUID organizationId) { this.organizationId = organizationId; }
        public UUID getRoleId() { return roleId; }
        public void setRoleId(UUID roleId) { this.roleId = roleId; }
        public UUID getPermissionId() { return permissionId; }
        public void setPermissionId(UUID permissionId) { this.permissionId = permissionId; }
        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof RolePermissionsId)) return false;
            RolePermissionsId that = (RolePermissionsId) o;
            return Objects.equals(organizationId, that.organizationId) && Objects.equals(roleId, that.roleId) && Objects.equals(permissionId, that.permissionId);
        }
        @Override
        public int hashCode() {
            return Objects.hash(organizationId, roleId, permissionId);
        }
    }
}
