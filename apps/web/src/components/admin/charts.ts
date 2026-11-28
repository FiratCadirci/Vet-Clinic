import '@mantine/charts/styles.css';
import dayjs from 'dayjs';

/**
 * Grafiklerde sabit sırayla kullanılan kategorik renkler (açık zemin için doğrulandı: CVD ΔE ≥ 25).
 * Seri rengi varlığa bağlıdır (ör. abonelik hep 1., komisyon hep 2. renk), sıraya göre değişmez.
 */
export const SERIES_COLORS = ['teal.7', 'indigo.7', 'orange.7'] as const;

/** Grafik eksenleri için kuruş → kısa TL ("12,5 B ₺"). */
export const shortTl = (kurus: number) =>
  new Intl.NumberFormat('tr-TR', { notation: 'compact', maximumFractionDigits: 1 }).format(kurus / 100) + ' ₺';

export const dayLabel = (d: string) => dayjs(d).format('DD MMM');
export const monthLabel = (m: string) => dayjs(`${m}-01`).format('MMM YYYY');
