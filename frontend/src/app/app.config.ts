import { ApplicationConfig, APP_INITIALIZER, inject, provideBrowserGlobalErrorListeners } from '@angular/core';
import { provideRouter, withInMemoryScrolling } from '@angular/router';
import { provideHttpClient, withInterceptorsFromDi, HTTP_INTERCEPTORS } from '@angular/common/http';
import { provideObliqueConfiguration, ObHttpApiInterceptor } from '@oblique/oblique';
import { provideTranslateService } from '@ngx-translate/core';

import { routes } from './app.routes';
import { RuntimeConfigService } from './core/runtime-config/runtime-config.service';
import { translateHttpLoaderFactory } from './core/i18n/translate-http-loader.factory';

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
