import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { describe, it, expect, beforeEach, afterEach } from 'vitest';

import { ReviewService, ReviewTask } from './review.service';
import { problemDetailOf } from './review.logic';
import { RuntimeConfigService } from '../../core/runtime-config/runtime-config.service';

const stubConfig = { get: () => ({ apiBaseUrl: '/api/v1' }) } as Partial<RuntimeConfigService>;

describe('ReviewService', () => {
  let service: ReviewService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        ReviewService,
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: RuntimeConfigService, useValue: stubConfig },
      ],
    });
    service = TestBed.inject(ReviewService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('list GETs /task (payload absent in the list contract)', () => {
    let result: ReviewTask[] | undefined;
    service.list().subscribe(r => (result = r));
    const req = http.expectOne('/api/v1/task');
    expect(req.request.method).toBe('GET');
    req.flush([
      {
        taskId: 't1', name: 'Review', stepKey: 'review',
        createdAt: '2026-05-30T07:21:16.326Z', antragId: 'a1', antragStatus: 'SUBMITTED',
        antragstypTitle: { de: 'Spesenantrag' }, antragstellerSubject: 'dev-x',
        allowedOutcomes: [],
      },
    ]);
    expect(result?.length).toBe(1);
    expect(result?.[0].payload).toBeUndefined();
  });

  it('get GETs /task/{id} (detail includes payload)', () => {
    let result: ReviewTask | undefined;
    service.get('t1').subscribe(r => (result = r));
    const req = http.expectOne('/api/v1/task/t1');
    expect(req.request.method).toBe('GET');
    req.flush({
      taskId: 't1', name: 'Review', stepKey: 'review',
      createdAt: '2026-05-30T07:21:16.326Z', allowedOutcomes: ['approve', 'reject'],
      payload: { betrag: 84 },
    });
    expect(result?.payload).toEqual({ betrag: 84 });
  });

  it('complete POSTs outcome + trimmed comment when both are provided', () => {
    service.complete('t1', 'approve', '  passt  ').subscribe();
    const req = http.expectOne('/api/v1/task/t1/complete');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ outcome: 'approve', comment: 'passt' });
    req.flush({ taskId: 't1', name: 'Review', stepKey: 'review', createdAt: '', allowedOutcomes: [], antragStatus: 'APPROVED' });
  });

  it('complete omits outcome when null (step without declared outcomes)', () => {
    service.complete('t1', null, null).subscribe();
    const req = http.expectOne('/api/v1/task/t1/complete');
    expect('outcome' in req.request.body).toBe(false);
    expect('comment' in req.request.body).toBe(false);
    req.flush({ taskId: 't1', name: 'Review', stepKey: 'review', createdAt: '', allowedOutcomes: [], antragStatus: 'COMPLETED' });
  });

  it('complete omits a blank comment', () => {
    service.complete('t1', 'reject', '   ').subscribe();
    const req = http.expectOne('/api/v1/task/t1/complete');
    expect(req.request.body).toEqual({ outcome: 'reject' });
    req.flush({ taskId: 't1', name: 'Review', stepKey: 'review', createdAt: '', allowedOutcomes: [], antragStatus: 'REJECTED' });
  });

  it('complete surfaces the 422 ProblemDetail.detail to the subscriber', () => {
    let caught: unknown;
    service.complete('t1', 'escalate', null).subscribe({ error: e => (caught = e) });
    const req = http.expectOne('/api/v1/task/t1/complete');
    req.flush(
      { type: 'about:blank', title: 'Unprocessable Entity', detail: "Outcome 'escalate' ist nicht erlaubt." },
      { status: 422, statusText: 'Unprocessable Entity' },
    );
    expect(problemDetailOf(caught)).toBe("Outcome 'escalate' ist nicht erlaubt.");
  });

  it('complete propagates a 404 (task gone / foreign tenant) without ProblemDetail mapping', () => {
    let caught: { status?: number } | undefined;
    service.complete('gone', null, null).subscribe({ error: e => (caught = e) });
    const req = http.expectOne('/api/v1/task/gone/complete');
    req.flush({ type: 'about:blank', title: 'Not Found' }, { status: 404, statusText: 'Not Found' });
    expect(caught?.status).toBe(404);
    expect(problemDetailOf(caught)).toBeNull();
  });
});
