import type { ExamField, ExamKind } from './types';

export const EXAM_KIND: Record<ExamKind, { label: string; color: string }> = {
  general: { label: 'Genel muayene', color: 'teal' },
  vaccination: { label: 'Aşı ziyareti', color: 'grape' },
  emergency: { label: 'Acil', color: 'red' },
  surgery: { label: 'Cerrahi', color: 'indigo' },
};

export const EXAM_STATUS_COLOR: Record<string, string> = {
  intake: 'yellow',
  examination: 'orange',
  diagnosis: 'cyan',
  plan: 'blue',
  closed: 'green',
};

/** Backend `FIELD_LABELS` ile aynı. */
export const FIELD_LABELS: Record<ExamField, string> = {
  complaint: 'Şikayet',
  anamnesis: 'Anamnez',
  physicalExam: 'Fiziksel muayene',
  weightKg: 'Kilo (kg)',
  temperatureC: 'Vücut ısısı (°C)',
  diagnosis: 'Tanı',
  treatmentPlan: 'Tedavi planı',
  procedures: 'Uygulanan işlemler',
  vetNotes: 'Hekim notu',
};

export const NUMERIC_FIELDS: ExamField[] = ['weightKg', 'temperatureC'];

export const SPECIES_OPTIONS = [
  { value: 'dog', label: 'Köpek' },
  { value: 'cat', label: 'Kedi' },
  { value: 'bird', label: 'Kuş' },
  { value: 'rabbit', label: 'Tavşan' },
  { value: 'other', label: 'Diğer' },
];

export const SEX_OPTIONS = [
  { value: 'unknown', label: 'Bilinmiyor' },
  { value: 'male', label: 'Erkek' },
  { value: 'female', label: 'Dişi' },
];
