import { ApplicationConfig, APP_INITIALIZER, inject, provideBrowserGlobalErrorListeners } from '@angular/core';
import { provideRouter, withInMemoryScrolling } from '@angular/router';
import { provideHttpClient, withInterceptorsFromDi, HTTP_INTERCEPTORS } from '@angular/common/http';
import { importProvidersFrom } from '@angular/core';
import { provideObliqueConfiguration, ObHttpApiInterceptor } from '@oblique/oblique';
import { provideTranslateService } from '@ngx-translate/core';
import { OAuthModule, AuthConfig, OAuthService } from 'angular-oauth2-oidc';

import { routes } from './app.routes';
import { RuntimeConfigService } from './core/runtime-config/runtime-config.service';
import { translateHttpLoaderFactory } from './core/i18n/translate-http-loader.factory';
import { authConfigFactory } from './core/auth/auth-config.factory';

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    provideRouter(routes, withInMemoryScrolling({ anchorScrolling: 'enabled' })),
    provideHttpClient(withInterceptorsFromDi()),
    { provide: HTTP_INTERCEPTORS, useClass: ObHttpApiInterceptor, multi: true },
    {
      provide: APP_INITIALIZER,
      multi: true,
      useFactory: () => {
        const svc = inject(RuntimeConfigService);
        return () => svc.load();
      },
    },
    importProvidersFrom(OAuthModule.forRoot()),
    {
      provide: AuthConfig,
      useFactory: authConfigFactory,
      deps: [RuntimeConfigService],
    },
    {
      provide: APP_INITIALIZER,
      multi: true,
      useFactory: () => {
        const oauthService = inject(OAuthService);
        const cfg = inject(AuthConfig);
        return () => {
          oauthService.configure(cfg);
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
