/**
 * Aşı ekranlarının veri katmanı (Spring Boot API). Ekrandaki "tür şablonu" backend'de o türün emekliye ayrılmamış
 * aşı protokolleridir (her kalem = bir protokol). Kalem değişikliği yeni taslak sürüm oluşturur ve onayı sıfırlar.
 * Yollar: /v1/vaccine-protocols, /v1/vaccinations, /v1/animals/{id}/vaccine-schedule, /v1/vaccinations/due, /v1/products.
 */
import { useQuery } from '@tanstack/react-query';
import { ApiError, api, type Page } from '../../lib/api';
import { useAuth } from '../../lib/auth';
import { SPECIES } from '../../lib/format';
import { listSpecies, speciesKey, type SpeciesDto } from './patientApi';
import type { DueVaccination, PetSchedule, Species, VaccineTemplate, VaccineTemplateItem, VaccineTemplateSummary } from './types';

// ---------------------------------------------------------------- backend DTO'ları
export interface ProtocolDoseDto {
  id: string;
  doseNumber: number;
  vaccineName: string;
  recommendedAgeDays: number | null;
  intervalFromPreviousDays: number | null;
  notes: string | null;
}

export interface ProtocolDto {
  id: string;
  code: string;
  name: string;
  speciesId: string;
  speciesCode: string;
  versionNo: number;
  status: 'DRAFT' | 'APPROVED' | 'RETIRED';
  boosterIntervalDays: number | null;
  notes: string | null;
  approvedBy: string | null;
  approvedAt: string | null;
  doses: ProtocolDoseDto[];
}

export interface VaccineRecordDto {
  id: string;
  animalId: string;
  encounterId: string;
  vaccineName: string;
  batchNumber: string | null;
  administeredAt: string;
  nextDueAt: string | null;
  nextDueSource: 'NONE' | 'TEMPLATE' | 'VET_OVERRIDE';
  vaccineProductId: string | null;
  lotId: string | null;
}

interface ScheduleDto {
  animalId: string;
  speciesCode: string;
  birthDate: string | null;
  pendingApproval: boolean;
  items: {
    protocolId: string;
    protocolCode: string;
    vaccineName: string;
    doseCount: number;
    doses: { doseNumber: number; status: 'DONE' | 'PLANNED' | 'OVERDUE'; plannedAt: string | null; administeredAt: string | null; recordId: string | null }[];
    booster: { dueAt: string; overdue: boolean } | null;
    next: { doseNumber: number; dueAt: string | null; overdue: boolean; booster: boolean } | null;
  }[];
  otherVaccinations: { recordId: string; vaccineName: string; administeredAt: string; nextDueAt: string | null }[];
  nextDue: { vaccineName: string; doseNumber: number; dueAt: string; overdue: boolean } | null;
}

interface DueDto {
  recordId: string;
  animalId: string;
  animalName: string;
  patientNo: string | null;
  clinicId: string;
  vaccineName: string;
  lastAdministeredAt: string;
  nextDueAt: string;
  status: 'OVERDUE' | 'UPCOMING';
  daysOverdue: number;
  speciesCode: string | null;
  ownerName: string | null;
  ownerPhone: string | null;
  doseNumber: number | null;
  reminderSentAt: string | null;
}

interface ProductDto {
  id: string;
  name: string;
  productType: string;
  tracksLot: boolean;
  active: boolean;
}

// ---------------------------------------------------------------- şablon (tür) ↔ protokol
const DAYS_PER_WEEK = 7;
const DEFAULT_INTERVAL = 21;

export const templateName = (species: Species) => `${SPECIES[species] ?? 'Tür'} aşı takvimi`;

export function toTemplateItem(p: ProtocolDto, templateId: string): VaccineTemplateItem {
  const first = p.doses[0];
  return {
    id: p.id,
    templateId,
    vaccineName: p.name,
    firstDoseAgeWeeks: Math.round((first?.recommendedAgeDays ?? 0) / DAYS_PER_WEEK),
    doseCount: p.doses.length,
    doseIntervalDays: p.doses[1]?.intervalFromPreviousDays ?? DEFAULT_INTERVAL,
    boosterIntervalDays: p.boosterIntervalDays ?? 365,
  };
}

/** Ekran kalemini protokol dozlarına çevirir: 1. doz ilk doz yaşıyla, sonrakiler doz aralığıyla. */
function protocolBody(i: Omit<VaccineTemplateItem, 'id' | 'templateId'>) {
  return {
    name: i.vaccineName,
    boosterIntervalDays: i.boosterIntervalDays,
    doses: Array.from({ length: i.doseCount }, (_, n) => ({
      doseNumber: n + 1,
      vaccineName: i.vaccineName,
      recommendedAgeDays: n === 0 ? i.firstDoseAgeWeeks * DAYS_PER_WEEK : null,
      intervalFromPreviousDays: n === 0 ? null : i.doseIntervalDays,
    })),
  };
}

const active = (p: ProtocolDto) => p.status !== 'RETIRED';

export async function listActiveProtocols(speciesId?: string) {
  const res = await api.get<Page<ProtocolDto>>('/v1/vaccine-protocols', { speciesId, size: 200 });
  return res.content.filter(active);
}

/** Tür başına özet; şablon kimliği tür kimliğidir. */
export async function listTemplates(): Promise<(VaccineTemplateSummary & { speciesId: string })[]> {
  const [species, protocols] = await Promise.all([listSpecies(), listActiveProtocols()]);
  return species
    .map((s) => ({ s, items: protocols.filter((p) => p.speciesId === s.id) }))
    .filter(({ items }) => items.length > 0)
    .map(({ s, items }) => summary(s, items));
}

function summary(s: SpeciesDto, items: ProtocolDto[]) {
  const approved = items.length > 0 && items.every((p) => p.status === 'APPROVED');
  const last = items.map((p) => p.approvedAt ?? '').sort().at(-1) || null;
  return {
    id: s.id,
    speciesId: s.id,
    species: speciesKey(s.code),
    name: templateName(speciesKey(s.code)),
    approvedBy: approved ? (items[0].approvedBy ?? null) : null,
    approvedAt: approved ? last : null,
    createdAt: '',
    itemCount: items.length,
  };
}

export interface TemplateDetail extends VaccineTemplate {
  speciesId: string;
  speciesCode: string;
  protocols: ProtocolDto[];
}

export async function getTemplate(speciesId: string): Promise<TemplateDetail> {
  const [species, protocols] = await Promise.all([listSpecies(), listActiveProtocols(speciesId)]);
  const s = species.find((x) => x.id === speciesId) ?? { id: speciesId, code: 'OTHER', name: '' };
  const sum = summary(s, protocols);
  protocols.sort((a, b) => (a.doses[0]?.recommendedAgeDays ?? 0) - (b.doses[0]?.recommendedAgeDays ?? 0) || a.name.localeCompare(b.name, 'tr'));
  return {
    ...sum,
    speciesCode: s.code,
    approved: !!sum.approvedAt,
    notice: 'Takvim türe göre önerilen değerlerle oluşturulur.',
    items: protocols.map((p) => toTemplateItem(p, speciesId)),
    protocols,
  };
}

export const generateTemplate = (speciesId: string) => api.post<ProtocolDto[]>('/v1/vaccine-protocols/generate', { speciesId });

/**
 * Türün fabrikasından taslak ister. Hazır takvimi olmayan türde (ör. "Diğer") backend 422 döner; bu durumda boş şablon
 * açılır ve klinik aşıları elle ekler (`empty: true`).
 */
export async function generateTemplateDraft(speciesId: string): Promise<{ empty: boolean }> {
  try {
    await generateTemplate(speciesId);
    return { empty: false };
  } catch (e) {
    if (e instanceof ApiError && e.status === 422) return { empty: true };
    throw e;
  }
}

type ItemInput = Omit<VaccineTemplateItem, 'id' | 'templateId'> & { id?: string };
const same = (a: VaccineTemplateItem, b: ItemInput) =>
  a.vaccineName === b.vaccineName &&
  a.firstDoseAgeWeeks === b.firstDoseAgeWeeks &&
  a.doseCount === b.doseCount &&
  (a.doseCount < 2 || a.doseIntervalDays === b.doseIntervalDays) &&
  a.boosterIntervalDays === b.boosterIntervalDays;

const randomCode = () => Math.random().toString(36).slice(2, 8).toUpperCase();

/** Kaydet: değişen kalem yeni taslak sürüm (PUT), yeni kalem POST, kaldırılan taslak silinir / onaylı emekliye ayrılır. */
export async function saveTemplate(t: TemplateDetail, items: ItemInput[]) {
  const keep = new Set(items.map((i) => i.id).filter(Boolean));
  for (const p of t.protocols.filter((x) => !keep.has(x.id))) {
    if (p.status === 'DRAFT') await api.del(`/v1/vaccine-protocols/${p.id}`);
    else await api.post(`/v1/vaccine-protocols/${p.id}/retire`);
  }
  for (const i of items) {
    const current = t.items.find((x) => x.id === i.id);
    if (current && same(current, i)) continue;
    if (current) await api.put(`/v1/vaccine-protocols/${current.id}`, protocolBody(i));
    else await api.post('/v1/vaccine-protocols', { ...protocolBody(i), code: `${t.speciesCode}-${randomCode()}`, speciesId: t.speciesId });
  }
}

/** Hekim onayı: türün tüm taslak kalemleri onaylanır. */
export async function approveTemplate(t: TemplateDetail) {
  for (const p of t.protocols.filter((x) => x.status === 'DRAFT')) {
    await api.post(`/v1/vaccine-protocols/${p.id}/approve`);
  }
}

// ---------------------------------------------------------------- takvim / vade
export async function getPetSchedule(petId: string): Promise<PetSchedule> {
  const d = await api.get<ScheduleDto>(`/v1/animals/${petId}/vaccine-schedule`);
  const species = speciesKey(d.speciesCode);
  const doseNo = (r: ScheduleDto['otherVaccinations'][number]) =>
    d.otherVaccinations.filter((o) => o.vaccineName.toLocaleLowerCase('tr') === r.vaccineName.toLocaleLowerCase('tr') && o.administeredAt <= r.administeredAt).length;
  return {
    petId: d.animalId,
    species,
    birthDate: d.birthDate,
    template: d.items.length ? { id: d.speciesCode, name: templateName(species), approvedAt: '' } : null,
    templatePendingApproval: d.pendingApproval,
    items: d.items.map((i) => ({
      templateItemId: i.protocolId,
      vaccineName: i.vaccineName,
      doseCount: i.doseCount,
      doses: i.doses.map((x) => ({
        doseNumber: x.doseNumber,
        status: x.status.toLowerCase() as 'done' | 'planned' | 'overdue',
        plannedAt: x.plannedAt,
        administeredAt: x.administeredAt ?? undefined,
        vaccinationId: x.recordId ?? undefined,
      })),
      booster: i.booster,
      next: i.next,
    })),
    otherVaccinations: d.otherVaccinations.map((o) => ({
      id: o.recordId,
      vaccineName: o.vaccineName,
      doseNumber: doseNo(o),
      administeredAt: o.administeredAt,
      nextDueAt: o.nextDueAt,
    })),
    nextDue: d.nextDue,
  };
}

export async function listDue(days: number): Promise<DueVaccination[]> {
  const res = await api.get<Page<DueDto>>('/v1/vaccinations/due', { status: 'ALL', days, size: 200 });
  return res.content.map((d) => ({
    id: d.recordId,
    branchId: d.clinicId,
    petId: d.animalId,
    petName: d.animalName,
    species: speciesKey(d.speciesCode),
    ownerId: '',
    ownerName: d.ownerName ?? '—',
    ownerPhone: d.ownerPhone ?? '',
    vaccineName: d.vaccineName,
    doseNumber: d.doseNumber ?? 0,
    administeredAt: d.lastAdministeredAt,
    nextDueAt: d.nextDueAt,
    reminderSentAt: d.reminderSentAt,
    overdue: d.status === 'OVERDUE',
  }));
}

// ---------------------------------------------------------------- aşı kaydı
export interface VaccinationInput {
  encounterId: string;
  protocolDoseId: string | null;
  vaccineName: string | null;
  batchNumber: string | null;
  certificateNo: string | null;
  administeredAt: string | undefined;
  productId: string | null;
  nextDueAt: string | null | undefined;
}
export const recordVaccination = (v: VaccinationInput) => api.post<VaccineRecordDto>('/v1/vaccinations', v);

// ---------------------------------------------------------------- ürün seçimi
export interface ProductOption {
  id: string;
  name: string;
  trackLots: boolean;
}

/** Stoktaki aktif ürünler (aşı kaydı: VACCINE, reçete: MEDICATION); inventory.read yetkisi gerekir. */
export function useProductOptions(productType: 'VACCINE' | 'MEDICATION') {
  const { can } = useAuth();
  return useQuery({
    queryKey: ['products', { productType, active: true }],
    queryFn: async () => {
      const res = await api.get<Page<ProductDto>>('/v1/products', { productType, active: true, size: 200 });
      return res.content.map<ProductOption>((p) => ({ id: p.id, name: p.name, trackLots: p.tracksLot }));
    },
    enabled: can('inventory.read'),
  });
}
