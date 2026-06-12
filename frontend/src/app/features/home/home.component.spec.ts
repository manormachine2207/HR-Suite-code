import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { ObIconService } from '@oblique/oblique';
import { describe, it, expect, beforeEach, vi } from 'vitest';

import { HomeComponent, FAVORITES_STORAGE_KEY } from './home.component';
import { TourService } from '../../core/tour/tour.service';

/** Minimal DE texts so the live search runs over real translated card content. */
const DE = {
  home: {
    modules: {
      antragstypen: { title: 'Antragstypen', description: 'Antragstypen modellieren.' },
      antraege: { title: 'Meine Anträge', description: 'Eigene Anträge stellen.' },
      aufgaben: { title: 'Aufgaben', description: 'Offene Aufgaben prüfen.' },
      lohnrechner: {
        title: 'Lohnrechner',
        description: 'Brutto-Netto-Simulation mit Richtwerten 2025.',
        badge: 'Simulation',
      },
      hilfe: { title: 'Hilfe', description: 'Anleitungen und geführte Touren.' },
    },
  },
};

/** TourService stub: driver.js must never mount its overlay inside jsdom tests. */
const tourStub = { maybeAutoStartDashboardTour: vi.fn(() => false) };

describe('HomeComponent', () => {
  beforeEach(async () => {
    localStorage.clear();
    tourStub.maybeAutoStartDashboardTour.mockClear();
    await TestBed.configureTestingModule({
      imports: [HomeComponent, TranslateModule.forRoot()],
      providers: [provideRouter([]), { provide: TourService, useValue: tourStub }],
    }).compileComponents();
    // Register the bundled Oblique icon set so <mat-icon svgIcon> resolves in jsdom.
    TestBed.inject(ObIconService).registerOnAppInit();
    const translate = TestBed.inject(TranslateService);
    translate.setTranslation('de', DE);
    translate.use('de');
  });

  it('renders the five module cards as real links', async () => {
    const fixture = TestBed.createComponent(HomeComponent);
    await fixture.whenStable();

    const el: HTMLElement = fixture.nativeElement;
    const cards = el.querySelectorAll('.hr-module-card');
    expect(cards).toHaveLength(5);
    const hrefs = [...el.querySelectorAll<HTMLAnchorElement>('a.hr-module-link')]
      .map(a => a.getAttribute('href'));
    expect(hrefs).toEqual(['/antragstypen', '/antraege', '/aufgaben', '/lohnrechner', '/hilfe']);
  });

  it('Lohnrechner card carries the "Simulation" maturity badge, the others the product tag (ADR-018)', async () => {
    const fixture = TestBed.createComponent(HomeComponent);
    await fixture.whenStable();

    const el: HTMLElement = fixture.nativeElement;
    const tags = [...el.querySelectorAll('.hr-tag')].map(t => t.textContent?.trim());
    // 'home.tag' is untranslated in this spec → the raw key identifies the default tag.
    expect(tags).toEqual(['home.tag', 'home.tag', 'home.tag', 'Simulation', 'home.tag']);
    expect(el.querySelectorAll('.hr-tag--badge')).toHaveLength(1);
  });

  it('offers the dashboard tour auto-start exactly once after the first render (ADR-015)', async () => {
    const fixture = TestBed.createComponent(HomeComponent);
    await fixture.whenStable();

    expect(tourStub.maybeAutoStartDashboardTour).toHaveBeenCalledTimes(1);
  });

  it('anchors the guided tour via data-tour attributes (search, star, first card)', async () => {
    const fixture = TestBed.createComponent(HomeComponent);
    await fixture.whenStable();

    const el: HTMLElement = fixture.nativeElement;
    expect(el.querySelector('[data-tour="dashboard-search"]')).not.toBeNull();
    expect(el.querySelectorAll('[data-tour="dashboard-favorite"]')).toHaveLength(1);
    expect(el.querySelectorAll('[data-tour="dashboard-card"]')).toHaveLength(1);
  });

  it('search field filters the cards live (translated title, case-insensitive)', async () => {
    const fixture = TestBed.createComponent(HomeComponent);
    await fixture.whenStable();

    const el: HTMLElement = fixture.nativeElement;
    const input = el.querySelector<HTMLInputElement>('#module-search')!;
    input.value = 'aufgaben';
    input.dispatchEvent(new Event('input'));
    await fixture.whenStable();

    const titles = [...el.querySelectorAll('.hr-module-link')].map(a => a.textContent?.trim());
    expect(titles).toEqual(['Aufgaben']);
  });

  it('shows the translated empty message when nothing matches', async () => {
    const fixture = TestBed.createComponent(HomeComponent);
    fixture.componentInstance.search.set('gibtsnicht');
    await fixture.whenStable();

    const el: HTMLElement = fixture.nativeElement;
    expect(el.querySelectorAll('.hr-module-card')).toHaveLength(0);
    expect(el.querySelector('[role="status"]')).not.toBeNull();
  });

  it('star toggles aria-pressed and persists to localStorage', async () => {
    const fixture = TestBed.createComponent(HomeComponent);
    await fixture.whenStable();

    const el: HTMLElement = fixture.nativeElement;
    const star = el.querySelector<HTMLButtonElement>('.hr-fav-btn')!;
    expect(star.getAttribute('aria-pressed')).toBe('false');

    star.click();
    await fixture.whenStable();

    expect(star.getAttribute('aria-pressed')).toBe('true');
    expect(JSON.parse(localStorage.getItem(FAVORITES_STORAGE_KEY)!)).toEqual(['antragstypen']);
  });

  it('favorites-only toggle restricts the grid to starred modules', async () => {
    localStorage.setItem(FAVORITES_STORAGE_KEY, JSON.stringify(['antraege']));
    const fixture = TestBed.createComponent(HomeComponent);
    fixture.componentInstance.onlyFavorites.set(true);
    await fixture.whenStable();

    const el: HTMLElement = fixture.nativeElement;
    const titles = [...el.querySelectorAll('.hr-module-link')].map(a => a.textContent?.trim());
    expect(titles).toEqual(['Meine Anträge']);
  });
});
