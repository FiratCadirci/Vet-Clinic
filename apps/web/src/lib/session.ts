/** Oturumla ilgili bileşen olmayan paylaşılan tanımlar (auth.tsx yalnız bileşen ve hook dışa aktarır). */

/** Sistem rolleri (backend V22 seed_system_roles). */
export type Role = 'clinic_admin' | 'branch_manager' | 'veterinarian' | 'receptionist';

export const ROLE_LABEL: Record<Role, string> = {
  clinic_admin: 'Klinik yöneticisi',
  branch_manager: 'Şube yöneticisi',
  veterinarian: 'Veteriner hekim',
  receptionist: 'Resepsiyon',
};

/** Aynı e-posta birden fazla klinikte kayıtlıysa backend 409 AMBIGUOUS_ORGANIZATION döner. */
export class AmbiguousOrganizationError extends Error {
  readonly organizations: { code: number; name: string }[];
  constructor(message: string, organizations: { code: number; name: string }[]) {
    super(message);
    this.organizations = organizations;
  }
}
