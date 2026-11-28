/** Yönetim ekranlarının kullandığı API yanıt tipleri (backend repository/service dönüşlerinden). */
import type { Role } from '../../lib/auth';

export type ProductCategory = 'drug' | 'food' | 'vaccine' | 'supply';
export type MovementType = 'purchase' | 'sale' | 'usage' | 'adjustment' | 'transfer';
export type SaleStatus = 'draft' | 'issued' | 'cancelled';
export type PaymentStatus = 'unpaid' | 'partial' | 'paid';
export type PaymentMethod = 'cash' | 'card' | 'transfer' | 'other';
export type BillingPeriod = 'monthly' | 'yearly';
export type SubscriptionStatus = 'trial' | 'active' | 'past_due' | 'cancelled';

export interface Product {
  id: string;
  name: string;
  category: ProductCategory;
  barcode: string | null;
  unit: string;
  purchasePriceKurus: number;
  salePriceKurus: number;
  taxRateBasisPoints: number;
  minStock: number;
  trackLots: boolean;
  active: boolean;
  createdAt: string;
}

export interface StockLot {
  lotId: string;
  lotNo: string;
  expiryDate: string | null;
  quantity: number;
  expired: boolean;
  expiringSoon: boolean;
}

export interface StockRow {
  productId: string;
  name: string;
  category: ProductCategory;
  unit: string;
  barcode: string | null;
  minStock: number;
  trackLots: boolean;
  total: number;
  low: boolean;
  expiringSoon: boolean;
  lots: StockLot[];
}

export interface StockMovement {
  id: string;
  branchId: string;
  productId: string;
  productName: string;
  lotNo: string;
  type: MovementType;
  quantity: number;
  reference: string | null;
  userId: string;
  userName: string;
  createdAt: string;
}

export interface StockAlerts {
  lowStock: { branchId: string; productId: string; name: string; total: number; minStock: number }[];
  expiring: ({ branchId: string; productId: string; name: string } & StockLot)[];
}

export interface ClinicService {
  id: string;
  name: string;
  durationMinutes: number;
  priceKurus: number;
  taxRateBasisPoints: number;
  active: boolean;
}

export interface SaleListRow {
  id: string;
  number: string;
  branchId: string;
  ownerId: string | null;
  ownerName: string | null;
  petId: string | null;
  channel: 'clinic' | 'mobile';
  status: SaleStatus;
  paymentStatus: PaymentStatus;
  subtotalKurus: number;
  discountKurus: number;
  taxKurus: number;
  totalKurus: number;
  paidKurus: number;
  createdAt: string;
  issuedAt: string | null;
}

export interface SaleItem {
  id: string;
  saleId: string;
  kind: 'service' | 'product';
  productId: string | null;
  serviceId: string | null;
  description: string;
  lotId: string | null;
  lotNo: string | null;
  quantity: number;
  unitPriceKurus: number;
  discountKurus: number;
  taxRateBasisPoints: number;
  taxKurus: number;
  lineTotalKurus: number;
}

export interface Payment {
  id: string;
  saleId: string;
  amountKurus: number;
  method: string;
  receivedBy: string;
  createdAt: string;
}

export interface Commission {
  id: string;
  saleId: string;
  saleItemId: string;
  ruleId: string | null;
  rateBasisPoints: number;
  baseKurus: number;
  amountKurus: number;
  createdAt: string;
}

export interface SaleDetail extends Omit<SaleListRow, 'ownerName'> {
  examinationId: string | null;
  createdBy: string;
  items: SaleItem[];
  payments: Payment[];
  commissions: Commission[];
}

export interface OwnerListRow {
  id: string;
  fullName: string;
  phone: string;
  email: string | null;
}

export interface PetListRow {
  id: string;
  name: string;
  species: string;
  ownerId: string;
  ownerName: string;
}

export interface StaffMember {
  id: string;
  email: string;
  fullName: string;
  phone: string | null;
  role: Role;
  crossBranchAccess: boolean;
  active: boolean;
  createdAt: string;
  branchIds: string[];
}

export interface StaffInvite {
  id: string;
  email: string;
  role: Role;
  branchIds: string[];
  invitedBy: string;
  acceptedAt: string | null;
  expiresAt: string;
  createdAt: string;
}

export interface InviteCreated {
  id: string;
  email: string;
  role: Role;
  branchIds: string[];
  expiresAt: string;
  token: string;
  inviteUrl: string;
}

export interface PermissionMatrix {
  permissions: { key: string; label: string }[];
  roles: { role: Role; label: string; permissions: string[] }[];
  matrix: Record<string, Record<Role, boolean>>;
}

export interface WorkingHour {
  id: string;
  branchId: string;
  vetId: string;
  vetName: string;
  weekday: number;
  startTime: string;
  endTime: string;
}

export interface Clinic {
  id: string;
  name: string;
  slug: string;
  phone: string | null;
  email: string | null;
  address: string | null;
  taxNumber: string | null;
  createdAt: string;
  subscription: {
    status: SubscriptionStatus;
    period: BillingPeriod;
    renewsAt: string;
    planCode: string;
    planName: string;
    maxBranches: number;
    maxUsers: number;
  } | null;
}

export interface Plan {
  id: string;
  code: string;
  name: string;
  monthlyPriceKurus: number;
  yearlyPriceKurus: number;
  maxBranches: number;
  maxUsers: number;
  active: boolean;
}

export interface CurrentSubscription {
  subscription: {
    id: string;
    status: SubscriptionStatus;
    period: BillingPeriod;
    startedAt: string;
    renewsAt: string;
    cancelledAt: string | null;
    priceKurus: number | null;
  } | null;
  plan: Plan | null;
  usage: { branches: number; users: number; maxBranches: number; maxUsers: number };
}

export interface SubscriptionPayment {
  id: string;
  amountKurus: number;
  periodStart: string;
  periodEnd: string;
  paidAt: string;
  simulated: boolean;
}

/** GET /v1/audit-logs satırı (kim/ne/hangi kayıt/hangi şube; içerik yok). */
export interface AuditLog {
  id: number;
  occurredAt: string;
  actorUserId: string | null;
  userName: string | null;
  action: string;
  entityType: string;
  entityId: string | null;
  clinicId: string | null;
  clinicName: string | null;
  ipAddress: string | null;
}

export interface CommissionRule {
  id: string;
  category: ProductCategory | null;
  rateBasisPoints: number;
  active: boolean;
  createdAt: string;
}

export interface Tenant {
  id: string;
  name: string;
  slug: string;
  email: string | null;
  phone: string | null;
  createdAt: string;
  plan: { id: string; code: string; name: string; maxBranches: number; maxUsers: number } | null;
  subscription: { status: SubscriptionStatus; period: BillingPeriod; renewsAt: string; cancelledAt: string | null } | null;
  branchCount: number;
  userCount: number;
  subscriptionRevenueKurus: number;
  commissionRevenueKurus: number;
}

/** Komisyon satırı (klinik dökümü ve platform raporu ortak alanları). */
export interface CommissionLine {
  id: string;
  saleId: string;
  saleNumber: string;
  issuedAt: string | null;
  description: string;
  category: ProductCategory | null;
  quantity: number;
  baseKurus: number;
  rateBasisPoints: number;
  amountKurus: number;
  createdAt: string;
  branchId?: string;
  tenantId?: string;
  tenantName?: string;
}
