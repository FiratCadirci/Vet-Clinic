/**
 * Hasta / sahip / randevu ekranlarının veri katmanı (Spring Boot API). Backend DTO'ları burada ekranların
 * kullandığı tiplere (types.ts) çevrilir; böylece bileşenler aynı kalır, yalnız bu dosya API'yi bilir.
 * Yollar: /v1/patients (liste + geçmiş), /v1/clients, /v1/animals, /v1/species, /v1/appointments.
 */
import dayjs from 'dayjs';
import { ApiError, api, type Page } from '../../lib/api';
import type { Appointment, AppointmentStatus, ExamStatus, Owner, OwnerListItem, Pet, PetDetail, PetHistory, PetListItem, Sex, Species } from './types';

// ---------------------------------------------------------------- backend DTO'ları
export interface PatientSummaryDto {
  id: string;
  patientNo: string;
  name: string;
  speciesId: string;
  speciesCode: string;
  speciesName: string;
  breedId: string | null;
  breedName: string | null;
  sex: string;
  birthDate: string | null;
  deceasedAt: string | null;
  profileNotes: string | null;
  microchipNo: string | null;
  registeredClinicId: string | null;
  ownerId: string | null;
  ownerName: string | null;
  ownerPhone: string | null;
  createdAt: string;
}

export interface ClientDto {
  id: string;
  clientType: 'PERSON' | 'BUSINESS';
  displayName: string;
  firstName: string | null;
  lastName: string | null;
  phone: string | null;
  email: string | null;
  address: { addressLine: string; city: string | null; district: string | null } | null;
  active: boolean;
  createdAt: string;
}

export interface ClientListItemDto extends ClientDto {
  animalCount: number;
}

export interface HealthSummaryDto {
  activeAllergies: { id: string; substance: string; reaction: string | null }[];
  identifiers: { id: string; identifierType: string; identifierValue: string }[];
}

export interface SpeciesDto {
  id: string;
  code: string;
  name: string;
}

export interface BreedDto {
  id: string;
  speciesId: string;
  name: string;
}

export interface AppointmentDto {
  id: string;
  appointmentNo: string;
  clinicId: string;
  clientId: string;
  clientName: string;
  animalId: string;
  animalName: string;
  veterinarianId: string | null;
  veterinarianName: string | null;
  appointmentType: string;
  status: string;
  startsAt: string;
  endsAt: string;
  reason: string | null;
  cancellationReason: string | null;
  createdAt: string;
}

export interface PatientHistoryDto {
  content: {
    type: 'ENCOUNTER' | 'VACCINATION' | 'APPOINTMENT';
    id: string;
    occurredAt: string;
    endedAt: string | null;
    clinicId: string;
    clinicName: string | null;
    status: string | null;
    title: string;
    veterinarianName: string | null;
    chiefComplaint: string | null;
    assessment: string | null;
    treatmentPlan: string | null;
    doseNumber: number | null;
    batchNumber: string | null;
    nextDueAt: string | null;
  }[];
  hiddenCount: number;
  hiddenByType: { encounters: number; vaccinations: number; appointments: number };
  clinicalAccess: boolean;
}

export interface BookingOptionsDto {
  services: { id: string; code: string; name: string; durationMinutes: number | null; price: number }[];
  veterinarians: { id: string; fullName: string }[];
}

export interface AvailabilityDto {
  durationMinutes: number;
  slots: { startsAt: string; endsAt: string; time: string }[];
}

// ---------------------------------------------------------------- dönüşümler
const SPECIES_BY_CODE: Record<string, Species> = { DOG: 'dog', CAT: 'cat', BIRD: 'bird', RABBIT: 'rabbit' };
export const speciesKey = (code: string | null | undefined): Species => SPECIES_BY_CODE[code ?? ''] ?? 'other';

const SEX_FROM_API: Record<string, Sex> = { MALE: 'male', FEMALE: 'female' };
export const sexKey = (sex: string | null | undefined): Sex => SEX_FROM_API[sex ?? ''] ?? 'unknown';
export const apiSex = (sex: Sex) => (sex === 'male' ? 'MALE' : sex === 'female' ? 'FEMALE' : 'UNKNOWN');

/** "REQUESTED" → "requested" (ekran durum anahtarları küçük harf). */
export const statusKey = (status: string) => status.toLowerCase() as AppointmentStatus;
export const apiStatus = (status: string) => status.toUpperCase();

const EXAM_STATUS_FROM_API: Record<string, ExamStatus> = {
  ADMISSION: 'intake',
  EXAMINATION: 'examination',
  DIAGNOSIS: 'diagnosis',
  PLAN: 'plan',
  CLOSED: 'closed',
};

export const APPOINTMENT_TYPE_LABEL: Record<string, string> = {
  EXAM: 'Muayene',
  VACCINATION: 'Aşı',
  SURGERY: 'Cerrahi',
  FOLLOW_UP: 'Kontrol',
  GROOMING: 'Bakım',
  LAB: 'Laboratuvar',
  IMAGING: 'Görüntüleme',
  OTHER: 'Diğer',
};

/** Ad soyadı "Ayşe Nur Yılmaz" → { firstName: "Ayşe Nur", lastName: "Yılmaz" }. */
export function splitName(fullName: string) {
  const parts = fullName.trim().split(/\s+/);
  const lastName = parts.length > 1 ? parts.pop()! : '';
  return { firstName: parts.join(' '), lastName };
}

export function toPetListItem(p: PatientSummaryDto): PetListItem {
  return {
    id: p.id,
    name: p.name,
    species: speciesKey(p.speciesCode),
    breed: p.breedName,
    sex: sexKey(p.sex),
    birthDate: p.birthDate,
    microchipNo: p.microchipNo,
    branchId: p.registeredClinicId ?? '',
    ownerId: p.ownerId ?? '',
    ownerName: p.ownerName ?? '—',
    ownerPhone: p.ownerPhone ?? '',
  };
}

export function toOwner(c: ClientDto): Owner {
  const a = c.address;
  return {
    id: c.id,
    // Mobil hesap bağlantısı (FG-B3) bu uçlarda dönmüyor.
    userId: null,
    fullName: c.displayName,
    phone: c.phone ?? '',
    email: c.email,
    address: a ? [a.addressLine, a.district, a.city].filter(Boolean).join(', ') : null,
    nationalId: null,
    createdAt: c.createdAt,
  };
}

export function toOwnerListItem(c: ClientListItemDto): OwnerListItem {
  return {
    id: c.id,
    fullName: c.displayName,
    phone: c.phone ?? '',
    email: c.email,
    hasMobileAccount: false,
    petCount: c.animalCount,
  };
}

export function toPet(p: PatientSummaryDto, health?: HealthSummaryDto): Pet {
  const allergies = health?.activeAllergies ?? [];
  return {
    ...toPetListItem(p),
    allergies: allergies.length ? allergies.map((a) => a.substance).join(', ') : null,
    healthNotes: p.profileNotes,
    deceased: !!p.deceasedAt,
    createdAt: p.createdAt,
    speciesId: p.speciesId,
    breedId: p.breedId,
    allergyItems: allergies.map((a) => ({ id: a.id, substance: a.substance })),
  };
}

export function toAppointment(a: AppointmentDto, branchName: (id: string) => string): Appointment {
  return {
    id: a.id,
    branchId: a.clinicId,
    branchName: branchName(a.clinicId),
    vetId: a.veterinarianId ?? '',
    vetName: a.veterinarianName ?? 'Hekim atanmamış',
    petId: a.animalId,
    petName: a.animalName,
    species: '',
    ownerId: a.clientId,
    ownerName: a.clientName,
    ownerPhone: '',
    serviceId: '',
    serviceName: APPOINTMENT_TYPE_LABEL[a.appointmentType] ?? a.appointmentType,
    startsAt: a.startsAt,
    endsAt: a.endsAt,
    status: statusKey(a.status),
    note: a.reason,
    cancelReason: a.cancellationReason,
    createdBy: '',
    createdAt: a.createdAt,
  };
}

export function toPetHistory(h: PatientHistoryDto, pet: Pet): PetHistory {
  const branch = (i: PatientHistoryDto['content'][number]) => i.clinicName ?? 'Diğer şube';
  return {
    pet,
    examinations: h.content
      .filter((i) => i.type === 'ENCOUNTER')
      .map((i) => ({
        id: i.id,
        branchId: i.clinicId,
        branchName: branch(i),
        vetName: i.veterinarianName ?? '—',
        status: EXAM_STATUS_FROM_API[i.status ?? ''] ?? 'intake',
        complaint: i.chiefComplaint,
        diagnosis: i.assessment,
        treatmentPlan: i.treatmentPlan,
        startedAt: i.occurredAt,
        closedAt: i.endedAt,
      })),
    vaccinations: h.content
      .filter((i) => i.type === 'VACCINATION')
      .map((i) => ({
        id: i.id,
        branchId: i.clinicId,
        branchName: branch(i),
        vaccineName: i.title,
        doseNumber: i.doseNumber ?? 0,
        lotNo: i.batchNumber,
        administeredAt: i.occurredAt,
        nextDueAt: i.nextDueAt,
      })),
    appointments: h.content
      .filter((i) => i.type === 'APPOINTMENT')
      .map((i) => ({
        id: i.id,
        branchId: i.clinicId,
        branchName: branch(i),
        startsAt: i.occurredAt,
        status: statusKey(i.status ?? ''),
      })),
    hiddenFromOtherBranches: h.hiddenCount,
    hiddenByType: {
      examinations: h.hiddenByType.encounters,
      vaccinations: h.hiddenByType.vaccinations,
      appointments: h.hiddenByType.appointments,
    },
    clinicalAccess: h.clinicalAccess,
  };
}

// ---------------------------------------------------------------- okuma
export const PAGE_SIZE_MAX = 200;

export async function listPets(q: string | undefined, page: number, size: number) {
  const res = await api.get<Page<PatientSummaryDto>>('/v1/patients', { q, page, size });
  return res.content.map(toPetListItem);
}

export async function listOwners(q: string | undefined, page: number, size: number) {
  const res = await api.get<Page<ClientListItemDto>>('/v1/clients', { q, page, size });
  return res.content.map(toOwnerListItem);
}

/** Hasta kartı: özet satırı + sağlık özeti (alerji, kimlik) + birincil sahip. */
export async function getPetDetail(id: string): Promise<PetDetail> {
  const [summary, health] = await Promise.all([
    api.get<PatientSummaryDto>(`/v1/patients/${id}`),
    api.get<HealthSummaryDto>(`/v1/animals/${id}/health-summary`),
  ]);
  const owner = summary.ownerId ? toOwner(await api.get<ClientDto>(`/v1/clients/${summary.ownerId}`)) : null;
  return { ...toPet(summary, health), owner };
}

export async function getOwnerDetail(id: string) {
  const [client, pets] = await Promise.all([
    api.get<ClientDto>(`/v1/clients/${id}`),
    api.get<Page<PatientSummaryDto>>('/v1/patients', { ownerId: id, size: PAGE_SIZE_MAX }),
  ]);
  return { ...toOwner(client), pets: pets.content.map(toPetListItem) };
}

export async function getPetHistory(pet: Pet): Promise<PetHistory> {
  const h = await api.get<PatientHistoryDto>(`/v1/patients/${pet.id}/history`, { size: PAGE_SIZE_MAX });
  return toPetHistory(h, pet);
}

export const listSpecies = () => api.get<SpeciesDto[]>('/v1/species');
export const listBreeds = (speciesId: string) => api.get<BreedDto[]>(`/v1/species/${speciesId}/breeds`);
export const getBookingOptions = (clinicId: string) => api.get<BookingOptionsDto>('/v1/appointments/booking-options', { clinicId });

export interface AppointmentQuery {
  clinicId?: string | null;
  from?: string;
  to?: string;
  vetId?: string | null;
  status?: string | null;
  animalId?: string;
  size?: number;
}

export async function listAppointments(q: AppointmentQuery, branchName: (id: string) => string) {
  const res = await api.get<Page<AppointmentDto>>('/v1/appointments', {
    clinicId: q.clinicId,
    startDateTime: q.from,
    endDateTime: q.to,
    vetId: q.vetId,
    status: q.status ? apiStatus(q.status) : undefined,
    animalId: q.animalId,
    size: q.size ?? PAGE_SIZE_MAX,
    sort: 'startsAt,asc',
  });
  return res.content.map((a) => toAppointment(a, branchName));
}

// ---------------------------------------------------------------- yazma
export interface OwnerInput {
  fullName: string;
  phone: string;
  email: string | null;
  address: string | null;
}

const ownerBody = (v: OwnerInput) => ({
  ...splitName(v.fullName),
  contact: { phone: v.phone, email: v.email },
  address: v.address ? { addressLine: v.address } : undefined,
});

/**
 * Yeni sahip. Aynı telefon/e-posta başka kayıtta varsa backend 409 DUPLICATE_CLIENT_CANDIDATE döner;
 * {@code confirmDuplicate} onayından sonra kayıt yine de oluşturulur.
 */
export const createOwner = (v: OwnerInput, confirmDuplicate = false) =>
  api.post<ClientDto>('/v1/clients', { clientType: 'PERSON', ...ownerBody(v), confirmDuplicate: confirmDuplicate || undefined }).then(toOwner);

export const updateOwner = (id: string, v: OwnerInput) => api.patch<ClientDto>(`/v1/clients/${id}`, ownerBody(v)).then(toOwner);

/** 409 mükerrer aday hatasındaki benzer kayıt adları. */
export function duplicateCandidates(err: unknown): string[] | null {
  if (!(err instanceof ApiError) || err.code !== 'DUPLICATE_CLIENT_CANDIDATE') return null;
  const list = (err.body?.details?.possibleDuplicates ?? []) as { displayName: string; phone: string | null }[];
  return list.map((d) => (d.phone ? `${d.displayName} (${d.phone})` : d.displayName));
}

export interface PetInput {
  name: string;
  speciesId: string;
  breedId: string | null;
  birthDate: string | null;
  sex: Sex;
  microchipNo: string | null;
  allergies: string | null;
  healthNotes: string | null;
  deceased: boolean;
}

const splitAllergies = (text: string | null) =>
  (text ?? '')
    .split(/[,\n;]/)
    .map((s) => s.trim())
    .filter(Boolean);

/** Kimlik ve alerjiler hastanın ayrı kayıtlarıdır; formdaki metin alanları bunlara dönüştürülür. */
async function syncHealth(petId: string, v: PetInput, current?: Pet) {
  const chip = v.microchipNo?.replace(/[\s-]/g, '') || null;
  if (chip && chip !== current?.microchipNo) {
    await api.post(`/v1/animals/${petId}/identifiers`, { identifierType: 'MICROCHIP', identifierValue: chip });
  }
  const wanted = splitAllergies(v.allergies);
  const existing = current?.allergyItems ?? [];
  const key = (s: string) => s.toLocaleLowerCase('tr');
  for (const substance of wanted) {
    if (!existing.some((a) => key(a.substance) === key(substance))) {
      await api.post(`/v1/animals/${petId}/allergies`, { substance });
    }
  }
  for (const a of existing) {
    if (!wanted.some((s) => key(s) === key(a.substance))) await api.post(`/v1/animals/${petId}/allergies/${a.id}/resolve`);
  }
}

export async function createPet(ownerId: string, v: PetInput): Promise<Pet> {
  const created = await api.post<{ id: string }>(`/v1/clients/${ownerId}/animals`, {
    name: v.name,
    speciesId: v.speciesId,
    breedId: v.breedId,
    sex: apiSex(v.sex),
    birthDate: v.birthDate,
    profileNotes: v.healthNotes,
  });
  await syncHealth(created.id, v);
  return toPet(await api.get<PatientSummaryDto>(`/v1/patients/${created.id}`));
}

export async function updatePet(pet: Pet, v: PetInput): Promise<Pet> {
  await api.patch(`/v1/animals/${pet.id}`, {
    name: v.name,
    breedId: v.breedId,
    sex: apiSex(v.sex),
    birthDate: v.birthDate,
    profileNotes: v.healthNotes ?? '',
  });
  await syncHealth(pet.id, v, pet);
  if (v.deceased && !pet.deceased) {
    await api.post(`/v1/animals/${pet.id}/deceased`, { deceasedAt: dayjs().format('YYYY-MM-DD') });
  }
  return toPet(await api.get<PatientSummaryDto>(`/v1/patients/${pet.id}`));
}

export interface AppointmentInput {
  animalId: string;
  veterinarianId: string;
  startsAt: string;
  durationMinutes: number;
  reason: string | null;
}

const plusMinutes = (iso: string, minutes: number) => dayjs(iso).add(minutes, 'minute').format();

/** Personelin oluşturduğu randevu doğrudan onaylanır (oluştur → onayla). */
export async function createAppointment(v: AppointmentInput, branchName: (id: string) => string) {
  const created = await api.post<AppointmentDto>('/v1/appointments', {
    animalId: v.animalId,
    veterinarianId: v.veterinarianId,
    appointmentType: 'EXAM',
    startsAt: v.startsAt,
    endsAt: plusMinutes(v.startsAt, v.durationMinutes),
    reason: v.reason,
    bookingChannel: 'STAFF',
  });
  const confirmed = await api.patch<AppointmentDto>(`/v1/appointments/${created.id}/confirm`);
  return toAppointment(confirmed, branchName);
}

export async function rescheduleAppointment(a: Appointment, startsAt: string, vetId: string | null, branchName: (id: string) => string) {
  const minutes = dayjs(a.endsAt).diff(dayjs(a.startsAt), 'minute');
  const res = await api.patch<AppointmentDto>(`/v1/appointments/${a.id}/reschedule`, {
    startsAt,
    endsAt: plusMinutes(startsAt, minutes),
    veterinarianId: vetId || undefined,
  });
  return toAppointment(res, branchName);
}

export const appointmentAction = (id: string, kind: 'confirm' | 'no-show' | 'check-in', branchName: (id: string) => string) =>
  api.patch<AppointmentDto>(`/v1/appointments/${id}/${kind}`).then((a) => toAppointment(a, branchName));

export const cancelAppointment = (id: string, cancellationReason: string, branchName: (id: string) => string) =>
  api.patch<AppointmentDto>(`/v1/appointments/${id}/cancel`, { cancellationReason }).then((a) => toAppointment(a, branchName));
