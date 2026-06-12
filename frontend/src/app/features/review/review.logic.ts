/**
 * Pure view logic for the reviewer task page (ADR-013, Review-Pfad). Kept free of
 * Angular so the mapping rules are unit-testable without TestBed (Tenet 14).
 */

/** Declared outcomes with a dedicated `review.outcome.*` translation (BDR-005). */
const OUTCOME_LABEL_KEYS: Record<string, string> = {
  approve: 'review.outcome.approve',
  reject: 'review.outcome.reject',
};

/** i18n key for a declared outcome, or null when the raw outcome value must render. */
export function outcomeLabelKey(outcome: string): string | null {
  return OUTCOME_LABEL_KEYS[outcome] ?? null;
}

/** Antrag statuses with a `status.*` translation (unknown ones render as raw enum). */
const KNOWN_STATUSES = new Set([
  'DRAFT', 'SUBMITTED', 'IN_REVIEW', 'APPROVED', 'REJECTED', 'CANCELLED', 'COMPLETED',
]);

export function statusKeyOf(status: string): string | null {
  return KNOWN_STATUSES.has(status) ? `status.${status}` : null;
}

/**
 * Server-provided RFC-9457 ProblemDetail.detail of an HTTP error (e.g. the 422
 * "Outcome nicht erlaubt" from POST /task/{id}/complete), or null when absent/blank.
 */
export function problemDetailOf(e: unknown): string | null {
  const detail = (e as { error?: { detail?: unknown } } | null)?.error?.detail;
  return typeof detail === 'string' && detail.trim().length > 0 ? detail : null;
}

export interface PayloadRow {
  key: string;
  value: string;
}

/**
 * Flattens a task payload (jsonb Record) into display rows for the key/value table.
 * Precomputed once when the detail loads — never allocate this in the template
 * (zoneless NG0100 discipline).
 */
export function payloadRows(payload: Record<string, unknown> | null | undefined): PayloadRow[] {
  if (!payload) {
    return [];
  }
  return Object.entries(payload).map(([key, value]) => ({ key, value: formatValue(value) }));
}

function formatValue(v: unknown): string {
  if (v === null || v === undefined || v === '') {
    return '—';
  }
  if (Array.isArray(v)) {
    return v.map(x => formatValue(x)).join(', ');
  }
  if (typeof v === 'object') {
    return JSON.stringify(v);
  }
  return String(v);
}
