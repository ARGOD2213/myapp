// The API mixes zoned ("...Z") and zone-less timestamps; the server clock is UTC, so zone-less means UTC.
export function parseApiDate(value?: string | null): Date | null {
  if (!value) return null;
  const hasZone = /[zZ]|[+-]\d{2}:?\d{2}$/.test(value);
  const d = new Date(hasZone ? value : `${value}Z`);
  return Number.isNaN(d.getTime()) ? null : d;
}

export function formatDateTime(value?: string | null): string {
  const d = parseApiDate(value);
  return d ? d.toLocaleString(undefined, { dateStyle: 'medium', timeStyle: 'short' }) : '—';
}

export function formatDate(value?: string | null): string {
  const d = parseApiDate(value);
  return d ? d.toLocaleDateString(undefined, { dateStyle: 'medium' }) : '—';
}

export function formatMoney(value?: number | string | null, currency = 'INR'): string {
  if (value === undefined || value === null || value === '') return '—';
  return new Intl.NumberFormat('en-IN', { style: 'currency', currency }).format(Number(value));
}

export function titleCase(value?: string | null): string {
  return (value ?? '').toLowerCase().split('_').map((w) => w.charAt(0).toUpperCase() + w.slice(1)).join(' ');
}

/** datetime-local input value (browser local time) -> LocalDateTime string the API expects (UTC). */
export function toApiLocalDateTime(localValue: string): string {
  return new Date(localValue).toISOString().slice(0, 19);
}
