import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { TranslateModule } from '@ngx-translate/core';
import { of, throwError } from 'rxjs';
import { describe, it, expect, beforeEach, vi, afterEach } from 'vitest';

import { AntragKatalogComponent } from './antrag-katalog.component';
import { AntragsTypService } from '../antragstyp/antragstyp.service';

// ---------------------------------------------------------------------------
// Minimal AntragsTypSummary stubs that match the real interface shape.
// ---------------------------------------------------------------------------
const makeTyp = (over: Partial<{
  id: string; key: string; title: Record<string, string>;
  status: string; category: string | null; currentVersionId: string | null;
  description: Record<string, string> | null; createdAt: string; updatedAt: string;
}> = {}) => ({
  id: 'a',
  key: 'k1',
  title: { de: 'Urlaub' },
  status: 'LIVE',
  category: 'ABSENCE',
  currentVersionId: null,
  description: null,
  createdAt: '',
  updatedAt: '',
  ...over,
});

describe('AntragKatalogComponent', () => {
  let service: { list: ReturnType<typeof vi.fn> };

  beforeEach(async () => {
    service = { list: vi.fn() };

    await TestBed.configureTestingModule({
      imports: [AntragKatalogComponent, TranslateModule.forRoot()],
      providers: [
        provideRouter([]),
        { provide: AntragsTypService, useValue: service },
      ],
    }).compileComponents();
  });

  afterEach(() => vi.restoreAllMocks());

  // -------------------------------------------------------------------------
  it('renders one tile per LIVE type and navigates with ?neu on click', async () => {
    service.list.mockReturnValue(of([
      makeTyp({ id: 'a', key: 'k1', title: { de: 'Urlaub' },    category: 'ABSENCE' }),
      makeTyp({ id: 'b', key: 'k2', title: { de: 'Spesen' },    category: 'FINANCE' }),
    ]));

    // Mock navigate BEFORE creating the component so the router is ready.
    const nav = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);

    const fx = TestBed.createComponent(AntragKatalogComponent);
    fx.detectChanges();
    await fx.whenStable();
    fx.detectChanges();

    const tiles = fx.nativeElement.querySelectorAll('.hr-kachel');
    expect(tiles.length).toBe(2);

    // Clicking the first tile should navigate with ?neu=a.
    (tiles[0] as HTMLElement).click();
    expect(nav).toHaveBeenCalledWith(['/antraege'], { queryParams: { neu: 'a' } });
  });

  // -------------------------------------------------------------------------
  it('filters tiles when a category chip is clicked', async () => {
    service.list.mockReturnValue(of([
      makeTyp({ id: 'a', key: 'k1', title: { de: 'Urlaub' }, category: 'ABSENCE' }),
      makeTyp({ id: 'b', key: 'k2', title: { de: 'Spesen' }, category: 'FINANCE' }),
    ]));
    vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);

    const fx = TestBed.createComponent(AntragKatalogComponent);
    fx.detectChanges();
    await fx.whenStable();
    fx.detectChanges();

    // Both tiles visible initially.
    expect(fx.nativeElement.querySelectorAll('.hr-kachel').length).toBe(2);

    // Click the FINANCE chip (second category chip after "Alle").
    const chips: NodeListOf<HTMLElement> = fx.nativeElement.querySelectorAll('.hr-chip');
    // chips[0] = "Alle", chips[1] = ABSENCE, chips[2] = FINANCE (canonical order)
    const financeChip = Array.from(chips).find(c =>
      c.textContent?.toLowerCase().includes('finance') ||
      // TranslateModule.forRoot() returns the key as-is, so the chip text is the i18n key.
      c.textContent?.includes('FINANCE')
    ) as HTMLElement | undefined;

    expect(financeChip).toBeDefined();
    financeChip!.click();
    fx.detectChanges();

    // Only the FINANCE type should remain.
    expect(fx.nativeElement.querySelectorAll('.hr-kachel').length).toBe(1);
  });

  // -------------------------------------------------------------------------
  it('excludes DRAFT types — only LIVE tiles are rendered', async () => {
    service.list.mockReturnValue(of([
      makeTyp({ id: 'live1', key: 'k1', title: { de: 'Urlaub' }, status: 'LIVE',  category: 'ABSENCE' }),
      makeTyp({ id: 'draft1', key: 'k2', title: { de: 'Entwurf' }, status: 'DRAFT', category: 'ABSENCE' }),
    ]));
    vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);

    const fx = TestBed.createComponent(AntragKatalogComponent);
    fx.detectChanges();
    await fx.whenStable();
    fx.detectChanges();

    // Only the LIVE type should render as a tile; the DRAFT must be excluded.
    const tiles = fx.nativeElement.querySelectorAll('.hr-kachel');
    expect(tiles.length).toBe(1);
    expect((tiles[0] as HTMLElement).getAttribute('aria-label')).toContain('Urlaub');
  });

  // -------------------------------------------------------------------------
  it('shows an empty/error state when the service call fails', async () => {
    service.list.mockReturnValue(throwError(() => new Error('network error')));
    vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);

    const fx = TestBed.createComponent(AntragKatalogComponent);
    fx.detectChanges();
    await fx.whenStable();
    fx.detectChanges();

    // No tiles rendered.
    expect(fx.nativeElement.querySelectorAll('.hr-kachel').length).toBe(0);
    // An error / fallback state element is present.
    expect(fx.nativeElement.querySelector('.hr-state--error')).not.toBeNull();
  });
});
