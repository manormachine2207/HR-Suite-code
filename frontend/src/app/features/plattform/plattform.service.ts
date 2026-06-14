import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

import { RuntimeConfigService } from '../../core/runtime-config/runtime-config.service';
import { CreateTenantRequest, Tenant, TenantStatus } from './tenant.model';
import { SmtpRelay, TestSendResult, UpdateSmtpRelay } from './smtp.model';
import { OidcConfig, UpdateOidcConfig } from './sso.model';

/**
 * Client for the platform admin API (`/api/v1/tenant`, `/api/v1/platform/*`,
 * platform-admin guarded — ADR-019). Calls answer 403 for non-platform-admin; the
 * views turn that into a "platform operators only" state.
 */
@Injectable({ providedIn: 'root' })
export class PlattformService {
  private readonly http = inject(HttpClient);
  private readonly config = inject(RuntimeConfigService);

  private get base(): string {
    return this.config.get().apiBaseUrl;
  }

  listTenants(): Observable<Tenant[]> {
    return this.http.get<Tenant[]>(`${this.base}/tenant`);
  }

  createTenant(req: CreateTenantRequest): Observable<Tenant> {
    return this.http.post<Tenant>(`${this.base}/tenant`, req);
  }

  changeStatus(id: string, status: TenantStatus): Observable<Tenant> {
    return this.http.patch<Tenant>(`${this.base}/tenant/${id}/status`, { status });
  }

  // ---- SMTP relay (ADR-019 Stufe 3) --------------------------------------
  getSmtp(): Observable<SmtpRelay> {
    return this.http.get<SmtpRelay>(`${this.base}/platform/smtp`);
  }

  updateSmtp(req: UpdateSmtpRelay): Observable<SmtpRelay> {
    return this.http.put<SmtpRelay>(`${this.base}/platform/smtp`, req);
  }

  testSmtp(to: string): Observable<TestSendResult> {
    return this.http.post<TestSendResult>(`${this.base}/platform/smtp/test`, { to });
  }

  // ---- OIDC / SSO (ADR-019 Stufe 2, configure-only) ----------------------
  getOidc(): Observable<OidcConfig> {
    return this.http.get<OidcConfig>(`${this.base}/platform/oidc`);
  }

  updateOidc(req: UpdateOidcConfig): Observable<OidcConfig> {
    return this.http.put<OidcConfig>(`${this.base}/platform/oidc`, req);
  }
}
