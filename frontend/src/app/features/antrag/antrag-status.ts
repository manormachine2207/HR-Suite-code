/**
 * Shared status rendering for Antrag views (list + detail) so both surfaces show
 * identical badges. Extracted from antrag-list when the detail page (Cut D) arrived.
 */

/** Antrag statuses with a `status.*` translation (unknown ones render as raw enum). */
const KNOWN_STATUSES = new Set([
  'DRAFT', 'SUBMITTED', 'IN_REVIEW', 'APPROVED', 'REJECTED', 'CANCELLED', 'COMPLETED',
]);

/** `status.*` i18n key for known Antrag statuses, null for unknown (renders raw). */
export function antragStatusKey(status: string): string | null {
  return KNOWN_STATUSES.has(status) ? `status.${status}` : null;
}

/** CSS badge class (`.hr-status.is-*`) for an Antrag status. */
export function antragStatusClass(status: string): string {
  switch (status) {
    case 'SUBMITTED': return 'is-submitted';
    case 'IN_REVIEW': return 'is-review';
    case 'APPROVED': return 'is-approved';
    case 'COMPLETED': return 'is-approved';
    case 'REJECTED': return 'is-rejected';
    case 'CANCELLED': return 'is-cancelled';
    case 'DRAFT': return 'is-draft';
    default: return 'is-default';
  }
}
