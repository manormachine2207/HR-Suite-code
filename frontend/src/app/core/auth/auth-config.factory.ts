import { AuthConfig } from 'angular-oauth2-oidc';
import { RuntimeConfigService } from '../runtime-config/runtime-config.service';

export const authConfigFactory = (cfg: RuntimeConfigService): AuthConfig => {
  const oidc = cfg.get().oidc;
  return {
    issuer: oidc.issuer,
    clientId: oidc.clientId,
    redirectUri: oidc.redirectUri,
    responseType: 'code',
    scope: oidc.scope,
    showDebugInformation: false,
    requireHttps: false   // dev only; prod overrides per runtime.json + reverse-proxy TLS enforcement
  };
};
