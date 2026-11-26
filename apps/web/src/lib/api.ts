/**
 * Backend (Spring Boot) REST istemcisi.
 * - Yollar `/api` önekine göre verilir: `/auth/...`, `/platform/...`, `/v1/...`. Vite geliştirme sunucusu /api'yi backend'e yönlendirir.
 * - Oturum: kısa ömürlü access token + refresh token (localStorage). 401'de bir kez yenilenir, olmazsa oturum kapanır.
 * - Şube: personel isteklerine seçili şube `X-Clinic-Id` başlığıyla eklenir (backend yetkisini token'daki şubelere göre doğrular).
 */
const SESSION_KEY = 'vet.session';

export type SessionKind = 'tenant' | 'platform';

export interface StoredSession {
  kind: SessionKind;
  accessToken: string;
  refreshToken: string | null;
  /** /auth/me e-posta döndürmediği için giriş e-postası saklanır (yalnız arayüzde gösterilir). */
  email?: string;
}

export const sessionStore = {
  get(): StoredSession | null {
    try {
      return JSON.parse(localStorage.getItem(SESSION_KEY) ?? 'null') as StoredSession | null;
    } catch {
      return null;
    }
  },
  set: (s: StoredSession) => localStorage.setItem(SESSION_KEY, JSON.stringify(s)),
  clear: () => localStorage.removeItem(SESSION_KEY),
};

/** Backend hata gövdesi: `{ status, code, message, path, details }`. */
export interface ApiErrorBody {
  status?: number;
  code?: string;
  message?: string;
  details?: Record<string, unknown>;
}

export class ApiError extends Error {
  readonly status: number;
  readonly code: string | undefined;
  readonly body: ApiErrorBody | null;
  constructor(status: number, message: string, body: ApiErrorBody | null) {
    super(message);
    this.status = status;
    this.code = body?.code;
    this.body = body;
  }
}

/** Sayfalı liste yanıtı (backend PageResponse). */
export interface Page<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  first: boolean;
  last: boolean;
}

type Query = Record<string, string | number | boolean | null | undefined | string[]>;

function buildUrl(path: string, query?: Query) {
  const url = new URL(`/api${path}`, window.location.origin);
  for (const [k, v] of Object.entries(query ?? {})) {
    if (Array.isArray(v)) v.forEach((item) => url.searchParams.append(k, item));
    else if (v !== undefined && v !== null && v !== '') url.searchParams.set(k, String(v));
  }
  return url.toString();
}

let activeClinicId: string | null = null;
/** BranchProvider seçili şubeyi buraya yazar; personel isteklerine X-Clinic-Id olarak eklenir. */
export const setActiveClinic = (id: string | null) => {
  activeClinicId = id;
};

let onUnauthorized: (() => void) | undefined;
export const setUnauthorizedHandler = (fn: () => void) => {
  onUnauthorized = fn;
};

let refreshing: Promise<boolean> | null = null;
/** Refresh token ile yeni access token alır. Eşzamanlı 401'ler tek bir yenileme isteğini bekler. */
function refreshSession(): Promise<boolean> {
  const s = sessionStore.get();
  if (!s?.refreshToken) return Promise.resolve(false);
  refreshing ??= (async () => {
    try {
      const res = await fetch(buildUrl(s.kind === 'platform' ? '/platform/auth/refresh' : '/auth/refresh'), {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ refreshToken: s.refreshToken }),
      });
      if (!res.ok) return false;
      const data = (await res.json()) as { accessToken: string; refreshToken?: string | null };
      sessionStore.set({ ...s, accessToken: data.accessToken, refreshToken: data.refreshToken ?? s.refreshToken });
      return true;
    } catch {
      return false;
    } finally {
      refreshing = null;
    }
  })();
  return refreshing;
}

function errorMessage(status: number, body: ApiErrorBody | null) {
  if (body?.message) return body.message;
  if (status === 403) return 'Bu işlem için yetkiniz yok.';
  if (status === 404) return 'Kayıt bulunamadı.';
  return `İstek başarısız (${status})`;
}

async function request<T>(method: string, path: string, opts: { query?: Query; body?: unknown } = {}, retried = false): Promise<T> {
  const headers: Record<string, string> = {};
  const session = sessionStore.get();
  if (session) headers.Authorization = `Bearer ${session.accessToken}`;
  if (session?.kind === 'tenant' && activeClinicId) headers['X-Clinic-Id'] = activeClinicId;
  let body: BodyInit | undefined;
  if (opts.body instanceof FormData) body = opts.body;
  else if (opts.body !== undefined) {
    headers['Content-Type'] = 'application/json';
    body = JSON.stringify(opts.body);
  }

  const res = await fetch(buildUrl(path, opts.query), { method, headers, body });
  if (res.status === 401 && session && !retried) {
    if (await refreshSession()) return request<T>(method, path, opts, true);
    onUnauthorized?.();
  }
  if (!res.ok) {
    const data = (await res.json().catch(() => null)) as ApiErrorBody | null;
    throw new ApiError(res.status, errorMessage(res.status, data), data);
  }
  if (res.status === 204) return undefined as T;
  const type = res.headers.get('content-type') ?? '';
  if (type.includes('application/json')) {
    const data: unknown = await res.json();
    // Bazı uçlar { success, data } zarfıyla döner (ApiResponse); içerik doğrudan verilir.
    if (data && typeof data === 'object' && 'success' in data && 'data' in data && 'timestamp' in data) {
      return (data as { data: T }).data;
    }
    return data as T;
  }
  return (await res.blob()) as T;
}

export const api = {
  get: <T>(path: string, query?: Query) => request<T>('GET', path, { query }),
  post: <T>(path: string, body?: unknown) => request<T>('POST', path, { body }),
  put: <T>(path: string, body?: unknown) => request<T>('PUT', path, { body }),
  patch: <T>(path: string, body?: unknown) => request<T>('PATCH', path, { body }),
  del: <T>(path: string) => request<T>('DELETE', path),
  /** PDF gibi dosyaları yeni sekmede açar. Sekme tıklama anında açılır (pop-up engelleyiciye takılmaz), içerik sonra yüklenir. */
  async open(path: string) {
    const tab = window.open('', '_blank');
    try {
      const blob = await request<Blob>('GET', path);
      const url = URL.createObjectURL(blob);
      if (tab) tab.location.href = url;
      else window.location.href = url;
    } catch (e) {
      tab?.close();
      throw e;
    }
  },
};
