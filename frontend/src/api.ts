// Centralised API client: bearer auth, single-flight token refresh on 401, uniform error contract.
// Every request carries an X-Correlation-Id so a failing screen can be traced in the API logs.

export type Row = Record<string, any>;

export interface Page<T = Row> {
  content: T[];
  page: { size: number; number: number; totalElements: number; totalPages: number };
}

export interface Session {
  accessToken: string;
  refreshToken: string;
  roles: string[];
  email?: string;
}

export class ApiError extends Error {
  constructor(
    public status: number,
    message: string,
    public category?: string,
    public correlationId?: string,
  ) {
    super(message);
  }
}

const BASE = '/api/v1';
const KEY = 'connectone.session';
let session: Session | null = readSession();
let refreshing: Promise<boolean> | null = null;
const listeners = new Set<() => void>();

function readSession(): Session | null {
  try {
    const raw = localStorage.getItem(KEY);
    return raw ? (JSON.parse(raw) as Session) : null;
  } catch {
    return null;
  }
}

export function getSession(): Session | null {
  return session;
}

export function setSession(next: Session | null) {
  session = next;
  try {
    if (next) localStorage.setItem(KEY, JSON.stringify(next));
    else localStorage.removeItem(KEY);
  } catch {
    /* storage unavailable: keep the in-memory session */
  }
  listeners.forEach((l) => l());
}

export function subscribeSession(listener: () => void) {
  listeners.add(listener);
  return () => listeners.delete(listener);
}

const newId = () => (crypto.randomUUID ? crypto.randomUUID() : String(Date.now()) + Math.random().toString(16).slice(2));

/** Idempotency keys for create-style calls (recharge, booking, refund...). */
export const idempotencyKey = (prefix: string) => `${prefix}-${newId()}`;

async function refresh(): Promise<boolean> {
  if (!session) return false;
  refreshing ??= (async () => {
    try {
      const res = await fetch(`${BASE}/auth/refresh`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ refreshToken: session!.refreshToken }),
      });
      if (!res.ok) return false;
      const t = await res.json();
      setSession({ ...session!, accessToken: t.accessToken, refreshToken: t.refreshToken, roles: t.roles ?? session!.roles });
      return true;
    } catch {
      return false;
    } finally {
      refreshing = null;
    }
  })();
  return refreshing;
}

interface Options {
  method?: string;
  body?: unknown;
  query?: Record<string, string | number | boolean | undefined | null>;
  auth?: boolean;
}

export async function api<T = any>(path: string, opts: Options = {}, retried = false): Promise<T> {
  const qs = new URLSearchParams();
  Object.entries(opts.query ?? {}).forEach(([k, v]) => {
    if (v !== undefined && v !== null && v !== '') qs.set(k, String(v));
  });
  const url = `${BASE}${path}${qs.size ? `?${qs}` : ''}`;
  const headers: Record<string, string> = { 'X-Correlation-Id': newId() };
  if (opts.body !== undefined) headers['Content-Type'] = 'application/json';
  if (opts.auth !== false && session) headers.Authorization = `Bearer ${session.accessToken}`;

  let res: Response;
  try {
    res = await fetch(url, { method: opts.method ?? 'GET', headers, body: opts.body === undefined ? undefined : JSON.stringify(opts.body) });
  } catch {
    throw new ApiError(0, 'Cannot reach the ConnectOne API. Is the backend running?');
  }
  if (res.status === 401 && opts.auth !== false && !retried && (await refresh())) {
    return api<T>(path, opts, true);
  }
  if (res.status === 401 && opts.auth !== false) setSession(null);
  if (res.status === 204) return undefined as T;
  const text = await res.text();
  const data = text ? safeJson(text) : undefined;
  if (!res.ok) {
    throw new ApiError(res.status, data?.message ?? `Request failed (${res.status})`, data?.category, data?.correlationId ?? res.headers.get('X-Correlation-Id') ?? undefined);
  }
  return data as T;
}

function safeJson(text: string): any {
  try {
    return JSON.parse(text);
  } catch {
    return undefined;
  }
}

export const isStaff = (roles: string[] = []) => roles.includes('ADMIN') || roles.includes('SUPPORT_AGENT');
