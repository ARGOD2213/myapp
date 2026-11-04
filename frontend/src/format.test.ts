import { describe, expect, it } from 'vitest';
import { formatMoney, parseApiDate, titleCase, toApiLocalDateTime } from './format';

describe('format helpers', () => {
  it('treats zone-less API timestamps as UTC', () => {
    expect(parseApiDate('2026-11-03T10:00:00')?.toISOString()).toBe('2026-11-03T10:00:00.000Z');
    expect(parseApiDate('2026-11-03T10:00:00.123Z')?.toISOString()).toBe('2026-11-03T10:00:00.123Z');
  });

  it('returns null for missing or invalid dates', () => {
    expect(parseApiDate(undefined)).toBeNull();
    expect(parseApiDate('not a date')).toBeNull();
  });

  it('formats money in INR and status labels', () => {
    expect(formatMoney(299)).toContain('299');
    expect(formatMoney(null)).toBe('—');
    expect(titleCase('REFUND_PENDING')).toBe('Refund Pending');
  });

  it('serialises a local datetime for the API', () => {
    expect(toApiLocalDateTime('2026-11-03T10:00')).toMatch(/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}$/);
  });
});
