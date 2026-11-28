/** Satış kalemi taslakları: API kalemi ↔ düzenleme satırı dönüşümleri ve canlı fiyat önizlemesi. */
import { useQuery } from '@tanstack/react-query';
import { toKurus } from '../../lib/format';
import { priceLine, type PricedLine } from './pricing';
import { listBranchStock, listSaleProducts, listSaleServices } from './salesApi';
import type { ClinicService, Product, SaleItem } from './types';
import { bpToPercent, kurusToTl, num, percentToBp } from './utils';

/** Düzenlenen satış kalemi. Para alanları kullanıcının yazdığı TL değeridir (yazarken virgül kaybolmasın). */
export interface ItemDraft {
  key: string;
  kind: 'product' | 'service';
  productId: string | null;
  serviceId: string | null;
  description: string;
  quantity: number | string;
  unitPrice: number | string;
  discount: number | string;
  /** Serbest hizmet kaleminde yüzde olarak düzenlenir; diğerlerinde ürün/hizmet tanımından gelir. */
  taxPercent: number | string;
  /** Elle seçilen parti; boşsa kesimde FEFO (SKT'si en yakın parti) uygulanır. */
  lotId: string | null;
  lotNo: string | null;
  trackLots: boolean;
  unit: string | null;
}

let seq = 0;
export const newKey = () => `k${Date.now()}-${seq++}`;

export function draftFromProduct(p: Product): ItemDraft {
  return {
    key: newKey(),
    kind: 'product',
    productId: p.id,
    serviceId: null,
    description: p.name,
    quantity: 1,
    unitPrice: kurusToTl(p.salePriceKurus),
    discount: 0,
    taxPercent: bpToPercent(p.taxRateBasisPoints),
    lotId: null,
    lotNo: null,
    trackLots: p.trackLots,
    unit: p.unit,
  };
}

export function draftFromService(s: ClinicService): ItemDraft {
  return {
    key: newKey(),
    kind: 'service',
    productId: null,
    serviceId: s.id,
    description: s.name,
    quantity: 1,
    unitPrice: kurusToTl(s.priceKurus),
    discount: 0,
    taxPercent: bpToPercent(s.taxRateBasisPoints),
    lotId: null,
    lotNo: null,
    trackLots: false,
    unit: null,
  };
}

export function draftFromItem(i: SaleItem, products: Product[]): ItemDraft {
  const p = i.productId ? products.find((x) => x.id === i.productId) : undefined;
  return {
    key: newKey(),
    kind: i.kind,
    productId: i.productId,
    serviceId: i.serviceId,
    description: i.description,
    quantity: i.quantity,
    unitPrice: kurusToTl(i.unitPriceKurus),
    discount: kurusToTl(i.discountKurus),
    taxPercent: bpToPercent(i.taxRateBasisPoints),
    lotId: i.lotId,
    lotNo: i.lotNo,
    trackLots: p?.trackLots ?? Boolean(i.lotNo),
    unit: p?.unit ?? null,
  };
}

export const priceDraft = (d: ItemDraft): PricedLine =>
  priceLine({
    quantity: Math.max(0, Math.trunc(num(d.quantity))),
    unitPriceKurus: toKurus(num(d.unitPrice)),
    discountKurus: toKurus(num(d.discount)),
    taxRateBasisPoints: percentToBp(d.taxPercent),
  });

/** Kaydetmeden önce kullanıcıya gösterilecek doğrulama hatası (yoksa null). */
export function validateDrafts(drafts: ItemDraft[]): string | null {
  for (const d of drafts) {
    const q = num(d.quantity);
    if (!Number.isInteger(q) || q < 1) return `${d.description || 'Kalem'}: miktar en az 1 olmalı`;
    if (d.kind === 'service' && !d.serviceId && !d.description.trim()) return 'Serbest kalem için açıklama girin';
    if (priceDraft(d).invalid) return `${d.description}: indirim satır tutarını aşamaz`;
  }
  return null;
}

const tl = (kurus: number) => (kurus / 100).toFixed(2);

/**
 * API'ye gönderilecek fatura satırları (POST /v1/billing/invoices, PUT /v1/billing/invoices/:id/lines):
 * ürün → PRODUCT, katalog hizmeti → SERVICE, serbest kalem → OTHER. Fiyat ve indirim TL, KDV yüzde.
 */
export function draftsToPayload(drafts: ItemDraft[]) {
  return drafts.map((d) => {
    const p = priceDraft(d);
    const common = {
      description: d.description.trim(),
      quantity: p.quantity,
      unitPrice: tl(p.unitPriceKurus),
      discountAmount: tl(p.discountKurus),
      taxRate: (p.taxRateBasisPoints / 100).toString(),
    };
    if (d.kind === 'product') {
      return { lineType: 'PRODUCT', productId: d.productId, lotId: d.trackLots ? d.lotId : null, ...common };
    }
    return d.serviceId ? { lineType: 'SERVICE', serviceId: d.serviceId, ...common } : { lineType: 'OTHER', ...common };
  });
}

export function useSaleCatalog(branchId: string | null) {
  const services = useQuery({ queryKey: ['services', 'sale'], queryFn: listSaleServices });
  const products = useQuery({
    queryKey: ['products', { active: true, size: 200 }, 'sale'],
    queryFn: () => listSaleProducts({ size: 200 }),
  });
  const stock = useQuery({ queryKey: ['stock', branchId, 'sale'], queryFn: listBranchStock, enabled: Boolean(branchId) });
  return { services: services.data ?? [], products: products.data ?? [], stock: stock.data ?? [] };
}
