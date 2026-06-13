import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

import { RuntimeConfigService } from '../../core/runtime-config/runtime-config.service';
import { CreateTenantRequest, Tenant, TenantStatus } from './tenant.model';

/**
 * Client for the Tenant admin API (`/api/v1/tenant`, platform-admin guarded —
 * ADR-019 Stufe 1). All three calls answer 403 for non-platform-admin; the
 * Mandanten view turns that into a "platform operators only" state.
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
}
