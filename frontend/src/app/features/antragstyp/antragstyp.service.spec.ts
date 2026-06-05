import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { describe, it, expect, beforeEach, afterEach } from 'vitest';

import { AntragsTypService } from './antragstyp.service';
import { RuntimeConfigService } from '../../core/runtime-config/runtime-config.service';
import { FormDefinition } from '../form-designer/form-definition.model';
import { FlowDefinition } from '../form-designer/flow-definition.model';

const stubConfig = { get: () => ({ apiBaseUrl: '/api/v1' }) } as Partial<RuntimeConfigService>;

describe('AntragsTypService', () => {
  let service: AntragsTypService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        AntragsTypService,
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: RuntimeConfigService, useValue: stubConfig },
      ],
    });
    service = TestBed.inject(AntragsTypService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('createAntragstyp POSTs key + title', () => {
    const title = { de: 'Urlaub' };
    service.createAntragstyp('urlaubsantrag', title).subscribe();
    const req = http.expectOne('/api/v1/antragstyp');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ key: 'urlaubsantrag', title });
    req.flush({ id: 'at1' });
  });

  it('createDraftVersion sends form + flow when flow provided', () => {
    const form: FormDefinition = { fields: [] };
    const flow: FlowDefinition = { steps: [{ kind: 'FORM', key: 'a', title: { de: 'A' } }] };
    service.createDraftVersion('at1', form, flow).subscribe();
    const req = http.expectOne('/api/v1/antragstyp/at1/versions');
    expect(req.request.method).toBe('POST');
    expect(req.request.body.formDefinition).toEqual(form);
    expect(req.request.body.flowDefinition).toEqual(flow);
    req.flush({ id: 'v1' });
  });

  it('createDraftVersion omits flowDefinition when flow is null', () => {
    service.createDraftVersion('at1', { fields: [] }, null).subscribe();
    const req = http.expectOne('/api/v1/antragstyp/at1/versions');
    expect('flowDefinition' in req.request.body).toBe(false);
    req.flush({ id: 'v1' });
  });

  it('createDraftVersion includes graphDefinition when provided', () => {
    const graph = { nodes: [{ id: 'n1', type: 'START', position: { x: 0, y: 0 }, data: {} }], edges: [] };
    service.createDraftVersion('at1', { fields: [] }, null, graph).subscribe();
    const req = http.expectOne('/api/v1/antragstyp/at1/versions');
    expect(req.request.body.graphDefinition).toEqual(graph);
    expect('flowDefinition' in req.request.body).toBe(false);
    req.flush({ id: 'v1' });
  });

  it('createDraftVersion omits graphDefinition when null (default)', () => {
    service.createDraftVersion('at1', { fields: [] }, null).subscribe();
    const req = http.expectOne('/api/v1/antragstyp/at1/versions');
    expect('graphDefinition' in req.request.body).toBe(false);
    req.flush({ id: 'v1' });
  });

  it('editDraft PUTs form + flow', () => {
    const flow: FlowDefinition = { steps: [] };
    service.editDraft('v1', { fields: [] }, flow).subscribe();
    const req = http.expectOne('/api/v1/antragstyp/versions/v1/draft');
    expect(req.request.method).toBe('PUT');
    req.flush({ id: 'v1' });
  });

  it('editDraft includes graphDefinition when provided', () => {
    const graph = { nodes: [{ id: 'n1', type: 'END', position: { x: 0, y: 0 }, data: {} }], edges: [] };
    service.editDraft('v1', { fields: [] }, null, graph).subscribe();
    const req = http.expectOne('/api/v1/antragstyp/versions/v1/draft');
    expect(req.request.body.graphDefinition).toEqual(graph);
    expect('flowDefinition' in req.request.body).toBe(false);
    req.flush({ id: 'v1' });
  });

  it('editDraft omits graphDefinition when null (default)', () => {
    service.editDraft('v1', { fields: [] }, null).subscribe();
    const req = http.expectOne('/api/v1/antragstyp/versions/v1/draft');
    expect('graphDefinition' in req.request.body).toBe(false);
    req.flush({ id: 'v1' });
  });

  it('publish POSTs to the publish endpoint', () => {
    service.publish('v1').subscribe();
    const req = http.expectOne('/api/v1/antragstyp/versions/v1/publish');
    expect(req.request.method).toBe('POST');
    req.flush({ id: 'v1', status: 'PUBLISHED' });
  });

  it('listActionRefs GETs /action/refs', () => {
    let result: string[] | undefined;
    service.listActionRefs().subscribe(r => (result = r));
    const req = http.expectOne('/api/v1/action/refs');
    expect(req.request.method).toBe('GET');
    req.flush(['provision-ad-account']);
    expect(result).toEqual(['provision-ad-account']);
  });
});
