import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { provideRouter, ActivatedRoute } from '@angular/router';
import { TranslateModule } from '@ngx-translate/core';
import { describe, it, expect, beforeEach, vi } from 'vitest';
import { ComponentFixture } from '@angular/core/testing';

import { FormDesignerComponent } from './form-designer.component';
import { RuntimeConfigService } from '../../core/runtime-config/runtime-config.service';

const stubConfig = { get: () => ({ apiBaseUrl: '/api/v1' }) } as Partial<RuntimeConfigService>;

const stubRoute = {
  snapshot: { paramMap: { get: (_: string) => 'at-1' } },
} as unknown as ActivatedRoute;

const GRAPH_DEF = {
  nodes: [
    { id: 'n1', type: 'START', position: { x: 0, y: 0 }, data: {} },
    { id: 'n2', type: 'ACTION', position: { x: 100, y: 0 }, data: { key: 'act', ref: 'r', inputMapping: {} } },
  ],
  edges: [{ id: 'e1', source: 'n1', target: 'n2' }],
};

const DRAFT_VERSION = {
  id: 'v-1', antragstypId: 'at-1', major: 1, minor: 0, status: 'DRAFT',
  formDefinition: { fields: [{ key: 'name', type: 'TEXT', required: true }] },
  graphDefinition: GRAPH_DEF,
  createdAt: '2026-01-01T00:00:00Z', updatedAt: '2026-01-01T00:00:00Z',
};

describe('FormDesignerComponent — canvas seeding', () => {
  let fixture: ComponentFixture<FormDesignerComponent>;
  let cmp: FormDesignerComponent;
  let http: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [FormDesignerComponent, TranslateModule.forRoot()],
      providers: [
        provideHttpClient(), provideHttpClientTesting(), provideRouter([]),
        { provide: RuntimeConfigService, useValue: stubConfig },
        { provide: ActivatedRoute, useValue: stubRoute },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(FormDesignerComponent);
    cmp = fixture.componentInstance;
    http = TestBed.inject(HttpTestingController);
  });

  it('seeds the canvas editor with an existing draft graphDefinition', () => {
    fixture.detectChanges(); // triggers ngOnInit — forkJoin fires

    // Flush all three HTTP requests
    http.expectOne('/api/v1/antragstyp/at-1').flush({ id: 'at-1', key: 'urlaubsantrag' });
    http.expectOne('/api/v1/action/refs').flush(['provision-ad-account']);
    http.expectOne('/api/v1/antragstyp/at-1/versions').flush([DRAFT_VERSION]);

    // Run another CD cycle so the subscribe callback runs and markForCheck() takes effect
    fixture.detectChanges();

    // ViewChild must now be defined (canvas is always mounted, not behind @if)
    expect(cmp.flowCanvas).toBeDefined();

    // The canvas must contain the nodes from the draft graphDefinition
    expect(cmp.flowCanvas!.graph().nodes.length).toBe(2);
    expect(cmp.flowCanvas!.graph().nodes[0].type).toBe('START');
    expect(cmp.flowCanvas!.graph().nodes[1].type).toBe('ACTION');
  });

  it('save() reads graph from the canvas and sends it as graphDefinition', () => {
    fixture.detectChanges();

    http.expectOne('/api/v1/antragstyp/at-1').flush({ id: 'at-1', key: 'urlaubsantrag' });
    http.expectOne('/api/v1/action/refs').flush(['provision-ad-account']);
    http.expectOne('/api/v1/antragstyp/at-1/versions').flush([DRAFT_VERSION]);

    fixture.detectChanges();

    // section is 'form' by default; the canvas editor is always mounted
    expect(cmp.section).toBe('form');
    expect(cmp.flowCanvas).toBeDefined();

    const toGraphSpy = vi.spyOn(cmp.flowCanvas!, 'toGraphDefinition');

    cmp.save();

    // save() must call toGraphDefinition() on the canvas regardless of active tab
    expect(toGraphSpy).toHaveBeenCalled();

    // Clean up pending PUT request — body must contain graphDefinition, NOT flowDefinition
    const req = http.expectOne(r => r.url.includes('/draft'));
    expect('graphDefinition' in req.request.body).toBe(true);
    expect('flowDefinition' in req.request.body).toBe(false);
    req.flush({ ...DRAFT_VERSION, minor: 1 });
  });

  it('canPublish is false when canvas has a graph, true when empty', () => {
    fixture.detectChanges();

    http.expectOne('/api/v1/antragstyp/at-1').flush({ id: 'at-1', key: 'urlaubsantrag' });
    http.expectOne('/api/v1/action/refs').flush(['provision-ad-account']);
    http.expectOne('/api/v1/antragstyp/at-1/versions').flush([DRAFT_VERSION]);

    fixture.detectChanges();

    // draftVersionId is set from the loaded draft; canvas has 2 nodes → canPublish false
    expect(cmp.draftVersionId).toBe('v-1');
    expect(cmp.canPublish).toBe(false);

    // Clear the canvas → toGraphDefinition() returns null; draftVersionId still set → canPublish true
    cmp.flowCanvas!.loadGraph(null);
    expect(cmp.canPublish).toBe(true);
  });

  function loadDraft(): void {
    fixture.detectChanges();
    http.expectOne('/api/v1/antragstyp/at-1').flush({ id: 'at-1', key: 'urlaubsantrag' });
    http.expectOne('/api/v1/action/refs').flush(['provision-ad-account']);
    http.expectOne('/api/v1/antragstyp/at-1/versions').flush([DRAFT_VERSION]);
    fixture.detectChanges(); // seeds the canvas + captures the saved snapshot
  }

  it('publish() publishes directly when the editor is clean (no extra save)', () => {
    loadDraft();
    expect(cmp.isDirty).toBe(false);

    cmp.publish();

    http.expectNone(r => r.url.includes('/draft'));
    const publishReq = http.expectOne('/api/v1/antragstyp/versions/v-1/publish');
    publishReq.flush({ ...DRAFT_VERSION, status: 'PUBLISHED', processDefinitionKey: 'proc_1' });

    expect(cmp.publishDone).toBe(true);
    expect(cmp.publishedKey).toBe('proc_1');
    expect(cmp.publishing).toBe(false);
  });

  it('publish() auto-saves a dirty editor first, then publishes the fresh draft', () => {
    loadDraft();
    cmp.fields.at(0).get('key')!.setValue('renamed');
    expect(cmp.isDirty).toBe(true);

    cmp.publish();

    // 1) dirty state is persisted (PUT on the existing draft) …
    const saveReq = http.expectOne('/api/v1/antragstyp/versions/v-1/draft');
    expect(saveReq.request.method).toBe('PUT');
    saveReq.flush({ ...DRAFT_VERSION, minor: 1 });

    // 2) … then the publish goes out.
    const publishReq = http.expectOne('/api/v1/antragstyp/versions/v-1/publish');
    publishReq.flush({ ...DRAFT_VERSION, status: 'PUBLISHED', processDefinitionKey: 'proc_2' });

    expect(cmp.publishDone).toBe(true);
    expect(cmp.isDirty).toBe(false);
  });

  it('publish() surfaces the 422 ProblemDetail.detail (i18n gate) as visible error', () => {
    loadDraft();

    cmp.publish();
    http.expectOne('/api/v1/antragstyp/versions/v-1/publish').flush(
      { detail: 'i18n incomplete (BDR-005): label.fr missing' },
      { status: 422, statusText: 'Unprocessable Entity' }
    );

    expect(cmp.errorKey).toBe('designer.error.publishRejected');
    expect(cmp.errorDetail).toBe('i18n incomplete (BDR-005): label.fr missing');
    expect(cmp.publishDone).toBe(false);
    expect(cmp.publishing).toBe(false);
  });
});
