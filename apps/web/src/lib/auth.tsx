import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from 'react';
import { useQueryClient } from '@tanstack/react-query';
import { ApiError, api, sessionStore, setUnauthorizedHandler, type StoredSession } from './api';
import { AmbiguousOrganizationError, type Role } from './session';

export type { Role } from './session';


export interface ClinicSummary {
  id: string;
  code: string;
  name: string;
}

export interface Me {
  kind: 'platform_admin' | 'staff';
  id: string;
  email: string;
  fullName: string;
  /** Kiracı (organizasyon) kimliği; platform yöneticisinde null. */
  tenantId: string | null;
  /** En yetkili sistem rolü (rozet ve rol bazlı arayüz kararları için). */
  role: Role | null;
  roles: { id: string; code: string; name: string }[];
  /** Kullanıcının rolü olan şubeler (X-Clinic-Id yalnız bunlardan biri olabilir). */
  branchIds: string[];
  clinics: ClinicSummary[];
  permissions: string[];
  mustChangePassword: boolean;
}

/** Backend AuthLoginResponse (personel). */
export interface TenantAuthResponse {
  accessToken: string | null;
  refreshToken: string | null;
  organizationId: string;
  userId: string;
  displayName: string;
  email?: string;
  clinicIds: string[];
  permissions: string[];
  mustChangePassword: boolean;
  roles: { id: string; code: string; name: string }[];
  clinics: ClinicSummary[];
}

/** Backend PlatformAuthResponse. */
interface PlatformAuthResponse {
  accessToken: string | null;
  refreshToken: string | null;
  adminId: string;
  displayName: string;
  email: string;
}

interface SignInResponse {
  accountType: 'TENANT' | 'PLATFORM';
  panel: string;
  tenant: TenantAuthResponse | null;
  platform: PlatformAuthResponse | null;
}


/** Access token'daki yetkili şube listesi (`clinics` claim'i). /auth/me yalnız aktif şubeyi döndürdüğü için yeniden yüklemede bu kullanılır. */
function tokenClinicIds(token: string | undefined): string[] | null {
  try {
    const payload = JSON.parse(atob(token!.split('.')[1].replace(/-/g, '+').replace(/_/g, '/'))) as { clinics?: string[] };
    return Array.isArray(payload.clinics) ? payload.clinics : null;
  } catch {
    return null;
  }
}

const ROLE_ORDER: Role[] = ['clinic_admin', 'branch_manager', 'veterinarian', 'receptionist'];

function meFromTenant(t: TenantAuthResponse, email = ''): Me {
  const codes = t.roles.map((r) => r.code);
  return {
    kind: 'staff',
    id: t.userId,
    email: t.email ?? email,
    fullName: t.displayName,
    tenantId: t.organizationId,
    role: ROLE_ORDER.find((r) => codes.includes(r)) ?? null,
    roles: t.roles,
    branchIds: t.clinicIds,
    clinics: t.clinics,
    permissions: t.permissions,
    mustChangePassword: t.mustChangePassword,
  };
}

function meFromPlatform(p: PlatformAuthResponse): Me {
  return {
    kind: 'platform_admin',
    id: p.adminId,
    email: p.email,
    fullName: p.displayName,
    tenantId: null,
    role: null,
    roles: [],
    branchIds: [],
    clinics: [],
    permissions: [],
    mustChangePassword: false,
  };
}

interface AuthState {
  user: Me | null;
  loading: boolean;
  login: (email: string, password: string, organizationCode?: number) => Promise<Me>;
  /** Kayıt / davet kabulü gibi token dönen akışlardan sonra. */
  acceptTenantSession: (t: TenantAuthResponse, email?: string) => void;
  /** Oturumdaki kullanıcıyı yeniden yükler (ör. parola değişikliği sonrası). */
  reload: () => Promise<void>;
  logout: () => void;
  can: (permission: string) => boolean;
}

const AuthContext = createContext<AuthState | null>(null);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<Me | null>(null);
  const [loading, setLoading] = useState(Boolean(sessionStore.get()));
  const qc = useQueryClient();

  const logout = useCallback(() => {
    const s = sessionStore.get();
    if (s?.refreshToken) {
      // Refresh token sunucuda iptal edilir; hata olsa da yerel oturum kapanır.
      const path = s.kind === 'platform' ? '/platform/auth/logout' : '/auth/logout';
      void api.post(path, { refreshToken: s.refreshToken }).catch(() => undefined);
    }
    sessionStore.clear();
    setUser(null);
    qc.clear();
  }, [qc]);

  const reload = useCallback(async () => {
    const s = sessionStore.get();
    if (!s) return;
    if (s.kind === 'platform') setUser(meFromPlatform(await api.get<PlatformAuthResponse>('/platform/auth/me')));
    else {
      const me = meFromTenant(await api.get<TenantAuthResponse>('/auth/me'), s.email);
      // İstek sırasında token yenilenmiş olabilir; güncel oturumdan okunur.
      setUser({ ...me, branchIds: tokenClinicIds(sessionStore.get()?.accessToken) ?? me.branchIds });
    }
  }, []);

  useEffect(() => {
    setUnauthorizedHandler(() => {
      sessionStore.clear();
      setUser(null);
      qc.clear();
    });
    if (!sessionStore.get()) return;
    reload()
      // Yalnız geçersiz oturumda (401) oturum silinir; ağ hatası / yarıda kesilen istek oturumu kapatmamalı.
      .catch((e) => {
        if (e instanceof ApiError && e.status === 401) sessionStore.clear();
      })
      .finally(() => setLoading(false));
  }, [reload, qc]);

  const acceptTenantSession = useCallback((t: TenantAuthResponse, email?: string) => {
    sessionStore.set({ kind: 'tenant', accessToken: t.accessToken ?? '', refreshToken: t.refreshToken, email });
    setUser(meFromTenant(t, email));
  }, []);

  const login = useCallback(
    async (email: string, password: string, organizationCode?: number) => {
      let res: SignInResponse;
      try {
        res = await api.post<SignInResponse>('/auth/sign-in', { email, password, organizationCode });
      } catch (e) {
        if (e instanceof ApiError && e.code === 'AMBIGUOUS_ORGANIZATION') {
          const orgs = (e.body?.details?.organizations ?? []) as { code: number; name: string }[];
          throw new AmbiguousOrganizationError('Bu e-posta birden fazla klinikte kayıtlı; kliniği seçin.', orgs);
        }
        throw e;
      }
      if (res.accountType === 'PLATFORM' && res.platform) {
        const session: StoredSession = { kind: 'platform', accessToken: res.platform.accessToken ?? '', refreshToken: res.platform.refreshToken };
        sessionStore.set(session);
        const me = meFromPlatform(res.platform);
        setUser(me);
        return me;
      }
      const tenant = res.tenant!;
      if (!tenant.roles.length) {
        throw new Error('Hayvan sahipleri mobil uygulamayı kullanmalıdır.');
      }
      acceptTenantSession(tenant, email);
      return meFromTenant(tenant, email);
    },
    [acceptTenantSession],
  );

  const value = useMemo<AuthState>(
    () => ({
      user,
      loading,
      login,
      acceptTenantSession,
      reload,
      logout,
      can: (p) => Boolean(user?.permissions.includes(p)),
    }),
    [user, loading, login, acceptTenantSession, reload, logout],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth() {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error('AuthProvider eksik');
  return ctx;
}
