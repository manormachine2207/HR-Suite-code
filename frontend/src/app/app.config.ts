import { ApplicationConfig, APP_INITIALIZER, inject, provideBrowserGlobalErrorListeners } from '@angular/core';
import { provideRouter, withInMemoryScrolling } from '@angular/router';
import { provideHttpClient, withInterceptorsFromDi, HTTP_INTERCEPTORS } from '@angular/common/http';
import { importProvidersFrom } from '@angular/core';
import { provideObliqueConfiguration } from '@oblique/oblique';
import { provideTranslateService } from '@ngx-translate/core';
import { OAuthModule, OAuthService } from 'angular-oauth2-oidc';

import { routes } from './app.routes';
import { RuntimeConfigService } from './core/runtime-config/runtime-config.service';
import { translateHttpLoaderFactory } from './core/i18n/translate-http-loader.factory';
import { DevAuthInterceptor } from './core/auth/dev-auth.interceptor';

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    provideRouter(routes, withInMemoryScrolling({ anchorScrolling: 'enabled' })),
    provideHttpClient(withInterceptorsFromDi()),
    // NOTE: Oblique's global ObHttpApiInterceptor spinner was removed — it stuck
    // "on" on multi-request pages (e.g. the form designer), animating forever and
    // never letting the page settle. Components carry their own loading/error
    // states. Re-introduce a non-blocking spinner in a later cut if desired.
    { provide: HTTP_INTERCEPTORS, useClass: DevAuthInterceptor, multi: true },
    importProvidersFrom(OAuthModule.forRoot()),
    {
      provide: APP_INITIALIZER,
      multi: true,
      useFactory: () => {
        const svc = inject(RuntimeConfigService);
        const oauthService = inject(OAuthService);
        return async () => {
          await svc.load();
          const oidc = svc.get().oidc;
          oauthService.configure({
            issuer: oidc.issuer,
            clientId: oidc.clientId,
            redirectUri: oidc.redirectUri,
            responseType: 'code',
            scope: oidc.scope,
            showDebugInformation: false,
            requireHttps: false,
          });
        };
      },
    },
    provideObliqueConfiguration({
      accessibilityStatement: {
        applicationName: 'HR-Suite',
        conformity: 'none',
        createdOn: new Date('2026-05-28'),
        applicationOperator: 'HR-Suite Federal',
        contact: [{ email: 'hr-suite@example.org' }]
      },
      hasLanguageInUrl: false
    }),
    provideTranslateService({
      defaultLanguage: 'de',
    }),
    ...translateHttpLoaderFactory()
  ]
};
