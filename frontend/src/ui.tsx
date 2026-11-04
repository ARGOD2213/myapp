import { type ReactNode, useCallback, useEffect, useState } from 'react';
import { ApiError } from './api';
import { titleCase } from './format';

// ---------------------------------------------------------------- data hook
export function useFetch<T>(load: () => Promise<T>, deps: unknown[] = []) {
  const [data, setData] = useState<T | undefined>();
  const [error, setError] = useState<ApiError | undefined>();
  const [loading, setLoading] = useState(true);
  const [tick, setTick] = useState(0);
  useEffect(() => {
    let live = true;
    setLoading(true);
    load()
      .then((d) => live && (setData(d), setError(undefined)))
      .catch((e) => live && setError(e instanceof ApiError ? e : new ApiError(0, String(e))))
      .finally(() => live && setLoading(false));
    return () => {
      live = false;
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [...deps, tick]);
  const reload = useCallback(() => setTick((t) => t + 1), []);
  return { data, error, loading, reload };
}

/** Runs a mutation, tracking busy/error/success state for the form that triggered it. */
export function useAction() {
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<ApiError | undefined>();
  const [done, setDone] = useState<string | undefined>();
  const run = useCallback(async <T,>(fn: () => Promise<T>, success?: string): Promise<T | undefined> => {
    setBusy(true);
    setError(undefined);
    setDone(undefined);
    try {
      const r = await fn();
      if (success) setDone(success);
      return r;
    } catch (e) {
      setError(e instanceof ApiError ? e : new ApiError(0, String(e)));
      return undefined;
    } finally {
      setBusy(false);
    }
  }, []);
  return { busy, error, done, run, clear: () => (setError(undefined), setDone(undefined)) };
}

// ---------------------------------------------------------------- feedback
export function Spinner({ label = 'Loading…' }: { label?: string }) {
  return (
    <div className="center muted" role="status">
      <span className="spinner" /> {label}
    </div>
  );
}

export function ErrorBox({ error, onRetry }: { error?: ApiError; onRetry?: () => void }) {
  if (!error) return null;
  return (
    <div className="alert error" role="alert">
      <strong>{error.message}</strong>
      {error.correlationId && <div className="small">Reference: {error.correlationId}</div>}
      {onRetry && (
        <button className="btn small" onClick={onRetry}>
          Retry
        </button>
      )}
    </div>
  );
}

export function Notice({ children, kind = 'success' }: { children?: ReactNode; kind?: 'success' | 'info' | 'warn' }) {
  if (!children) return null;
  return <div className={`alert ${kind}`}>{children}</div>;
}

export function Empty({ children = 'Nothing here yet.' }: { children?: ReactNode }) {
  return <div className="empty">{children}</div>;
}

/** Standard loading / error / content wrapper for fetched data. */
export function Async<T>({ state, children }: { state: { data?: T; error?: ApiError; loading: boolean; reload: () => void }; children: (d: T) => ReactNode }) {
  if (state.loading && state.data === undefined) return <Spinner />;
  if (state.error) return <ErrorBox error={state.error} onRetry={state.reload} />;
  if (state.data === undefined) return null;
  return <>{children(state.data)}</>;
}

// ---------------------------------------------------------------- layout
export function PageHeader({ title, subtitle, actions }: { title: string; subtitle?: string; actions?: ReactNode }) {
  return (
    <div className="page-header">
      <div>
        <h1>{title}</h1>
        {subtitle && <p className="muted">{subtitle}</p>}
      </div>
      <div className="row">{actions}</div>
    </div>
  );
}

export function Card({ title, children, actions }: { title?: string; children: ReactNode; actions?: ReactNode }) {
  return (
    <section className="card">
      {(title || actions) && (
        <div className="card-head">
          {title && <h2>{title}</h2>}
          <div className="row">{actions}</div>
        </div>
      )}
      {children}
    </section>
  );
}

export function Stat({ label, value, tone }: { label: string; value: ReactNode; tone?: 'bad' | 'warn' | 'good' }) {
  return (
    <div className={`stat ${tone ?? ''}`}>
      <div className="stat-value">{value}</div>
      <div className="muted small">{label}</div>
    </div>
  );
}

const TONES: Record<string, string> = {
  SUCCEEDED: 'good', ACTIVE: 'good', CONFIRMED: 'good', SENT: 'good', RESOLVED: 'good', CLOSED: 'neutral', RECONCILED: 'good',
  FAILED: 'bad', CANCELLED: 'bad', REJECTED: 'bad', DISCONNECTED: 'bad', RENEWAL_FAILED: 'bad', RECONCILIATION_REQUIRED: 'bad', RETRY_REQUIRED: 'warn',
  PENDING: 'warn', INITIATED: 'warn', PROCESSING: 'warn', IN_PROGRESS: 'warn', WAITING: 'warn', REFUND_PENDING: 'warn', SUSPENDED: 'warn', OPEN: 'info',
  EXPIRED: 'neutral', REFUNDED: 'info', REVERSED: 'info', NONE: 'neutral', URGENT: 'bad', HIGH: 'warn',
};

export function Badge({ value }: { value?: string | null }) {
  if (!value) return <span className="muted">—</span>;
  return <span className={`badge ${TONES[value] ?? 'neutral'}`}>{titleCase(value)}</span>;
}

// ---------------------------------------------------------------- forms
export function Field({ label, error, hint, children }: { label: string; error?: string; hint?: string; children: ReactNode }) {
  return (
    <label className="field">
      <span>{label}</span>
      {children}
      {hint && !error && <small className="muted">{hint}</small>}
      {error && <small className="field-error">{error}</small>}
    </label>
  );
}

export function Button({ busy, children, kind, ...rest }: React.ButtonHTMLAttributes<HTMLButtonElement> & { busy?: boolean; kind?: 'primary' | 'danger' | 'ghost' }) {
  return (
    <button className={`btn ${kind ?? ''}`} disabled={busy || rest.disabled} {...rest}>
      {busy ? 'Working…' : children}
    </button>
  );
}

export function Modal({ title, onClose, children }: { title: string; onClose: () => void; children: ReactNode }) {
  useEffect(() => {
    const onKey = (e: KeyboardEvent) => e.key === 'Escape' && onClose();
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [onClose]);
  return (
    <div className="modal-backdrop" onClick={onClose}>
      <div className="modal" role="dialog" aria-modal="true" aria-label={title} onClick={(e) => e.stopPropagation()}>
        <div className="card-head">
          <h2>{title}</h2>
          <button className="btn small" onClick={onClose} aria-label="Close">
            ✕
          </button>
        </div>
        {children}
      </div>
    </div>
  );
}

// ---------------------------------------------------------------- table + pagination
export interface Column<T> {
  header: string;
  render: (row: T) => ReactNode;
  className?: string;
}

export function Table<T>({ columns, rows, empty, rowKey }: { columns: Column<T>[]; rows: T[]; empty?: ReactNode; rowKey: (row: T) => string | number }) {
  if (rows.length === 0) return <Empty>{empty}</Empty>;
  return (
    <div className="table-wrap">
      <table>
        <thead>
          <tr>
            {columns.map((c) => (
              <th key={c.header} className={c.className}>
                {c.header}
              </th>
            ))}
          </tr>
        </thead>
        <tbody>
          {rows.map((r) => (
            <tr key={rowKey(r)}>
              {columns.map((c) => (
                <td key={c.header} className={c.className}>
                  {c.render(r)}
                </td>
              ))}
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

export function Pager({ page, totalPages, total, onChange }: { page: number; totalPages: number; total?: number; onChange: (p: number) => void }) {
  if (totalPages <= 1 && !total) return null;
  return (
    <div className="pager">
      <button className="btn small" disabled={page <= 0} onClick={() => onChange(page - 1)}>
        ← Prev
      </button>
      <span className="muted small">
        Page {page + 1} of {Math.max(totalPages, 1)}
        {total !== undefined && ` · ${total.toLocaleString()} total`}
      </span>
      <button className="btn small" disabled={page + 1 >= totalPages} onClick={() => onChange(page + 1)}>
        Next →
      </button>
    </div>
  );
}

/** Client-side pagination for endpoints that return a plain (bounded) array. */
export function usePaged<T>(rows: T[] | undefined, size = 10) {
  const [page, setPage] = useState(0);
  const all = rows ?? [];
  const totalPages = Math.max(1, Math.ceil(all.length / size));
  const safe = Math.min(page, totalPages - 1);
  return { items: all.slice(safe * size, safe * size + size), page: safe, totalPages, total: all.length, setPage };
}

export function useDebounced<T>(value: T, ms = 350): T {
  const [v, setV] = useState(value);
  useEffect(() => {
    const t = setTimeout(() => setV(value), ms);
    return () => clearTimeout(t);
  }, [value, ms]);
  return v;
}
