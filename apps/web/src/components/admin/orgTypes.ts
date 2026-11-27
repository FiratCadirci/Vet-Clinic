/**
 * Klinik kaydı, personel, şube, hizmet ve çalışma saati ekranlarının backend (Spring Boot) DTO'ları.
 * Para alanları TL (numeric), oranlar yüzde; tarih-saatler "yyyy-MM-dd HH:mm" biçimindedir.
 */
import dayjs from 'dayjs';
import type { Role } from '../../lib/auth';

/** GET /auth/signup/plans */
export interface PlanOption {
  id: string;
  code: string;
  name: string;
  billingPeriod: 'MONTHLY' | 'YEARLY';
  price: number;
  currency: string;
  maxBranches: number;
  maxUsers: number;
}

export interface ClinicRoleAssignment {
  id: string;
  clinicId: string;
  clinicName: string;
  roleId: string;
  roleCode: string;
  roleName: string;
  validFrom: string;
  validTo: string | null;
}

/** GET /v1/users (UserResponse) */
export interface UserRow {
  id: string;
  email: string;
  displayName: string;
  phone: string | null;
  active: boolean;
  mustChangePassword: boolean;
  lastLoginAt: string | null;
  createdAt: string;
  clinicRoles: ClinicRoleAssignment[];
}

/** GET /v1/roles, yetki matrisindeki roller */
export interface RoleRow {
  id: string;
  code: string;
  name: string;
  description: string | null;
  systemRole: boolean;
  permissions: string[];
}

export interface PermissionMatrixData {
  permissions: { code: string; description: string }[];
  roles: RoleRow[];
}

export type InvitationStatus = 'PENDING' | 'ACCEPTED' | 'REVOKED' | 'EXPIRED';

/** GET/POST /v1/staff-invitations (davet kodu yanıtta yoktur; e-postayla gider) */
export interface Invitation {
  id: string;
  email: string;
  roleId: string;
  roleCode: string;
  roleName: string;
  clinicIds: string[];
  status: InvitationStatus;
  invitedByUserId: string | null;
  invitedByName: string | null;
  expiresAt: string;
  acceptedAt: string | null;
  revokedAt: string | null;
  createdAt: string;
}

/** POST /auth/invitations/preview */
export interface InvitationPreview {
  organizationName: string;
  email: string;
  roleCode: string;
  roleName: string;
  expiresAt: string;
}

/** GET /v1/organization */
export interface OrganizationProfile {
  id: string;
  code: number;
  name: string;
  legalName: string | null;
  taxOffice: string | null;
  taxNumber: string | null;
  phone: string | null;
  email: string | null;
  addressLine: string | null;
  subscription: {
    status: 'TRIAL' | 'ACTIVE' | 'PAST_DUE' | 'CANCELLED' | 'EXPIRED';
    planCode: string;
    planName: string;
    billingPeriod: 'MONTHLY' | 'YEARLY';
    currentPeriodEnd: string;
    maxBranches: number;
    maxUsers: number;
  } | null;
  usage: { activeBranches: number; activeUsers: number };
}

/** GET /v1/services */
export interface CatalogService {
  id: string;
  code: string;
  name: string;
  category: string | null;
  durationMinutes: number;
  price: number;
  taxRate: number;
  active: boolean;
}

/** GET /v1/working-hours — dayOfWeek: 0 = Pazar … 6 = Cumartesi */
export interface WorkingHourRow {
  id: string;
  clinicId: string;
  clinicName: string;
  veterinarianId: string;
  userId: string;
  veterinarianName: string;
  dayOfWeek: number;
  startTime: string;
  endTime: string;
}

/** GET /v1/working-hours/veterinarians */
export interface VetOption {
  veterinarianId: string;
  userId: string;
  displayName: string;
  licenseNo: string;
}

const ROLE_ORDER: Role[] = ['clinic_admin', 'branch_manager', 'veterinarian', 'receptionist'];

/** Bugün geçerli rol atamaları (valid_to bugün veya sonrası). */
export const activeAssignments = (u: UserRow) =>
  u.clinicRoles.filter((r) => !r.validTo || !dayjs(r.validTo).isBefore(dayjs(), 'day'));

/** Personel ekranı görünümü: en yetkili rol ve çalıştığı şubeler. */
export interface StaffView {
  id: string;
  fullName: string;
  email: string;
  phone: string | null;
  active: boolean;
  createdAt: string;
  role: string | null;
  roleId: string | null;
  branchIds: string[];
}

export function toStaffView(u: UserRow): StaffView {
  const roles = activeAssignments(u);
  const top =
    ROLE_ORDER.map((code) => roles.find((r) => r.roleCode === code)).find(Boolean) ?? roles[0] ?? null;
  return {
    id: u.id,
    fullName: u.displayName,
    email: u.email,
    phone: u.phone,
    active: u.active,
    createdAt: u.createdAt,
    role: top?.roleCode ?? null,
    roleId: top?.roleId ?? null,
    branchIds: [...new Set(roles.map((r) => r.clinicId))],
  };
}
