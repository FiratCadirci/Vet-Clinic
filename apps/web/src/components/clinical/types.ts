/** Klinik ekranlarının kullandığı API yanıt tipleri (backend controller/repository'lerinden). Tarihler ISO metin. */

export type Species = 'dog' | 'cat' | 'bird' | 'rabbit' | 'other';
export type Sex = 'male' | 'female' | 'unknown';
export type AppointmentStatus =
  | 'requested'
  | 'booked'
  | 'confirmed'
  | 'checked_in'
  | 'in_progress'
  | 'completed'
  | 'cancelled'
  | 'no_show';
export type ExamStatus = 'intake' | 'examination' | 'diagnosis' | 'plan' | 'closed';
export type ExamStep = Exclude<ExamStatus, 'closed'>;
export type ExamKind = 'general' | 'vaccination' | 'emergency' | 'surgery';
export type ExamField =
  | 'complaint'
  | 'anamnesis'
  | 'physicalExam'
  | 'weightKg'
  | 'temperatureC'
  | 'diagnosis'
  | 'treatmentPlan'
  | 'procedures'
  | 'vetNotes';

// ---------------------------------------------------------------- hasta / sahip
export interface PetListItem {
  id: string;
  name: string;
  species: Species;
  breed: string | null;
  sex: Sex;
  birthDate: string | null;
  microchipNo: string | null;
  branchId: string;
  ownerId: string;
  ownerName: string;
  ownerPhone: string;
}

export interface Owner {
  id: string;
  userId: string | null;
  fullName: string;
  phone: string;
  email: string | null;
  address: string | null;
  nationalId: string | null;
  createdAt: string;
}

export interface OwnerListItem {
  id: string;
  fullName: string;
  phone: string;
  email: string | null;
  hasMobileAccount: boolean;
  petCount: number;
}

export interface Pet {
  id: string;
  ownerId: string;
  branchId: string;
  name: string;
  species: Species;
  breed: string | null;
  birthDate: string | null;
  sex: Sex;
  microchipNo: string | null;
  allergies: string | null;
  healthNotes: string | null;
  deceased: boolean;
  createdAt: string;
  /** Düzenleme formu için (tür / ırk kimliği, aktif alerji kayıtları). */
  speciesId?: string;
  breedId?: string | null;
  allergyItems?: { id: string; substance: string }[];
}

export interface PetDetail extends Pet {
  owner: Owner | null;
}

export interface OwnerDetail extends Owner {
  pets: PetListItem[];
}

export interface PetHistory {
  pet: Pet;
  examinations: {
    id: string;
    branchId: string;
    branchName: string;
    vetName: string;
    status: ExamStatus;
    complaint: string | null;
    diagnosis: string | null;
    treatmentPlan: string | null;
    startedAt: string;
    closedAt: string | null;
  }[];
  vaccinations: {
    id: string;
    branchId: string;
    branchName: string;
    vaccineName: string;
    doseNumber: number;
    lotNo: string | null;
    administeredAt: string;
    nextDueAt: string | null;
  }[];
  appointments: { id: string; branchId: string; branchName: string; startsAt: string; status: AppointmentStatus }[];
  hiddenFromOtherBranches: number;
  hiddenByType?: { examinations: number; vaccinations: number; appointments: number };
  /** false: kullanıcının klinik kayıt (muayene/aşı) okuma yetkisi yok. */
  clinicalAccess?: boolean;
}

// ---------------------------------------------------------------- randevu
export interface Appointment {
  id: string;
  branchId: string;
  branchName: string;
  vetId: string;
  vetName: string;
  petId: string;
  petName: string;
  /** Randevu yanıtında tür yok; bilinmiyorsa boş. */
  species: Species | '';
  ownerId: string;
  ownerName: string;
  ownerPhone: string;
  serviceId: string;
  serviceName: string;
  startsAt: string;
  endsAt: string;
  status: AppointmentStatus;
  note: string | null;
  cancelReason: string | null;
  createdBy: string;
  createdAt: string;
}

export interface Availability {
  durationMinutes: number;
  slots: { startsAt: string; endsAt: string; time: string }[];
}

export interface ClinicService {
  id: string;
  name: string;
  durationMinutes: number;
  priceKurus: number;
  taxRateBasisPoints: number;
  active: boolean;
}

export interface StaffMember {
  id: string;
  email: string;
  fullName: string;
  phone: string | null;
  role: 'clinic_admin' | 'branch_manager' | 'vet' | 'reception';
  crossBranchAccess: boolean;
  active: boolean;
  branchIds: string[];
}

export interface WorkingHour {
  id: string;
  branchId: string;
  vetId: string;
  vetName: string;
  weekday: number;
  startTime: string;
  endTime: string;
}

// ---------------------------------------------------------------- muayene
export interface ExamListItem {
  id: string;
  branchId: string;
  petId: string;
  petName: string;
  species: Species;
  vetId: string;
  vetName: string;
  appointmentId: string | null;
  status: ExamStatus;
  complaint: string | null;
  diagnosis: string | null;
  startedAt: string;
  closedAt: string | null;
}

export interface FlowStep {
  step: ExamStep;
  label: string;
  fields: ExamField[];
  required: ExamField[];
}

export interface ExamFlow {
  kind: ExamKind;
  label: string;
  steps: FlowStep[];
}

export interface Prescription {
  id: string;
  examinationId: string;
  productId: string | null;
  drugName: string;
  dosage: string;
  durationDays: number | null;
  instructions: string | null;
}

export interface Attachment {
  id: string;
  fileName: string;
  mimeType: string;
  sizeBytes: number;
  uploadedBy: string;
  createdAt: string;
}

export interface ExamDetail extends Record<ExamField, string | null> {
  id: string;
  branchId: string;
  petId: string;
  vetId: string;
  appointmentId: string | null;
  kind: ExamKind;
  status: ExamStatus;
  startedAt: string;
  closedAt: string | null;
  flow: ExamFlow;
  prescriptions: Prescription[];
  attachments: Attachment[];
}

// ---------------------------------------------------------------- aşı
export interface DueVaccination {
  id: string;
  branchId: string;
  petId: string;
  petName: string;
  species: Species;
  ownerId: string;
  ownerName: string;
  ownerPhone: string;
  vaccineName: string;
  doseNumber: number;
  administeredAt: string;
  nextDueAt: string;
  reminderSentAt: string | null;
  overdue: boolean;
}

export interface VaccineTemplateSummary {
  id: string;
  species: Species;
  name: string;
  approvedBy: string | null;
  approvedAt: string | null;
  createdAt: string;
  itemCount: number;
}

export interface VaccineTemplateItem {
  id: string;
  templateId: string;
  vaccineName: string;
  firstDoseAgeWeeks: number;
  doseCount: number;
  doseIntervalDays: number;
  boosterIntervalDays: number;
}

export interface VaccineTemplate extends Omit<VaccineTemplateSummary, 'itemCount'> {
  approved: boolean;
  notice: string;
  items: VaccineTemplateItem[];
}

export interface PetSchedule {
  petId: string;
  species: Species;
  birthDate: string | null;
  template: { id: string; name: string; approvedAt: string } | null;
  templatePendingApproval: boolean;
  items: {
    templateItemId: string;
    vaccineName: string;
    doseCount: number;
    doses: {
      doseNumber: number;
      status: 'done' | 'planned' | 'overdue';
      plannedAt: string | null;
      administeredAt?: string;
      vaccinationId?: string;
    }[];
    booster: { dueAt: string; overdue: boolean } | null;
    next: { doseNumber: number; dueAt: string | null; overdue: boolean; booster: boolean } | null;
  }[];
  otherVaccinations: {
    id: string;
    vaccineName: string;
    doseNumber: number;
    administeredAt: string;
    nextDueAt: string | null;
  }[];
  nextDue: { vaccineName: string; doseNumber: number; dueAt: string; overdue: boolean } | null;
}

export interface Product {
  id: string;
  name: string;
  category: 'drug' | 'food' | 'vaccine' | 'supply';
  unit: string;
  trackLots: boolean;
  active: boolean;
  salePriceKurus: number;
}

// ---------------------------------------------------------------- panel
export interface ReportsOverview {
  examinations: number;
  appointments: Partial<Record<AppointmentStatus, number>>;
  occupancyRate: number;
  overdueVaccinations: number;
  sales: { count: number; totalKurus: number; paidKurus: number };
}

export interface StockAlerts {
  lowStock: { branchId: string; productId: string; name: string; total: number; minStock: number }[];
  expiring: {
    branchId: string;
    productId: string;
    name: string;
    lotNo: string;
    expiryDate: string | null;
    quantity: number;
    expired: boolean;
  }[];
}
