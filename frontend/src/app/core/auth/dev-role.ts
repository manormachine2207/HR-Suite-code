/**
 * Dev identity override (ADR-016, Prototyp-Gap 13).
 *
 * In the local dev stack the backend accepts mock bearer tokens of the form
 * `dev-<role>~<tenant-uuid>` (SDR-001). The dev role switcher stores an override
 * role under DEV_ROLE_STORAGE_KEY; DevAuthInterceptor then rebuilds the token at
 * runtime from that role + the tenant id of the configured `devAuth.token`.
 *
 * SECURITY: none of this is consulted unless runtime.json carries an enabled
 * `devAuth` block — production configs have no such block, so the interceptor
 * never reads localStorage and the switcher widget never renders there.
 */

/** localStorage key holding the dev identity override. */
export const DEV_ROLE_STORAGE_KEY = 'hrsuite.dev.role';

/** Dev identities offered by the switcher (mirrors the backend mock-JWT roles, ADR-016). */
export const DEV_ROLES: readonly string[] = [
  'applicant',
  'approver-vg',
  'approver-hr-bp',
  'approver-hal',
  'hr-reviewer',
  'hr-designer',
  'tenant-admin',
];

/** Reads the override role; tolerates storage being unavailable (returns null). */
export function readDevRoleOverride(): string | null {
  try {
    return globalThis.localStorage?.getItem(DEV_ROLE_STORAGE_KEY) ?? null;
  } catch {
    return null;
  }
}

/**
 * Builds the dev bearer token for `role`, re-using the tenant id of the configured
 * dev token (`dev-<role>~<tenant-uuid>` → part after `~`). Returns null when no
 * tenant id can be extracted (e.g. `dev-platform-admin`) — callers then keep the
 * configured token unchanged.
 */
export function devTokenForRole(configuredToken: string, role: string): string | null {
  const sep = configuredToken.indexOf('~');
  if (sep < 0) return null;
  const tenantId = configuredToken.slice(sep + 1);
  return tenantId ? `dev-${role}~${tenantId}` : null;
}
