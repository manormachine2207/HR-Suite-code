import { Injectable, inject } from '@angular/core';
import { HttpEvent, HttpHandler, HttpInterceptor, HttpRequest } from '@angular/common/http';
import { Observable } from 'rxjs';

import { RuntimeConfigService } from '../runtime-config/runtime-config.service';

/**
 * Dev-only auth shim (SDR-001). The local backend's mock JwtDecoder accepts a bearer
 * token `dev-<role>~<tenant-uuid>` (or `dev-platform-admin`). When `runtime.json`
 * carries an enabled `devAuth` block, this interceptor attaches that token to API
 * requests so the SPA can talk to RLS-protected endpoints without a real OIDC login.
 *
 * In production `devAuth` is absent → this interceptor is a no-op and the real OIDC
 * access token (angular-oauth2-oidc) must be wired in instead. Only API calls are
 * touched; static asset / i18n / runtime.json requests pass through untouched (they
 * also run before RuntimeConfig is loaded, so config is only read for `/api/` URLs).
 */
@Injectable()
export class DevAuthInterceptor implements HttpInterceptor {
  private readonly config = inject(RuntimeConfigService);

  intercept(req: HttpRequest<unknown>, next: HttpHandler): Observable<HttpEvent<unknown>> {
    if (!req.url.includes('/api/')) {
      return next.handle(req);
    }
    const devAuth = this.config.get().devAuth;
    if (devAuth?.enabled && devAuth.token && !req.headers.has('Authorization')) {
      req = req.clone({ setHeaders: { Authorization: `Bearer ${devAuth.token}` } });
    }
    return next.handle(req);
  }
}
