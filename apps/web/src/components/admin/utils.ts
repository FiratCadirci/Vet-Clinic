/** Yönetim ekranlarının ortak yardımcıları (bileşen dışı). */
import dayjs from 'dayjs';
import { useState } from 'react';

// ------------------------------------------------------------------ para / oran

/** NumberInput değeri ('' olabilir) → sayı. */
export const num = (v: number | string | null | undefined) => {
  if (v === '' || v === null || v === undefined) return 0;
  const n = typeof v === 'number' ? v : Number(String(v).replace(/\./g, '').replace(',', '.'));
  return Number.isFinite(n) ? n : 0;
};
export const kurusToTl = (k: number) => k / 100;
export const bpToPercent = (bp: number) => bp / 100;
export const percentToBp = (p: number | string) => Math.round(num(p) * 100);

// ------------------------------------------------------------------ tarih aralığı

export type Range = [string | null, string | null];

/** Varsayılan: son `days` gün (bugün dahil). */
export function useDateRange(days = 30) {
  return useState<Range>(() => [
    dayjs()
      .subtract(days - 1, 'day')
      .format('YYYY-MM-DD'),
    dayjs().format('YYYY-MM-DD'),
  ]);
}

/** `from`/`to` yalnız tarih (YYYY-MM-DD; backend `to`yu gün sonuna kadar dahil sayar): stok, satış, komisyon uçları. */
export const dateOnlyQuery = ([from, to]: Range) => ({ from: from ?? undefined, to: to ?? undefined });

/**
 * Rapor / denetim uçları ISO tarih-saat ister ve `to` hariçtir: başlangıç günün başı,
 * bitiş seçilen günün ertesi gün başı (Türkiye saatiyle).
 */
export const isoRangeQuery = ([from, to]: Range) => ({
  from: from ? dayjs(from).startOf('day').format('YYYY-MM-DDTHH:mm:ssZ') : undefined,
  to: to ? dayjs(to).add(1, 'day').startOf('day').format('YYYY-MM-DDTHH:mm:ssZ') : undefined,
});

// ------------------------------------------------------------------ sayfalama

/** Basit sayfalama: limit/offset; tam sayfa geldiyse sonraki sayfa olabilir. */
export function usePager(limit = 50) {
  const [page, setPage] = useState(0);
  return { page, setPage, limit, offset: page * limit, reset: () => setPage(0) };
}
