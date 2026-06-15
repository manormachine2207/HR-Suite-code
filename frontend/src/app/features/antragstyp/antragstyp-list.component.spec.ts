import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { TranslateModule } from '@ngx-translate/core';
import { describe, it, expect, beforeEach, vi } from 'vitest';
import { of } from 'rxjs';

import { AntragstypListComponent } from './antragstyp-list.component';
import { AntragsTypService } from './antragstyp.service';
import { RuntimeConfigService } from '../../core/runtime-config/runtime-config.service';

const stubConfig = { get: () => ({ apiBaseUrl: '/api/v1' }) } as Partial<RuntimeConfigService>;

const ROW_STUB = {
  id: 'r1',
  key: 'k',
  title: { de: 'T' },
  status: 'DRAFT',
  category: null,
  currentVersionId: null,
  description: null,
  createdAt: '',
  updatedAt: '',
};

describe('AntragstypListComponent', () => {
  let serviceMock: { list: ReturnType<typeof vi.fn>; setCategory: ReturnType<typeof vi.fn> };

  beforeEach(async () => {
    serviceMock = {
      list: vi.fn(() => of([ROW_STUB])),
      setCategory: vi.fn((id: string, category: string) => of({
        id,
        key: 'k',
        title: { de: 'T' },
        status: 'DRAFT',
        category,
        currentVersionId: null,
        description: null,
        createdAt: '',
        updatedAt: '',
      })),
    };

    await TestBed.configureTestingModule({
      imports: [AntragstypListComponent, TranslateModule.forRoot()],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        { provide: RuntimeConfigService, useValue: stubConfig },
        { provide: AntragsTypService, useValue: serviceMock },
      ],
    }).compileComponents();
  });

  it('renders one row with a category select', async () => {
    const fixture = TestBed.createComponent(AntragstypListComponent);
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();

    const el: HTMLElement = fixture.nativeElement;
    const select = el.querySelector<HTMLSelectElement>('select[aria-label]');
    expect(select).not.toBeNull();
  });

  it('calls service.setCategory(rowId, value) when the category select changes', async () => {
    const fixture = TestBed.createComponent(AntragstypListComponent);
    const component = fixture.componentInstance;
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();

    const el: HTMLElement = fixture.nativeElement;
    const select = el.querySelector<HTMLSelectElement>('select[aria-label]');
    expect(select).not.toBeNull();

    // Simulate user picking 'ABSENCE'
    select!.value = 'ABSENCE';
    select!.dispatchEvent(new Event('change'));

    expect(serviceMock.setCategory).toHaveBeenCalledWith('r1', 'ABSENCE');
    // The local row must be updated from the service response so a later re-render
    // (e.g. onLangChange → markForCheck) does not revert to the stale category.
    expect(component.items[0].category).toBe('ABSENCE');
  });
});
