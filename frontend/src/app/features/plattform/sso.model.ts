/**
 * Read/write models for the global OIDC config API (`/api/v1/platform/oidc`,
 * platform-admin — ADR-019 Stufe 2). SDR-004: the client secret is NEVER part of
 * these models — only `clientSecretRef` (the env var NAME) + a `secretConfigured`
 * flag. Stufe 2 is configure-only: `enabled` is stored but not yet enforced.
 */
export interface OidcConfig {
  readonly issuer: string | null;
  readonly clientId: string | null;
  readonly clientSecretRef: string | null;
  readonly scopes: string | null;
  readonly redirectUri: string | null;
  readonly tenantClaim: string | null;
  readonly secretConfigured: boolean;
  readonly enabled: boolean;
  readonly updatedAt: string | null;
}

export interface UpdateOidcConfig {
  readonly issuer?: string | null;
  readonly clientId?: string | null;
  readonly clientSecretRef?: string | null;
  readonly scopes?: string | null;
  readonly redirectUri?: string | null;
  readonly tenantClaim?: string | null;
  readonly enabled: boolean;
}
