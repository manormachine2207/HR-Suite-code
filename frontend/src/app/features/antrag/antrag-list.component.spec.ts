import { TestBed } from '@angular/core/testing';
import { provideRouter, Router, ActivatedRoute, convertToParamMap } from '@angular/router';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { TranslateModule } from '@ngx-translate/core';
import { of } from 'rxjs';
import { describe, it, expect, beforeEach, vi, afterEach } from 'vitest';

import { AntragListComponent } from './antrag-list.component';
import { AntragService } from './antrag.service';
import { AntragsTypService } from '../antragstyp/antragstyp.service';
import { RuntimeConfigService } from '../../core/runtime-config/runtime-config.service';

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------
const makeAntragService = () => ({
  listOwn:  vi.fn().mockReturnValue(of([])),
  create:   vi.fn(),
  submit:   vi.fn(),
  cancel:   vi.fn(),
});

const makeAntragsTypService = () => ({
  list:         vi.fn().mockReturnValue(of([])),
  listVersions: vi.fn().mockReturnValue(of([])),
});

// ---------------------------------------------------------------------------
describe('AntragListComponent', () => {
  afterEach(() => vi.restoreAllMocks());

  // -------------------------------------------------------------------------
  // Helper: build module with an optional `neu` query param
  // -------------------------------------------------------------------------
  async function createFixture(neuParam: string | null = null) {
    const antragSvc   = makeAntragService();
    const antragTypSvc = makeAntragsTypService();

    await TestBed.configureTestingModule({
      imports: [AntragListComponent, TranslateModule.forRoot()],
      providers: [
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: AntragService,     useValue: antragSvc },
        { provide: AntragsTypService, useValue: antragTypSvc },
        {
          provide: ActivatedRoute,
          useValue: {
            snapshot: {
              queryParamMap: convertToParamMap(neuParam ? { neu: neuParam } : {}),
            },
          },
        },
        {
          provide: RuntimeConfigService,
          useValue: { get: () => ({ apiBaseUrl: '/api/v1' }) },
        },
      ],
    }).compileComponents();

    // Mock router.navigate so RouterLink / programmatic calls don't throw.
    vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);

    const fixture = TestBed.createComponent(AntragListComponent);
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();

    return { fixture, antragSvc, antragTypSvc };
  }

  // -------------------------------------------------------------------------
  it('without ?neu= param: creating is false and selectedTypId is empty', async () => {
    const { fixture } = await createFixture(null);
    expect(fixture.componentInstance.creating).toBe(false);
    expect(fixture.componentInstance.selectedTypId).toBe('');
  });

  // -------------------------------------------------------------------------
  it('with ?neu=x: auto-opens create form preselected with type x (ADR-021)', async () => {
    // The service stubs must return the type BEFORE the fixture is created so
    // the preselect path (inside reload()'s success callback) can run after
    // liveTypen is populated — that is the race fix being tested here.
    const antragSvc   = makeAntragService();
    const antragTypSvc = makeAntragsTypService();
    antragTypSvc.list.mockReturnValue(of([
      {
        id: 'x',
        key: 'typ-x',
        title: { de: 'Typ X' },
        status: 'LIVE',
        category: null,
        currentVersionId: null,
        description: null,
        createdAt: '',
        updatedAt: '',
      }
    ]));
    // listVersions is called by onTypChange — keep it returning empty so the
    // subscribe resolves without errors.
    antragTypSvc.listVersions.mockReturnValue(of([]));

    await TestBed.configureTestingModule({
      imports: [AntragListComponent, TranslateModule.forRoot()],
      providers: [
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: AntragService,     useValue: antragSvc },
        { provide: AntragsTypService, useValue: antragTypSvc },
        {
          provide: ActivatedRoute,
          useValue: {
            snapshot: {
              queryParamMap: convertToParamMap({ neu: 'x' }),
            },
          },
        },
        {
          provide: RuntimeConfigService,
          useValue: { get: () => ({ apiBaseUrl: '/api/v1' }) },
        },
      ],
    }).compileComponents();

    vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);

    const fixture = TestBed.createComponent(AntragListComponent);
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();

    expect(fixture.componentInstance.creating).toBe(true);
    expect(fixture.componentInstance.selectedTypId).toBe('x');
  });

  // -------------------------------------------------------------------------
  it('"Neuer Antrag" button is a router link to /antraege/neu (ADR-021)', async () => {
    const { fixture } = await createFixture(null);
    // The button must NOT call openCreate() directly any more; it should navigate via routerLink.
    const el: HTMLElement = fixture.nativeElement;
    const btn = el.querySelector('[routerLink="/antraege/neu"], [ng-reflect-router-link="/antraege/neu"], a[href="/antraege/neu"]');
    expect(btn).not.toBeNull();
  });
});
