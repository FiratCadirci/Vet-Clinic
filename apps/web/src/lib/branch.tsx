import { createContext, useContext, useMemo, useState, type ReactNode } from 'react';
import { useQuery } from '@tanstack/react-query';
import { api, setActiveClinic } from './api';
import { useAuth } from './auth';

export interface Branch {
  id: string;
  code: string;
  name: string;
  address: string | null;
  city: string | null;
  phone: string | null;
  email: string | null;
  active: boolean;
}

/** Backend ClinicResponse (şube). */
interface ClinicResponse {
  id: string;
  code: string;
  name: string;
  phone: string | null;
  email: string | null;
  city: string | null;
  district: string | null;
  addressLine: string | null;
  isActive: boolean;
}

const toBranch = (c: ClinicResponse): Branch => ({
  id: c.id,
  code: c.code,
  name: c.name,
  address: [c.addressLine, c.district].filter(Boolean).join(', ') || null,
  city: c.city,
  phone: c.phone,
  email: c.email,
  active: c.isActive,
});

interface BranchState {
  /** Kullanıcının rolü olan aktif şubeler (seçilebilir). */
  branches: Branch[];
  /** Kliniğin (aynı gruptaki) tüm şubeleri; raporlar ve listeler için. */
  allBranches: Branch[];
  /** Seçili şube; şubeye bağlı ekranlar (randevu, stok, satış) bunu kullanır ve istekler X-Clinic-Id ile gider. */
  branchId: string | null;
  branch: Branch | null;
  setBranchId: (id: string) => void;
}

const BranchContext = createContext<BranchState | null>(null);
const KEY = 'vet.branch';

export function BranchProvider({ children }: { children: ReactNode }) {
  const { user } = useAuth();
  const { data: allBranches = [] } = useQuery({
    queryKey: ['branches'],
    queryFn: async () => (await api.get<ClinicResponse[]>('/v1/clinics')).map(toBranch),
    enabled: user?.kind === 'staff',
  });
  const branches = useMemo(
    () => allBranches.filter((b) => b.active && user?.branchIds.includes(b.id)),
    [allBranches, user],
  );
  const [storedId, setBranchIdState] = useState<string | null>(() => localStorage.getItem(KEY));
  // Kayıtlı şube artık seçilebilir değilse (pasif / yetki kalktı / başka kullanıcı) ilk yetkili şube seçili sayılır.
  const fallback = user?.branchIds[0] ?? null;
  const branchId = user?.kind !== 'staff' ? null : storedId && user.branchIds.includes(storedId) ? storedId : (branches[0]?.id ?? fallback);
  // İstek başlığı render sırasında güncellenir; böylece aynı render'daki sorgular doğru şubeyle gider.
  setActiveClinic(branchId);

  const value = useMemo<BranchState>(
    () => ({
      branches,
      allBranches,
      branchId,
      branch: allBranches.find((b) => b.id === branchId) ?? null,
      setBranchId: (id) => {
        localStorage.setItem(KEY, id);
        setActiveClinic(id);
        setBranchIdState(id);
      },
    }),
    [branches, allBranches, branchId],
  );
  return <BranchContext.Provider value={value}>{children}</BranchContext.Provider>;
}

export function useBranch() {
  const ctx = useContext(BranchContext);
  if (!ctx) throw new Error('BranchProvider eksik');
  return ctx;
}
