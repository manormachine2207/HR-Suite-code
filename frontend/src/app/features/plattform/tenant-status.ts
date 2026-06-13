import { TenantStatus } from './tenant.model';

/**
 * Pure presentation + transition helpers for tenant lifecycle status (ADR-019
 * Stufe 1). The allowed transitions mirror the backend matrix exactly
 * (Tenant.changeStatus): ONBOARDING→ACTIVE; ACTIVE↔SUSPENDED; any non-archived
 * →ARCHIVED; ARCHIVED is terminal. Kept pure so the rules are unit-tested without
 * the component.
 */

const KNOWN: readonly string[] = ['ACTIVE', 'SUSPENDED', 'ONBOARDING', 'ARCHIVED'];

/** `plattform.status.*` i18n key for a known status, or null for an unknown one. */
export function tenantStatusKey(status: string): string | null {
  return KNOWN.includes(status) ? `plattform.status.${status.toLowerCase()}` : null;
}

/** CSS modifier class for the status badge (colour bucket). */
export function tenantStatusClass(status: string): string {
  switch (status) {
    case 'ACTIVE': return 'is-active';
    case 'SUSPENDED': return 'is-suspended';
    case 'ONBOARDING': return 'is-onboarding';
    case 'ARCHIVED': return 'is-archived';
    default: return 'is-unknown';
  }
}

/** A status-change action offered on a tenant row: target status + button i18n key. */
export interface TenantStatusAction {
  readonly target: TenantStatus;
  readonly labelKey: string;
}

/** Status actions available for a tenant in the given status (empty for ARCHIVED). */
export function allowedStatusActions(status: string): readonly TenantStatusAction[] {
  switch (status) {
    case 'ACTIVE':
      return [
        { target: 'SUSPENDED', labelKey: 'plattform.action.suspend' },
        { target: 'ARCHIVED', labelKey: 'plattform.action.archive' },
      ];
    case 'SUSPENDED':
      return [
        { target: 'ACTIVE', labelKey: 'plattform.action.activate' },
        { target: 'ARCHIVED', labelKey: 'plattform.action.archive' },
      ];
    case 'ONBOARDING':
      return [
        { target: 'ACTIVE', labelKey: 'plattform.action.activate' },
        { target: 'ARCHIVED', labelKey: 'plattform.action.archive' },
      ];
    default:
      return [];
  }
}
