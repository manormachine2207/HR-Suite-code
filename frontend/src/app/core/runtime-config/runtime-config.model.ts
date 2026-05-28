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
  i18n: {
    defaultLocale: 'de' | 'fr' | 'it' | 'en';
    supportedLocales: ReadonlyArray<'de' | 'fr' | 'it' | 'en'>;
  };
  release: {
    version: string;
  };
}
