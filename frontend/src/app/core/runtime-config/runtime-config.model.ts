export interface RuntimeConfig {
  apiBaseUrl: string;
  oidc: {
    issuer: string;
    clientId: string;
    redirectUri: string;
    scope: string;
  };
  tenant: {
    code: string;
  };
  /**
   * Dev-only authentication shim. In production this block is absent and the real
   * OIDC flow (angular-oauth2-oidc) provides the bearer token. In the local dev
   * stack the backend accepts a mock token `dev-<role>~<tenant-uuid>` (SDR-001),
   * which DevAuthInterceptor attaches to API calls when `enabled` is true.
   */
  devAuth?: {
    enabled: boolean;
    token: string;
  };
  i18n: {
    defaultLocale: 'de' | 'fr' | 'it' | 'en';
    supportedLocales: ReadonlyArray<'de' | 'fr' | 'it' | 'en'>;
  };
  release: {
    version: string;
  };
}
