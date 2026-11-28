import { describe, expect, it } from 'vitest'
import { priceLine, saleTotals, type PriceInput } from './pricing'

const line = (o: Partial<PriceInput>): PriceInput => ({ quantity: 1, unitPriceKurus: 10000, discountKurus: 0, taxRateBasisPoints: 2000, ...o })

describe('priceLine (canlı önizleme)', () => {
  it('KDV hariç fiyat: brüt, net, KDV ve KDV dahil satır toplamı', () => {
    expect(priceLine(line({ quantity: 3, unitPriceKurus: 12550, discountKurus: 650 }))).toMatchObject({
      grossKurus: 37650,
      netKurus: 37000,
      taxKurus: 7400,
      lineTotalKurus: 44400,
      invalid: false,
    })
  })

  it('KDV satır bazında yuvarlanır (yarım kuruş yukarı)', () => {
    expect(priceLine(line({ unitPriceKurus: 1, taxRateBasisPoints: 5000 })).taxKurus).toBe(1) // 0,5 → 1
    expect(priceLine(line({ unitPriceKurus: 1, taxRateBasisPoints: 4999 })).taxKurus).toBe(0)
    expect(priceLine(line({ unitPriceKurus: 333, taxRateBasisPoints: 100 })).taxKurus).toBe(3) // 3,33 → 3
  })

  it('KDV oranı 0 olabilir', () => {
    expect(priceLine(line({ taxRateBasisPoints: 0 }))).toMatchObject({ taxKurus: 0, lineTotalKurus: 10000 })
  })

  it('indirim brütü aşarsa satır geçersiz işaretlenir ve tutarlar negatife düşmez', () => {
    const p = priceLine(line({ unitPriceKurus: 1000, discountKurus: 1500 }))
    expect(p).toMatchObject({ invalid: true, netKurus: 0, taxKurus: 0, lineTotalKurus: 0 })
  })

  it('indirim brüte eşitse geçerlidir (ücretsiz satır)', () => {
    expect(priceLine(line({ discountKurus: 10000 }))).toMatchObject({ invalid: false, lineTotalKurus: 0 })
  })
})

describe('saleTotals', () => {
  it('ara toplam − indirim + KDV = genel toplam = Σ satır toplamı', () => {
    const lines = [line({ quantity: 2, unitPriceKurus: 4500, discountKurus: 500 }), line({ unitPriceKurus: 19999, taxRateBasisPoints: 1000 })]
    const priced = lines.map(priceLine)
    const t = saleTotals(priced)
    expect(t).toEqual({ subtotalKurus: 28999, discountKurus: 500, taxKurus: 1700 + 2000, totalKurus: 28999 - 500 + 3700 })
    expect(t.totalKurus).toBe(priced.reduce((s, l) => s + l.lineTotalKurus, 0))
  })

  it('boş satış sıfırdır', () => {
    expect(saleTotals([])).toEqual({ subtotalKurus: 0, discountKurus: 0, taxKurus: 0, totalKurus: 0 })
  })
})

/**
 * Önizleme backend'deki hesapla aynı olmalı. Test vektörleri Java tarafıyla ortak tek dosyadadır
 * (apps/api/src/test/resources/billing/pricing-vectors.json): aynı dosyayı Java LinePricingTest ve DB üretilmiş
 * sütunlarını doğrulayan BillingSalesIT de okur. Dosya dinamik içe aktarılır ki web'in `tsc -b` programına girmesin.
 */
describe('backend (Java LinePricing + DB) ile ortak vektörler', () => {
  type Amount = string
  interface VectorLine {
    name?: string
    quantity: Amount
    unitPrice: Amount
    discountAmount: Amount
    taxRate: Amount
    expected?: { gross: Amount; discount: Amount; net: Amount; tax: Amount; total: Amount }
  }
  interface Vectors {
    lines: VectorLine[]
    sale: { lines: VectorLine[]; expected: { gross: Amount; discount: Amount; tax: Amount; total: Amount } }
    invalid: VectorLine
  }
  const vectorsUrl = new URL('../../../../api/src/test/resources/billing/pricing-vectors.json', import.meta.url).href
  const load = async () => ((await import(/* @vite-ignore */ vectorsUrl)) as { default: Vectors }).default

  const kurus = (tl: Amount) => Math.round(Number(tl) * 100)
  const input = (v: VectorLine): PriceInput => ({
    quantity: Number(v.quantity),
    unitPriceKurus: kurus(v.unitPrice),
    discountKurus: kurus(v.discountAmount),
    taxRateBasisPoints: Math.round(Number(v.taxRate) * 100),
  })

  it('her satır vektörü backend ile birebir aynı (brüt, net, KDV, toplam)', async () => {
    const vectors = await load()
    expect(vectors.lines.length).toBeGreaterThan(5)
    for (const v of vectors.lines) {
      const p = priceLine(input(v))
      const e = v.expected!
      expect(p.grossKurus, `${v.name} brüt`).toBe(kurus(e.gross))
      expect(p.discountKurus, `${v.name} indirim`).toBe(kurus(e.discount))
      expect(p.netKurus, `${v.name} net`).toBe(kurus(e.net))
      expect(p.taxKurus, `${v.name} KDV`).toBe(kurus(e.tax))
      expect(p.lineTotalKurus, `${v.name} toplam`).toBe(kurus(e.total))
      expect(p.invalid).toBe(false)
    }
  })

  it('satış toplamları backend ile aynı', async () => {
    const { sale } = await load()
    const t = saleTotals(sale.lines.map((l) => priceLine(input(l))))
    expect(t).toEqual({
      subtotalKurus: kurus(sale.expected.gross),
      discountKurus: kurus(sale.expected.discount),
      taxKurus: kurus(sale.expected.tax),
      totalKurus: kurus(sale.expected.total),
    })
  })

  it('backend 422 ile reddettiği satırı (indirim > brüt) önizleme geçersiz işaretler', async () => {
    const { invalid } = await load()
    expect(priceLine(input(invalid)).invalid).toBe(true)
  })
})
