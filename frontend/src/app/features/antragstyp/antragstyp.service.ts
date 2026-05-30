import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

import { RuntimeConfigService } from '../../core/runtime-config/runtime-config.service';
import { AntragsTypSummary } from './antragstyp.model';
import { AntragsTypVersion } from './antragstyp-version.model';
import { FormDefinition } from '../form-designer/form-definition.model';

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

  /** Creates a new DRAFT major version carrying the designed form definition. */
  createDraftVersion(id: string, formDefinition: FormDefinition): Observable<AntragsTypVersion> {
    return this.http.post<AntragsTypVersion>(`${this.base}/antragstyp/${id}/versions`, {
      formDefinition,
      workflowBpmn: '<bpmn/>',
      sfActionBindings: {}
    });
  }
}
