import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { ObIconService } from '@oblique/oblique';
import { describe, it, expect, beforeEach } from 'vitest';

import { HomeComponent, FAVORITES_STORAGE_KEY } from './home.component';

/** Minimal DE texts so the live search runs over real translated card content. */
const DE = {
  home: {
    modules: {
      antragstypen: { title: 'Antragstypen', description: 'Antragstypen modellieren.' },
      antraege: { title: 'Meine Anträge', description: 'Eigene Anträge stellen.' },
      aufgaben: { title: 'Aufgaben', description: 'Offene Aufgaben prüfen.' },
    },
  },
};

describe('HomeComponent', () => {
  beforeEach(async () => {
    localStorage.clear();
    await TestBed.configureTestingModule({
      imports: [HomeComponent, TranslateModule.forRoot()],
      providers: [provideRouter([])],
    }).compileComponents();
    // Register the bundled Oblique icon set so <mat-icon svgIcon> resolves in jsdom.
    TestBed.inject(ObIconService).registerOnAppInit();
    const translate = TestBed.inject(TranslateService);
    translate.setTranslation('de', DE);
    translate.use('de');
  });

  it('renders the three module cards as real links', async () => {
    const fixture = TestBed.createComponent(HomeComponent);
    await fixture.whenStable();

    const el: HTMLElement = fixture.nativeElement;
    const cards = el.querySelectorAll('.hr-module-card');
    expect(cards).toHaveLength(3);
    const hrefs = [...el.querySelectorAll<HTMLAnchorElement>('a.hr-module-link')]
      .map(a => a.getAttribute('href'));
    expect(hrefs).toEqual(['/antragstypen', '/antraege', '/aufgaben']);
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
