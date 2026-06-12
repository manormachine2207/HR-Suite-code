/**
 * Pure counting logic for the dashboard KPI row („Meine Übersicht", ADR-017 Stufe 1).
 *
 * No Angular imports — the semantics ("which Antrag statuses count as open / as
 * decided") are unit-testable without HttpClient/TranslateService; the component
 * layers Signals on top. Statuses outside both sets (e.g. CANCELLED or a future
 * backend status) are deliberately counted in NEITHER KPI, so a new status never
 * inflates a number silently.
 */

/** Statuses that make an own Antrag count as "open" (KPI 1). */
export const OPEN_ANTRAG_STATUSES: ReadonlySet<string> = new Set([
  'DRAFT', 'SUBMITTED', 'IN_REVIEW',
]);

/** Statuses that make an own Antrag count as "decided" (KPI 3). */
export const DECIDED_ANTRAG_STATUSES: ReadonlySet<string> = new Set([
  'APPROVED', 'REJECTED', 'COMPLETED',
]);

/** Minimal shape the counters need — keeps the functions decoupled from the DTO. */
export interface HasStatus {
  readonly status: string;
}

/** Number of own Anträge in DRAFT/SUBMITTED/IN_REVIEW („Meine offenen Anträge"). */
export function countOpenAntraege(antraege: readonly HasStatus[]): number {
  return antraege.filter(a => OPEN_ANTRAG_STATUSES.has(a.status)).length;
}

/** Number of own Anträge in APPROVED/REJECTED/COMPLETED („Zuletzt entschieden"). */
export function countDecidedAntraege(antraege: readonly HasStatus[]): number {
  return antraege.filter(a => DECIDED_ANTRAG_STATUSES.has(a.status)).length;
}
