package org.codeflow.veterineryproject.auth.security;

public final class Permission {

    public static final String CLIENTS_READ = "clients.read";
    public static final String CLIENTS_WRITE = "clients.write";
    public static final String APPOINTMENTS_MANAGE = "appointments.manage";
    public static final String CLINICAL_READ = "clinical.read";
    public static final String CLINICAL_WRITE = "clinical.write";
    public static final String CLINICAL_DELETE = "clinical.delete";
    public static final String DIAGNOSTICS_MANAGE = "diagnostics.manage";
    public static final String INVENTORY_READ = "inventory.read";
    public static final String INVENTORY_WRITE = "inventory.write";
    public static final String BILLING_MANAGE = "billing.manage";
    public static final String USERS_MANAGE = "users.manage";
    public static final String REPORTS_READ = "reports.read";
    public static final String AUDIT_READ = "audit.read";
    public static final String CLINIC_ROLES_MANAGE = "clinic_roles.manage";
    public static final String VACCINE_PROTOCOLS_MANAGE = "vaccine_protocols.manage";
    public static final String SUBSCRIPTIONS_READ = "subscriptions.read";
    public static final String COMMISSIONS_READ = "commissions.read";

    private Permission() {
    }
}

