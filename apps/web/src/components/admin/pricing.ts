/**
 * Backend ile aynı formül (canlı önizleme için; Java `billing/service/LinePricing` ve V28 invoice_lines üretilmiş
 * sütunları): fiyatlar KDV hariç; brüt = miktar × birim; net = brüt − indirim; KDV = round(net × oran / 10000)
 * satır bazında; satır toplamı = net + KDV. Eşlik ortak vektörlerle test edilir (pricing.test.ts).
 */
export interface PriceInput {
  quantity: number;
  unitPriceKurus: number;
  discountKurus: number;
  taxRateBasisPoints: number;
}

export interface PricedLine extends PriceInput {
  grossKurus: number;
  netKurus: number;
  taxKurus: number;
  lineTotalKurus: number;
  /** İndirim brütü aşıyorsa backend 400 döner; önizlemede işaretlenir. */
  invalid: boolean;
}

export function priceLine(input: PriceInput): PricedLine {
  const grossKurus = input.quantity * input.unitPriceKurus;
  const invalid = input.discountKurus > grossKurus;
  const netKurus = Math.max(0, grossKurus - input.discountKurus);
  const taxKurus = Math.round((netKurus * input.taxRateBasisPoints) / 10000);
  return { ...input, grossKurus, netKurus, taxKurus, lineTotalKurus: netKurus + taxKurus, invalid };
}

export function saleTotals(lines: PricedLine[]) {
  const subtotalKurus = lines.reduce((s, l) => s + l.grossKurus, 0);
  const discountKurus = lines.reduce((s, l) => s + l.discountKurus, 0);
  const taxKurus = lines.reduce((s, l) => s + l.taxKurus, 0);
  return { subtotalKurus, discountKurus, taxKurus, totalKurus: subtotalKurus - discountKurus + taxKurus };
}
