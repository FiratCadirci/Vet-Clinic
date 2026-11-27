/**
 * Muayene ekranlarının veri katmanı (Spring Boot API). Backend DTO'ları ekranların kullandığı tiplere (types.ts)
 * çevrilir. Yollar: /v1/encounters, /v1/encounter-flows, /v1/encounters/{id}/prescriptions|attachments|treatments.
 *
 * Durum anlamı (backend): status = son TAMAMLANAN adım. Muayene kaydının açılması kabul adımıdır; doldurulacak adım
 * durumdan sonraki adımdır. Son adım (plan) tamamlandığında yalnızca kapatma kalır.
 */
import { api, type Page } from '../../lib/api';
import type { Attachment, ExamDetail, ExamField, ExamFlow, ExamKind, ExamListItem, ExamStatus, ExamStep, Prescription } from './types';

// ---------------------------------------------------------------- backend DTO'ları
export type EncounterStatusDto = 'ADMISSION' | 'EXAMINATION' | 'DIAGNOSIS' | 'PLAN' | 'CLOSED';

export interface TreatmentDto {
  id: string;
  encounterId: string;
  itemType: 'PROCEDURE' | 'MEDICATION';
  name: string;
  productId: string | null;
  notes: string | null;
  performedAt: string;
}

export interface EncounterDto {
  id: string;
  encounterNo: string;
  animalId: string;
  animalName: string | null;
  patientNo: string | null;
  clinicId: string;
  appointmentId: string | null;
  attendingVetId: string;
  encounterType: string;
  status: EncounterStatusDto;
  startedAt: string;
  endedAt: string | null;
  chiefComplaint: string | null;
  history: string | null;
  examinationSummary: string | null;
  assessment: string | null;
  treatmentPlan: string | null;
  vetNotes: string | null;
  followUpAt: string | null;
  treatments: TreatmentDto[];
  weightKg: number | null;
  temperatureC: number | null;
  warnings: string[];
}

export interface EncounterFlowDto {
  code: string;
  label: string;
  encounterTypes: string[];
  steps: { status: Exclude<EncounterStatusDto, 'CLOSED'>; label: string; fields: string[]; required: string[] }[];
}

export interface PrescriptionItemDto {
  id: string;
  prescriptionId: string;
  prescriptionNo: string;
  encounterId: string;
  lineNo: number;
  productId: string;
  productName: string;
  dose: number | null;
  doseUnit: string | null;
  route: string | null;
  frequencyText: string;
  durationDays: number | null;
  quantity: number | null;
  instructions: string | null;
}

export interface AttachmentDto {
  id: string;
  encounterId: string;
  clinicId: string;
  documentType: string;
  title: string;
  originalFilename: string;
  mimeType: string;
  sizeBytes: number;
  uploadedBy: string;
  createdAt: string;
}

// ---------------------------------------------------------------- dönüşümler
const STATUS_FROM_API: Record<EncounterStatusDto, ExamStatus> = {
  ADMISSION: 'intake',
  EXAMINATION: 'examination',
  DIAGNOSIS: 'diagnosis',
  PLAN: 'plan',
  CLOSED: 'closed',
};
export const examStatusKey = (s: string): ExamStatus => STATUS_FROM_API[s as EncounterStatusDto] ?? 'intake';
export const apiExamStatus = (s: ExamStatus): EncounterStatusDto =>
  (Object.entries(STATUS_FROM_API).find(([, v]) => v === s)?.[0] ?? 'ADMISSION') as EncounterStatusDto;

const KIND_BY_TYPE: Record<string, ExamKind> = { VACCINATION: 'vaccination', EMERGENCY: 'emergency', SURGERY: 'surgery' };
export const examKind = (encounterType: string): ExamKind => KIND_BY_TYPE[encounterType] ?? 'general';
export const encounterTypeFor = (kind: ExamKind) =>
  ({ general: 'CONSULTATION', vaccination: 'VACCINATION', emergency: 'EMERGENCY', surgery: 'SURGERY' })[kind];

/** Backend alan adı → ekran alan adı (labels.ts FIELD_LABELS anahtarları). */
const FIELD_FROM_API: Record<string, ExamField> = {
  chiefComplaint: 'complaint',
  history: 'anamnesis',
  examinationSummary: 'physicalExam',
  assessment: 'diagnosis',
  weightKg: 'weightKg',
  temperatureC: 'temperatureC',
  treatmentPlan: 'treatmentPlan',
  procedures: 'procedures',
  vetNotes: 'vetNotes',
};
const fieldKeys = (names: string[]) => names.map((n) => FIELD_FROM_API[n]).filter((f): f is ExamField => !!f);

export function toExamFlow(f: EncounterFlowDto): ExamFlow {
  return {
    kind: examKind(f.encounterTypes[0] ?? ''),
    label: f.label,
    steps: f.steps.map((s) => ({
      step: STATUS_FROM_API[s.status] as ExamStep,
      label: s.label,
      fields: fieldKeys(s.fields),
      required: fieldKeys(s.required),
    })),
  };
}

const num = (v: number | null | undefined) => (v === null || v === undefined ? null : String(v));

/** Ekran alanları (complaint, anamnesis…) — ExamDetail'in Record<ExamField> kısmı. */
function fieldValues(e: EncounterDto): Record<ExamField, string | null> {
  const procedures = e.treatments.filter((t) => t.itemType === 'PROCEDURE').map((t) => t.name);
  return {
    complaint: e.chiefComplaint,
    anamnesis: e.history,
    physicalExam: e.examinationSummary,
    weightKg: num(e.weightKg),
    temperatureC: num(e.temperatureC),
    diagnosis: e.assessment,
    treatmentPlan: e.treatmentPlan,
    procedures: procedures.length ? procedures.join('\n') : null,
    vetNotes: e.vetNotes,
  };
}

export function toPrescription(p: PrescriptionItemDto): Prescription {
  const dose = p.dose !== null ? `${p.dose}${p.doseUnit ? ` ${p.doseUnit}` : ''} · ` : '';
  return {
    id: p.id,
    examinationId: p.encounterId,
    productId: p.productId,
    drugName: p.productName,
    dosage: `${dose}${p.frequencyText}`,
    durationDays: p.durationDays,
    instructions: p.instructions,
  };
}

export function toAttachment(a: AttachmentDto): Attachment {
  return {
    id: a.id,
    fileName: a.originalFilename,
    mimeType: a.mimeType,
    sizeBytes: a.sizeBytes,
    uploadedBy: a.uploadedBy,
    createdAt: a.createdAt,
  };
}

/** Detay ekranının modeli: types.ts ExamDetail + backend'e özgü alanlar (işlem kalemleri, numara). */
export interface Exam extends ExamDetail {
  encounterNo: string;
  encounterType: string;
  petName: string | null;
  patientNo: string | null;
  treatments: TreatmentDto[];
}

export function toExam(e: EncounterDto, flow: ExamFlow, prescriptions: PrescriptionItemDto[], attachments: AttachmentDto[]): Exam {
  return {
    ...fieldValues(e),
    id: e.id,
    encounterNo: e.encounterNo,
    encounterType: e.encounterType,
    petName: e.animalName,
    patientNo: e.patientNo,
    branchId: e.clinicId,
    petId: e.animalId,
    vetId: e.attendingVetId,
    appointmentId: e.appointmentId,
    kind: examKind(e.encounterType),
    status: examStatusKey(e.status),
    startedAt: e.startedAt,
    closedAt: e.endedAt,
    flow,
    prescriptions: prescriptions.map(toPrescription),
    attachments: attachments.map(toAttachment),
    treatments: e.treatments,
  };
}

/** Liste satırı. Yanıtta tür ve hekim adı yok: tür bilinmez ('other' değil boş), hekim adı ekranda çözülür. */
export interface ExamRow extends Omit<ExamListItem, 'species' | 'vetName'> {
  patientNo: string | null;
  kind: ExamKind;
}

export function toExamRow(e: EncounterDto): ExamRow {
  return {
    id: e.id,
    branchId: e.clinicId,
    petId: e.animalId,
    petName: e.animalName ?? '—',
    patientNo: e.patientNo,
    vetId: e.attendingVetId,
    appointmentId: e.appointmentId,
    status: examStatusKey(e.status),
    complaint: e.chiefComplaint,
    diagnosis: e.assessment,
    startedAt: e.startedAt,
    closedAt: e.endedAt,
    kind: examKind(e.encounterType),
  };
}

// ---------------------------------------------------------------- okuma
let flowsCache: Promise<EncounterFlowDto[]> | null = null;
/** Akış tanımları sabittir; oturum boyunca bir kez alınır. */
export function listFlows() {
  flowsCache ??= api.get<EncounterFlowDto[]>('/v1/encounter-flows').catch((e: unknown) => {
    flowsCache = null;
    throw e;
  });
  return flowsCache;
}

export async function flowFor(kind: ExamKind): Promise<ExamFlow | undefined> {
  const type = encounterTypeFor(kind);
  const f = (await listFlows()).find((x) => x.encounterTypes.includes(type));
  return f ? toExamFlow(f) : undefined;
}

export async function listExams(q: { animalId?: string; status?: ExamStatus; size?: number }) {
  const res = await api.get<Page<EncounterDto>>('/v1/encounters', {
    animalId: q.animalId,
    status: q.status ? apiExamStatus(q.status) : undefined,
    size: q.size ?? 200,
  });
  return res.content.map(toExamRow);
}

export async function getExam(id: string): Promise<Exam> {
  const [e, flows, prescriptions, attachments] = await Promise.all([
    api.get<EncounterDto>(`/v1/encounters/${id}`),
    listFlows(),
    api.get<PrescriptionItemDto[]>(`/v1/encounters/${id}/prescriptions`),
    api.get<AttachmentDto[]>(`/v1/encounters/${id}/attachments`),
  ]);
  const flow = flows.find((f) => f.encounterTypes.includes(e.encounterType)) ?? flows[0];
  return toExam(e, toExamFlow(flow), prescriptions, attachments);
}

// ---------------------------------------------------------------- yazma
export interface StartExamInput {
  petId: string;
  appointmentId: string | null;
  kind: ExamKind;
  complaint: string | null;
  anamnesis: string | null;
  weightKg: number | null;
  temperatureC: number | null;
}

/** Kabul adımı: kayıt açılır (randevudan ya da randevusuz). */
export function startExam(v: StartExamInput) {
  const body = {
    encounterType: encounterTypeFor(v.kind),
    chiefComplaint: v.complaint,
    history: v.anamnesis,
    weightKg: v.weightKg,
    temperatureC: v.temperatureC,
  };
  return v.appointmentId
    ? api.post<EncounterDto>(`/v1/appointments/${v.appointmentId}/encounter`, body)
    : api.post<EncounterDto>('/v1/encounters', { ...body, animalId: v.petId });
}

export type StepValues = Partial<Record<ExamField, string | number | null>>;

const text = (v: string | number | null | undefined) => (v === null || v === undefined || String(v).trim() === '' ? null : String(v).trim());
const decimal = (v: string | number | null | undefined) => (v === null || v === undefined || v === '' ? null : Number(v));

/** Adımın ana alanı dışındaki alanlar (anamnez, hekim notu, aşı ziyaretinde tanı…) PATCH ile yazılır. */
function sideFields(step: ExamStep, v: StepValues) {
  const body = {
    chiefComplaint: step === 'intake' ? text(v.complaint) : null,
    history: text(v.anamnesis),
    examinationSummary: step === 'examination' ? null : text(v.physicalExam),
    assessment: step === 'diagnosis' ? null : text(v.diagnosis),
    treatmentPlan: step === 'plan' ? null : text(v.treatmentPlan),
    vetNotes: text(v.vetNotes),
    weightKg: step === 'examination' ? null : decimal(v.weightKg),
    temperatureC: step === 'examination' ? null : decimal(v.temperatureC),
  };
  return Object.values(body).some((x) => x !== null) ? body : null;
}

/**
 * Adımı tamamlar: önce yan alanlar (PATCH), sonra adım ucu. Adım zaten tamamlanmışsa (son adım düzenlemesi)
 * yalnızca PATCH yapılır. Dönen uyarılar (ör. ateş) gösterilmek üzere verilir.
 */
export async function completeStep(exam: Exam, step: ExamStep, v: StepValues): Promise<string[]> {
  const alreadyDone = exam.status === step;
  if (alreadyDone) {
    await api.patch<EncounterDto>(`/v1/encounters/${exam.id}`, {
      ...sideFields(step, v),
      chiefComplaint: text(v.complaint),
      examinationSummary: text(v.physicalExam),
      assessment: text(v.diagnosis),
      treatmentPlan: text(v.treatmentPlan),
      weightKg: decimal(v.weightKg),
      temperatureC: decimal(v.temperatureC),
    });
    return [];
  }
  const side = sideFields(step, v);
  if (side) await api.patch<EncounterDto>(`/v1/encounters/${exam.id}`, side);
  let res: EncounterDto;
  switch (step) {
    case 'examination':
      res = await api.post<EncounterDto>(`/v1/encounters/${exam.id}/examination`, {
        examinationSummary: text(v.physicalExam),
        weightKg: decimal(v.weightKg),
        temperatureC: decimal(v.temperatureC),
      });
      break;
    case 'diagnosis':
      res = await api.post<EncounterDto>(`/v1/encounters/${exam.id}/diagnosis`, { assessment: text(v.diagnosis) });
      break;
    case 'plan':
      res = await api.post<EncounterDto>(`/v1/encounters/${exam.id}/plan`, { treatmentPlan: text(v.treatmentPlan) });
      break;
    default:
      // Kabul adımı kayıt açılırken tamamlanır; burada yalnız alanlar güncellenir.
      return [];
  }
  return res.warnings ?? [];
}

export async function closeExam(id: string, vetNotes: string | null) {
  const res = await api.post<EncounterDto>(`/v1/encounters/${id}/close`, { vetNotes });
  return res.warnings ?? [];
}

export const addProcedure = (examId: string, name: string) =>
  api.post<TreatmentDto>(`/v1/encounters/${examId}/treatments`, { itemType: 'PROCEDURE', name: name.slice(0, 200) });
export const deleteTreatment = (examId: string, treatmentId: string) => api.del(`/v1/encounters/${examId}/treatments/${treatmentId}`);

export interface PrescriptionInput {
  productId: string;
  frequencyText: string;
  durationDays: number | null;
  instructions: string | null;
}
export const addPrescription = (examId: string, v: PrescriptionInput) =>
  api.post<PrescriptionItemDto>(`/v1/encounters/${examId}/prescriptions`, v);
export const deletePrescription = (examId: string, itemId: string) => api.del(`/v1/encounters/${examId}/prescriptions/${itemId}`);

export async function uploadAttachment(examId: string, file: File) {
  const fd = new FormData();
  fd.append('file', file);
  return toAttachment(await api.post<AttachmentDto>(`/v1/encounters/${examId}/attachments`, fd));
}
export const attachmentContentPath = (attachmentId: string) => `/v1/attachments/${attachmentId}/content`;
