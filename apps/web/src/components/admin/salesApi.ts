/**
 * Satış ve fatura ekranlarının Java API bağlantısı (/v1/billing, /v1/commissions, /v1/products, /v1/inventory/stock,
 * /v1/services). Backend TL (numeric) ve yüzde kullanır; ekranlar kuruş ve baz puanla çalıştığı için dönüşüm burada.
 * Fatura durumları: DRAFT → taslak, ISSUED / PARTIALLY_PAID / PAID / OVERDUE → kesildi, VOID → iptal.
 */
import { api, type Page } from '../../lib/api';
import { toKurus } from '../../lib/format';
import type { CatalogService } from './orgTypes';
import type {
  ClinicService,
  Commission,
  PaymentStatus,
  Product,
  ProductCategory,
  SaleDetail,
  SaleListRow,
  SaleStatus,
  StockRow,
} from './types';

// ------------------------------------------------------------------ Java DTO'ları

interface InvoiceSummaryDto {
  id: string;
  invoiceNo: string;
  status: string;
  clinicId: string;
  clientId: string;
  clientName: string | null;
  animalId: string | null;
  issuedAt: string | null;
  grandTotal: number;
  paidTotal: number;
  balance: number;
  createdAt: string;
  animalName: string | null;
}

interface LineDto {
  id: string;
  lineNo: number;
  lineType: 'SERVICE' | 'PRODUCT' | 'OTHER';
  serviceId: string | null;
  productId: string | null;
  lotId: string | null;
  description: string;
  quantity: number;
  unitPrice: number;
  taxRate: number;
  taxAmount: number;
  lineTotal: number;
  discountAmount: number;
  lotNumber: string | null;
}

interface PaymentDto {
  paymentId: string;
  paymentMethod: string;
  appliedAmount: number;
  paidAt: string;
}

interface InvoiceDto {
  invoice: InvoiceSummaryDto;
  subtotal: number;
  taxTotal: number;
  grossTotal: number;
  discountTotal: number;
  encounterId: string | null;
  lines: LineDto[];
  payments: PaymentDto[];
}

interface CommissionRecordDto {
  id: string;
  invoiceId: string;
  invoiceLineId: string;
  ratePercent: number;
  eligibleAmount: number;
  commissionAmount: number;
  calculatedAt: string;
}

interface ProductDto {
  id: string;
  barcode: string | null;
  name: string;
  productType: string;
  baseUnit: string;
  tracksLot: boolean;
  minimumStock: number;
  purchasePrice: number;
  salePrice: number | null;
  taxRate: number;
  active: boolean;
  createdAt: string;
}

interface StockItemDto {
  productId: string;
  name: string;
  productType: string;
  barcode: string | null;
  baseUnit: string;
  minimumStock: number;
  tracksLot: boolean;
  onHand: number;
  low: boolean;
  expiringSoon: boolean;
  lots: { lotId: string | null; lotNumber: string | null; expiresAt: string | null; quantity: number; expired: boolean; expiringSoon: boolean }[];
}

/** Satış detayı + faturanın taşıdığı adlar (sahip/hasta adı ayrı istek gerektirmez). */
export interface SaleView extends SaleDetail {
  ownerName: string | null;
  petName: string | null;
}

// ------------------------------------------------------------------ dönüşümler

const ISSUED_STATUSES = ['ISSUED', 'PARTIALLY_PAID', 'PAID', 'OVERDUE'];
const API_STATUS: Record<SaleStatus, string[]> = { draft: ['DRAFT'], issued: ISSUED_STATUSES, cancelled: ['VOID'] };

const saleStatus = (s: string): SaleStatus => (s === 'DRAFT' ? 'draft' : s === 'VOID' ? 'cancelled' : 'issued');
const paymentStatus = (s: string): PaymentStatus => (s === 'PAID' ? 'paid' : s === 'PARTIALLY_PAID' ? 'partial' : 'unpaid');
const bp = (percentValue: number) => Math.round(percentValue * 100);

const METHOD_FROM_API: Record<string, string> = { CASH: 'cash', CREDIT_CARD: 'card', BANK_TRANSFER: 'transfer' };
const METHOD_TO_API: Record<string, string> = { cash: 'CASH', card: 'CREDIT_CARD', transfer: 'BANK_TRANSFER', other: 'OTHER' };

/** Ürün tipi → ekran kategorisi (ilaç, mama, aşı; diğerleri sarf). */
export const productCategory = (type: string): ProductCategory =>
  type === 'MEDICATION' ? 'drug' : type === 'FOOD' ? 'food' : type === 'VACCINE' ? 'vaccine' : 'supply';

function toListRow(s: InvoiceSummaryDto, gross = 0, discount = 0, tax = 0): SaleListRow {
  return {
    id: s.id,
    number: s.invoiceNo,
    branchId: s.clinicId,
    ownerId: s.clientId,
    ownerName: s.clientName,
    petId: s.animalId,
    channel: 'clinic',
    status: saleStatus(s.status),
    paymentStatus: paymentStatus(s.status),
    subtotalKurus: toKurus(gross),
    discountKurus: toKurus(discount),
    taxKurus: toKurus(tax),
    totalKurus: toKurus(s.grandTotal),
    paidKurus: toKurus(s.paidTotal),
    createdAt: s.createdAt,
    issuedAt: s.issuedAt,
  };
}

function toSale(d: InvoiceDto): SaleView {
  const s = d.invoice;
  return {
    ...toListRow(s, d.grossTotal, d.discountTotal, d.taxTotal),
    ownerName: s.clientName,
    petName: s.animalName,
    examinationId: d.encounterId,
    createdBy: '',
    items: d.lines.map((l) => ({
      id: l.id,
      saleId: s.id,
      kind: l.lineType === 'PRODUCT' ? 'product' : 'service',
      productId: l.productId,
      serviceId: l.serviceId,
      lotId: l.lotId,
      description: l.description,
      lotNo: l.lotNumber,
      quantity: Number(l.quantity),
      unitPriceKurus: toKurus(l.unitPrice),
      discountKurus: toKurus(l.discountAmount),
      taxRateBasisPoints: bp(l.taxRate),
      taxKurus: toKurus(l.taxAmount),
      lineTotalKurus: toKurus(l.lineTotal),
    })),
    payments: d.payments.map((p) => ({
      id: p.paymentId,
      saleId: s.id,
      amountKurus: toKurus(p.appliedAmount),
      method: METHOD_FROM_API[p.paymentMethod] ?? 'other',
      receivedBy: '',
      createdAt: p.paidAt,
    })),
    commissions: [],
  };
}

function toCommission(c: CommissionRecordDto): Commission {
  return {
    id: c.id,
    saleId: c.invoiceId,
    saleItemId: c.invoiceLineId,
    ruleId: null,
    rateBasisPoints: bp(c.ratePercent),
    baseKurus: toKurus(c.eligibleAmount),
    amountKurus: toKurus(c.commissionAmount),
    createdAt: c.calculatedAt,
  };
}

export function toProduct(p: ProductDto): Product {
  return {
    id: p.id,
    name: p.name,
    category: productCategory(p.productType),
    barcode: p.barcode,
    unit: p.baseUnit,
    purchasePriceKurus: toKurus(p.purchasePrice ?? 0),
    salePriceKurus: toKurus(p.salePrice ?? 0),
    taxRateBasisPoints: bp(p.taxRate),
    minStock: Number(p.minimumStock),
    trackLots: p.tracksLot,
    active: p.active,
    createdAt: p.createdAt,
  };
}

function toStockRow(s: StockItemDto): StockRow {
  return {
    productId: s.productId,
    name: s.name,
    category: productCategory(s.productType),
    unit: s.baseUnit,
    barcode: s.barcode,
    minStock: Number(s.minimumStock),
    trackLots: s.tracksLot,
    total: Number(s.onHand),
    low: s.low,
    expiringSoon: s.expiringSoon,
    lots: s.lots
      .filter((l) => l.lotId)
      .map((l) => ({
        lotId: l.lotId!,
        lotNo: l.lotNumber ?? '',
        expiryDate: l.expiresAt,
        quantity: Number(l.quantity),
        expired: l.expired,
        expiringSoon: l.expiringSoon,
      })),
  };
}

const toService = (s: CatalogService): ClinicService => ({
  id: s.id,
  name: s.name,
  durationMinutes: s.durationMinutes,
  priceKurus: toKurus(s.price),
  taxRateBasisPoints: bp(s.taxRate),
  active: s.active,
});

// ------------------------------------------------------------------ uçlar

const INVOICES = '/v1/billing/invoices';

export interface SaleListQuery {
  branchId?: string;
  status?: SaleStatus;
  from?: string;
  to?: string;
  limit: number;
  offset: number;
}

export async function listSales(q: SaleListQuery) {
  const res = await api.get<Page<InvoiceSummaryDto>>(INVOICES, {
    clinicId: q.branchId,
    status: q.status ? API_STATUS[q.status] : undefined,
    from: q.from,
    to: q.to,
    page: Math.floor(q.offset / q.limit),
    size: q.limit,
  });
  return res.content.map((s) => toListRow(s));
}

export const getSale = async (id: string) => toSale(await api.get<InvoiceDto>(`${INVOICES}/${id}`));

/** Faturanın komisyon kayıtları (commissions.read). */
export async function getSaleCommissions(id: string) {
  const res = await api.get<Page<CommissionRecordDto>>('/v1/commissions', { invoiceId: id, size: 200 });
  return res.content.map(toCommission);
}

export const createSale = async (body: { clientId: string; animalId: string | null; lines: unknown[] }) =>
  toSale(await api.post<InvoiceDto>(INVOICES, body));

export const replaceSaleLines = async (id: string, lines: unknown[]) =>
  toSale(await api.put<InvoiceDto>(`${INVOICES}/${id}/lines`, { lines }));

export const issueSale = async (id: string) => toSale(await api.post<InvoiceDto>(`${INVOICES}/${id}/issue`));

/** Yalnız taslak iptal edilir. */
export const cancelSale = async (id: string) => toSale(await api.post<InvoiceDto>(`${INVOICES}/${id}/void`));

export const paySale = async (id: string, amountKurus: number, method: string) =>
  toSale(
    await api.post<InvoiceDto>(`${INVOICES}/${id}/payments`, {
      amount: (amountKurus / 100).toFixed(2),
      paymentMethod: METHOD_TO_API[method] ?? 'OTHER',
    }),
  );

export const openSalePdf = (id: string) => api.open(`${INVOICES}/${id}/pdf`);

export async function listSaleProducts(query: { search?: string; size: number }) {
  const res = await api.get<Page<ProductDto>>('/v1/products', { active: true, search: query.search, size: query.size });
  return res.content.map(toProduct);
}

export const listBranchStock = async () => (await api.get<StockItemDto[]>('/v1/inventory/stock')).map(toStockRow);

export const listSaleServices = async () => (await api.get<CatalogService[]>('/v1/services')).filter((s) => s.active).map(toService);
