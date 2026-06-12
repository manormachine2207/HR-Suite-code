import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

import { RuntimeConfigService } from '../../core/runtime-config/runtime-config.service';
import { Antrag, AntragProgress } from './antrag.model';

/**
 * Client for the Antrag API (`/api/v1/antrag`). Covers the applicant path:
 * list-own, create draft, submit (pins the published major + starts the Flowable
 * process, ADR-009 §4/§5) and cancel.
 */
@Injectable({ providedIn: 'root' })
export class AntragService {
  private readonly http = inject(HttpClient);
  private readonly config = inject(RuntimeConfigService);

  private get base(): string {
    return this.config.get().apiBaseUrl;
  }

  listOwn(): Observable<Antrag[]> {
    return this.http.get<Antrag[]>(`${this.base}/antrag`);
  }

  get(id: string): Observable<Antrag> {
    return this.http.get<Antrag>(`${this.base}/antrag/${id}`);
  }

  /** Workflow progress (user tasks in execution order) for the detail stepper (Cut D). */
  progress(id: string): Observable<AntragProgress> {
    return this.http.get<AntragProgress>(`${this.base}/antrag/${id}/progress`);
  }

  create(antragstypId: string, payload: Record<string, unknown>): Observable<Antrag> {
    return this.http.post<Antrag>(`${this.base}/antrag`, { antragstypId, payload });
  }

  submit(id: string): Observable<Antrag> {
    return this.http.post<Antrag>(`${this.base}/antrag/${id}/submit`, {});
  }

  cancel(id: string): Observable<Antrag> {
    return this.http.post<Antrag>(`${this.base}/antrag/${id}/cancel`, {});
  }
}
