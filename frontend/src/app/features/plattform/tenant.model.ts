/**
 * Read/write models for the Tenant admin API (`/api/v1/tenant`, platform-admin
 * guarded — ADR-019 Stufe 1). i18n fields are locale → text maps (jsonb on the
 * backend, BDR-005). `status` is kept as a string union but tolerated as a plain
 * string in views so a new backend status never breaks the build.
 */

/** Lifecycle states of a tenant (mirrors the backend enum). */
export type TenantStatus = 'ACTIVE' | 'SUSPENDED' | 'ONBOARDING' | 'ARCHIVED';

/** A tenant as returned by `GET /api/v1/tenant`. */
export interface Tenant {
  readonly id: string;
  readonly code: string;
  readonly displayName: Record<string, string>;
  readonly subdomain: string;
  readonly status: string;
  readonly defaultLocale: string;
  readonly createdAt: string;
  readonly updatedAt: string;
}

/** Body of `POST /api/v1/tenant`. `defaultLocale` is optional (backend default). */
export interface CreateTenantRequest {
  readonly code: string;
  readonly subdomain: string;
  readonly displayName: Record<string, string>;
  readonly defaultLocale?: string;
}
