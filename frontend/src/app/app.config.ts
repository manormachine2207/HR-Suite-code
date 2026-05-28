import { ApplicationConfig, provideBrowserGlobalErrorListeners } from '@angular/core';
import { provideRouter, withInMemoryScrolling } from '@angular/router';
import { provideHttpClient, withInterceptorsFromDi, HTTP_INTERCEPTORS } from '@angular/common/http';
import { provideObliqueConfiguration, ObHttpApiInterceptor } from '@oblique/oblique';

import { routes } from './app.routes';

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    provideRouter(routes, withInMemoryScrolling({ anchorScrolling: 'enabled' })),
    provideHttpClient(withInterceptorsFromDi()),
    { provide: HTTP_INTERCEPTORS, useClass: ObHttpApiInterceptor, multi: true },
    provideObliqueConfiguration({
      accessibilityStatement: {
        applicationName: 'HR-Suite',
        conformity: 'none',
        createdOn: new Date('2026-05-28'),
        applicationOperator: 'HR-Suite Federal',
        contact: [{ email: 'hr-suite@example.org' }]
      },
      hasLanguageInUrl: false
    })
  ]
};
