import { TestBed } from '@angular/core/testing';
import { registerLocaleData } from '@angular/common';
import localeDeCh from '@angular/common/locales/de-CH';
import { TranslateModule } from '@ngx-translate/core';
import { of, throwError } from 'rxjs';
import { describe, it, expect, beforeEach, vi } from 'vitest';

import { MandantenComponent } from './mandanten.component';
import { PlattformService } from '../plattform.service';

// The table renders dates with the de-CH DatePipe locale (registered in app.config
// for prod); register it here so the template render under test doesn't throw.
registerLocaleData(localeDeCh);

const tenant = (over: Partial<Record<string, unknown>> = {}) => ({
  id: 't1', code: 'BIT', displayName: { de: 'BIT Bern' }, subdomain: 'bit',
  status: 'ACTIVE', defaultLocale: 'de',
  createdAt: '2026-01-01T00:00:00Z', updatedAt: '2026-01-01T00:00:00Z', ...over,
});

describe('MandantenComponent', () => {
  let service: { listTenants: ReturnType<typeof vi.fn>; createTenant: ReturnType<typeof vi.fn>; changeStatus: ReturnType<typeof vi.fn> };

  beforeEach(async () => {
    service = { listTenants: vi.fn(), createTenant: vi.fn(), changeStatus: vi.fn() };
    await TestBed.configureTestingModule({
      imports: [MandantenComponent, TranslateModule.forRoot()],
      providers: [{ provide: PlattformService, useValue: service }],
    }).compileComponents();
  });

  it('renders a table row per tenant with status badge', async () => {
    service.listTenants.mockReturnValue(of([tenant(), tenant({ id: 't2', code: 'EFV', status: 'SUSPENDED' })]));
    const fixture = TestBed.createComponent(MandantenComponent);
    fixture.detectChanges();            // triggers ngOnInit → listTenants (sync of())
    await fixture.whenStable();
    fixture.detectChanges();            // flush the rendered rows
    const rows = fixture.nativeElement.querySelectorAll('tbody tr');
    expect(rows).toHaveLength(2);
    expect(fixture.nativeElement.querySelector('.hr-badge.is-active')).not.toBeNull();
    expect(fixture.nativeElement.querySelector('.hr-badge.is-suspended')).not.toBeNull();
  });

  it('shows the operators-only notice on 403 instead of an error', async () => {
    service.listTenants.mockReturnValue(throwError(() => ({ status: 403 })));
    const fixture = TestBed.createComponent(MandantenComponent);
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
    expect(fixture.componentInstance.forbidden).toBe(true);
    expect(fixture.nativeElement.querySelector('[role="note"]')).not.toBeNull();
    expect(fixture.nativeElement.querySelector('table')).toBeNull();
  });

  it('offers suspend + archive actions for an ACTIVE tenant', async () => {
    service.listTenants.mockReturnValue(of([tenant()]));
    const fixture = TestBed.createComponent(MandantenComponent);
    await fixture.whenStable();
    expect(fixture.componentInstance.actionsFor(tenant() as never).map(a => a.target))
      .toEqual(['SUSPENDED', 'ARCHIVED']);
  });
});
