import { TestBed } from '@angular/core/testing';
import {
  HttpClient,
  HTTP_INTERCEPTORS,
  provideHttpClient,
  withInterceptorsFromDi,
} from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { describe, it, expect, afterEach, beforeEach } from 'vitest';

import { DevAuthInterceptor } from './dev-auth.interceptor';
import { DEV_ROLE_STORAGE_KEY, devTokenForRole } from './dev-role';
import { RuntimeConfigService } from '../runtime-config/runtime-config.service';

const TENANT_ID = '019e754d-371c-70e0-b199-88ab785bef6e';
const CONFIG_TOKEN = `dev-hr-designer~${TENANT_ID}`;

/** Builds a TestBed whose RuntimeConfigService returns the given (partial) config. */
function setup(config: object): { http: HttpClient; ctrl: HttpTestingController } {
  TestBed.configureTestingModule({
    providers: [
      provideHttpClient(withInterceptorsFromDi()),
      provideHttpClientTesting(),
      { provide: HTTP_INTERCEPTORS, useClass: DevAuthInterceptor, multi: true },
      { provide: RuntimeConfigService, useValue: { get: () => config } },
    ],
  });
  return { http: TestBed.inject(HttpClient), ctrl: TestBed.inject(HttpTestingController) };
}

describe('DevAuthInterceptor (SDR-001 + ADR-016 dev role override)', () => {
  beforeEach(() => localStorage.removeItem(DEV_ROLE_STORAGE_KEY));
  afterEach(() => {
    localStorage.removeItem(DEV_ROLE_STORAGE_KEY);
    TestBed.inject(HttpTestingController).verify();
  });

  it('attaches the configured dev token when no override is stored', () => {
    const { http, ctrl } = setup({ devAuth: { enabled: true, token: CONFIG_TOKEN } });
    http.get('/api/v1/antragstyp').subscribe();
    const req = ctrl.expectOne('/api/v1/antragstyp');
    expect(req.request.headers.get('Authorization')).toBe(`Bearer ${CONFIG_TOKEN}`);
    req.flush([]);
  });

  it('prefers the localStorage identity, rebuilding the token with the configured tenant id', () => {
    localStorage.setItem(DEV_ROLE_STORAGE_KEY, 'approver-hal');
    const { http, ctrl } = setup({ devAuth: { enabled: true, token: CONFIG_TOKEN } });
    http.get('/api/v1/task').subscribe();
    const req = ctrl.expectOne('/api/v1/task');
    expect(req.request.headers.get('Authorization')).toBe(`Bearer dev-approver-hal~${TENANT_ID}`);
    req.flush([]);
  });

  it('ignores the localStorage identity when devAuth is absent (prod config)', () => {
    localStorage.setItem(DEV_ROLE_STORAGE_KEY, 'approver-hal');
    const { http, ctrl } = setup({});   // no devAuth block at all
    http.get('/api/v1/task').subscribe();
    const req = ctrl.expectOne('/api/v1/task');
    expect(req.request.headers.has('Authorization')).toBe(false);
    req.flush([]);
  });

  it('ignores the localStorage identity when devAuth is disabled', () => {
    localStorage.setItem(DEV_ROLE_STORAGE_KEY, 'approver-hal');
    const { http, ctrl } = setup({ devAuth: { enabled: false, token: CONFIG_TOKEN } });
    http.get('/api/v1/task').subscribe();
    const req = ctrl.expectOne('/api/v1/task');
    expect(req.request.headers.has('Authorization')).toBe(false);
    req.flush([]);
  });

  it('keeps the configured token when it has no tenant suffix (override not buildable)', () => {
    localStorage.setItem(DEV_ROLE_STORAGE_KEY, 'approver-hal');
    const { http, ctrl } = setup({ devAuth: { enabled: true, token: 'dev-platform-admin' } });
    http.get('/api/v1/tenant').subscribe();
    const req = ctrl.expectOne('/api/v1/tenant');
    expect(req.request.headers.get('Authorization')).toBe('Bearer dev-platform-admin');
    req.flush([]);
  });

  it('leaves non-API requests untouched even with devAuth + override present', () => {
    localStorage.setItem(DEV_ROLE_STORAGE_KEY, 'approver-hal');
    const { http, ctrl } = setup({ devAuth: { enabled: true, token: CONFIG_TOKEN } });
    http.get('assets/i18n/de.json').subscribe();
    const req = ctrl.expectOne('assets/i18n/de.json');
    expect(req.request.headers.has('Authorization')).toBe(false);
    req.flush({});
  });
});

describe('devTokenForRole', () => {
  it('builds dev-<role>~<tenant> from the configured token', () => {
    expect(devTokenForRole(CONFIG_TOKEN, 'applicant')).toBe(`dev-applicant~${TENANT_ID}`);
  });

  it('returns null without a tenant suffix', () => {
    expect(devTokenForRole('dev-platform-admin', 'applicant')).toBeNull();
    expect(devTokenForRole('dev-x~', 'applicant')).toBeNull();
  });
});
