import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

import { RuntimeConfigService } from '../../core/runtime-config/runtime-config.service';

/**
 * Read model for an open review task, mirroring the backend `TaskResponse` DTO
 * (`/api/v1/task`, ADR-013). The DTO is serialized with NON_NULL, so nullable
 * Antrag-context fields may be absent entirely. `payload` is only present on the
 * detail endpoint; `allowedOutcomes` is empty when the step declares no outcomes
 * (then complete is called without an outcome). Statuses stay plain strings so a
 * new backend status never breaks the frontend build.
 */
export interface ReviewTask {
  taskId: string;
  name: string;
  stepKey: string;
  /** ISO instant. */
  createdAt: string;
  antragId?: string | null;
  antragStatus?: string | null;
  /** jsonb LocaleMap of the Antragstyp title (resolved via locale-text). */
  antragstypTitle?: Partial<Record<string, string>> | null;
  antragstellerSubject?: string | null;
  allowedOutcomes: string[];
  /** Only set by GET /task/{id}. */
  payload?: Record<string, unknown> | null;
}

/**
 * Client for the reviewer task API (`/api/v1/task`, ADR-013). Roles
 * hr-reviewer/tenant-admin — other callers get 403, which the page surfaces as a
 * translated "no permission" state. `complete` posts only non-blank fields, matching
 * the optional `{outcome?, comment?}` contract of CompleteTaskRequest.
 */
@Injectable({ providedIn: 'root' })
export class ReviewService {
  private readonly http = inject(HttpClient);
  private readonly config = inject(RuntimeConfigService);

  private get base(): string {
    return this.config.get().apiBaseUrl;
  }

  list(): Observable<ReviewTask[]> {
    return this.http.get<ReviewTask[]>(`${this.base}/task`);
  }

  get(taskId: string): Observable<ReviewTask> {
    return this.http.get<ReviewTask>(`${this.base}/task/${taskId}`);
  }

  complete(taskId: string, outcome: string | null, comment: string | null): Observable<ReviewTask> {
    const body: Record<string, string> = {};
    if (outcome) {
      body['outcome'] = outcome;
    }
    const trimmed = comment?.trim();
    if (trimmed) {
      body['comment'] = trimmed;
    }
    return this.http.post<ReviewTask>(`${this.base}/task/${taskId}/complete`, body);
  }
}
