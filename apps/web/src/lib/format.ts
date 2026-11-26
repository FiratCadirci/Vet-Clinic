import dayjs from 'dayjs';
import 'dayjs/locale/tr';

dayjs.locale('tr');

const tl = new Intl.NumberFormat('tr-TR', { style: 'currency', currency: 'TRY' });

/** Kuruş → "1.234,50 ₺" */
export const money = (kurus: number | null | undefined) => tl.format((kurus ?? 0) / 100);
/** "12,5" TL girişini kuruşa çevirir. */
export const toKurus = (tlValue: number | string) => Math.round(Number(String(tlValue).replace(',', '.')) * 100);
/** Baz puan → "%20" */
export const percent = (bp: number) => `%${(bp / 100).toLocaleString('tr-TR')}`;

export const date = (d: string | Date | null | undefined) => (d ? dayjs(d).format('DD.MM.YYYY') : '—');
export const dateTime = (d: string | Date | null | undefined) => (d ? dayjs(d).format('DD.MM.YYYY HH:mm') : '—');
export const time = (d: string | Date | null | undefined) => (d ? dayjs(d).format('HH:mm') : '—');

export const SPECIES: Record<string, string> = {
  dog: 'Köpek',
  cat: 'Kedi',
  bird: 'Kuş',
  rabbit: 'Tavşan',
  other: 'Diğer',
};
export const SEX: Record<string, string> = { male: 'Erkek', female: 'Dişi', unknown: 'Bilinmiyor' };

export const APPOINTMENT_STATUS: Record<string, { label: string; color: string }> = {
  requested: { label: 'Onay bekliyor', color: 'yellow' },
  booked: { label: 'Planlandı', color: 'cyan' },
  confirmed: { label: 'Onaylandı', color: 'blue' },
  checked_in: { label: 'Geldi', color: 'teal' },
  in_progress: { label: 'Muayenede', color: 'indigo' },
  completed: { label: 'Tamamlandı', color: 'green' },
  cancelled: { label: 'İptal', color: 'gray' },
  no_show: { label: 'Gelmedi', color: 'red' },
};

export const EXAM_STATUS: Record<string, string> = {
  intake: 'Kabul',
  examination: 'İnceleme',
  diagnosis: 'Tanı',
  plan: 'Tedavi planı',
  closed: 'Kapandı',
};

export const PRODUCT_CATEGORY: Record<string, string> = {
  drug: 'İlaç',
  food: 'Mama',
  vaccine: 'Aşı',
  supply: 'Sarf malzeme',
};

export const ageFrom = (birthDate: string | null | undefined) => {
  if (!birthDate) return '—';
  const months = dayjs().diff(dayjs(birthDate), 'month');
  return months < 12 ? `${months} ay` : `${Math.floor(months / 12)} yaş`;
};
