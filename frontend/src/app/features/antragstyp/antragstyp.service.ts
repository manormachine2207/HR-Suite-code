import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

import { RuntimeConfigService } from '../../core/runtime-config/runtime-config.service';
import { AntragsTypSummary } from './antragstyp.model';
import { AntragsTypVersion } from './antragstyp-version.model';
import { FormDefinition } from '../form-designer/form-definition.model';
import { FlowDefinition } from '../form-designer/flow-definition.model';
import { LocaleMap } from '../form-designer/form-definition.model';

/** Client for the Antragstyp definition + version API (`/api/v1/antragstyp`). */
@Injectable({ providedIn: 'root' })
export class AntragsTypService {
  private readonly http = inject(HttpClient);
  private readonly config = inject(RuntimeConfigService);

  private get base(): string {
    return this.config.get().apiBaseUrl;
  }

  list(): Observable<AntragsTypSummary[]> {
    return this.http.get<AntragsTypSummary[]>(`${this.base}/antragstyp`);
  }

  getById(id: string): Observable<AntragsTypSummary> {
    return this.http.get<AntragsTypSummary>(`${this.base}/antragstyp/${id}`);
  }

  listVersions(id: string): Observable<AntragsTypVersion[]> {
    return this.http.get<AntragsTypVersion[]>(`${this.base}/antragstyp/${id}/versions`);
  }

  createAntragstyp(key: string, title: LocaleMap): Observable<AntragsTypSummary> {
    return this.http.post<AntragsTypSummary>(`${this.base}/antragstyp`, { key, title });
  }

  /**
   * Creates a new DRAFT major carrying the form, (optional) flow, and (optional) graph
   * definition. `workflowBpmn` is intentionally NOT sent: the backend removed it from
   * CreateVersionRequest (2026-06-12) — BPMN is compiler output only (ADR-010).
   */
  createDraftVersion(id: string, formDefinition: FormDefinition,
                     flowDefinition: FlowDefinition | null,
                     graphDefinition: unknown | null = null): Observable<AntragsTypVersion> {
    const body: Record<string, unknown> = { formDefinition, sfActionBindings: {} };
    if (flowDefinition) {
      body['flowDefinition'] = flowDefinition;
    }
    if (graphDefinition) {
      body['graphDefinition'] = graphDefinition;
    }
    return this.http.post<AntragsTypVersion>(`${this.base}/antragstyp/${id}/versions`, body);
  }

  editDraft(versionId: string, formDefinition: FormDefinition,
            flowDefinition: FlowDefinition | null,
            graphDefinition: unknown | null = null): Observable<AntragsTypVersion> {
    const body: Record<string, unknown> = { formDefinition, sfActionBindings: {} };
    if (flowDefinition) {
      body['flowDefinition'] = flowDefinition;
    }
    if (graphDefinition) {
      body['graphDefinition'] = graphDefinition;
    }
    return this.http.put<AntragsTypVersion>(`${this.base}/antragstyp/versions/${versionId}/draft`, body);
  }

  publish(versionId: string): Observable<AntragsTypVersion> {
    return this.http.post<AntragsTypVersion>(`${this.base}/antragstyp/versions/${versionId}/publish`, {});
  }

  listActionRefs(): Observable<string[]> {
    return this.http.get<string[]>(`${this.base}/action/refs`);
  }
}
