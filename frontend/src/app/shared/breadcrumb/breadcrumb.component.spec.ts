import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { describe, it, expect, beforeEach } from 'vitest';

import { BreadcrumbComponent } from './breadcrumb.component';

/** Minimal texts — enough to assert node labels + the prepended Dashboard root. */
const DE = {
  nav: {
    dashboard: 'Dashboard',
    breadcrumbAria: 'Brotkrümel-Navigation',
    antragstypen: 'Antragstypen',
  },
  designer: { crumb: 'Designer' },
};

describe('BreadcrumbComponent (ADR-014 nav hub)', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [BreadcrumbComponent, TranslateModule.forRoot()],
      providers: [provideRouter([])],
    }).compileComponents();
    const translate = TestBed.inject(TranslateService);
    translate.setTranslation('de', DE);
    translate.use('de');
  });

  it('renders a labelled nav landmark with the Dashboard root linked to "/"', async () => {
    const fixture = TestBed.createComponent(BreadcrumbComponent);
    fixture.componentInstance.trail = [{ labelKey: 'nav.antragstypen' }];
    await fixture.whenStable();
    fixture.detectChanges();
    const el: HTMLElement = fixture.nativeElement;

    const nav = el.querySelector('nav');
    expect(nav?.getAttribute('aria-label')).toBe('Brotkrümel-Navigation');

    const root = el.querySelector('a');
    expect(root?.getAttribute('href')).toBe('/');
    expect(root?.textContent?.trim()).toBe('Dashboard');
  });

  it('renders N nodes; the last has no link and carries aria-current="page"', async () => {
    const fixture = TestBed.createComponent(BreadcrumbComponent);
    // Dashboard (auto) › Antragstypen (link) › Designer (current)
    fixture.componentInstance.trail = [
      { labelKey: 'nav.antragstypen', link: '/antragstypen' },
      { labelKey: 'designer.crumb' },
    ];
    await fixture.whenStable();
    fixture.detectChanges();
    const el: HTMLElement = fixture.nativeElement;

    // 1 auto Dashboard root + 2 trail nodes = 3 list items
    expect(el.querySelectorAll('li').length).toBe(3);

    // Two links (Dashboard + Antragstypen), the last node is plain text
    const links = Array.from(el.querySelectorAll('a'));
    expect(links.map(a => a.textContent?.trim())).toEqual(['Dashboard', 'Antragstypen']);

    const current = el.querySelector('[aria-current="page"]');
    expect(current?.textContent?.trim()).toBe('Designer');
    expect(current?.tagName.toLowerCase()).toBe('span');
  });

  it('treats a single-node trail as the current page (no link on it)', async () => {
    const fixture = TestBed.createComponent(BreadcrumbComponent);
    fixture.componentInstance.trail = [{ labelKey: 'nav.antragstypen' }];
    await fixture.whenStable();
    fixture.detectChanges();
    const el: HTMLElement = fixture.nativeElement;

    // only the Dashboard root is a link
    expect(el.querySelectorAll('a').length).toBe(1);
    expect(el.querySelector('[aria-current="page"]')?.textContent?.trim()).toBe('Antragstypen');
  });
});
