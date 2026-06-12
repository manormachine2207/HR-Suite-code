import { TestBed } from '@angular/core/testing';
import { registerLocaleData } from '@angular/common';
import localeDeCh from '@angular/common/locales/de-CH';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { ObIconService } from '@oblique/oblique';
import { describe, it, expect, beforeEach } from 'vitest';

import { LohnrechnerComponent } from './lohnrechner.component';

// app.config.ts registers the Swiss locales at bootstrap; the TestBed boots without
// it, so the DecimalPipe needs the de-CH data registered here as well.
registerLocaleData(localeDeCh);

/** Minimal DE texts — enough to assert banner + table semantics. */
const DE = {
  lohnrechner: {
    title: 'Lohnrechner',
    banner: 'Unverbindliche Simulation mit Richtwerten 2025.',
    position: {
      ahvIvEo: 'AHV/IV/EO', alv: 'ALV', nbu: 'NBU', ktg: 'KTG',
      bvg: 'BVG (Sparbeitrag AN)', qst: 'Quellensteuer',
    },
  },
};

describe('LohnrechnerComponent (ADR-018)', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [LohnrechnerComponent, TranslateModule.forRoot()],
    }).compileComponents();
    TestBed.inject(ObIconService).registerOnAppInit();
    const translate = TestBed.inject(TranslateService);
    translate.setTranslation('de', DE);
    translate.use('de');
  });

  it('renders the simulation banner and recomputes live with de-CH number formatting', async () => {
    const fixture = TestBed.createComponent(LohnrechnerComponent);
    await fixture.whenStable();
    const el: HTMLElement = fixture.nativeElement;

    expect(el.querySelector('.hr-banner')?.textContent)
      .toContain('Unverbindliche Simulation mit Richtwerten 2025.');

    // Standard fixture: Alter 40 · BE · 120'000 · 100 % · QST an → Netto 96'656.62/Jahr.
    const cmp = fixture.componentInstance;
    cmp.jahresbrutto.set(120_000);
    cmp.mitQuellensteuer.set(true);
    await fixture.whenStable();

    const nettoValues = [...el.querySelectorAll('.hr-netto__value')].map(n => n.textContent ?? '');
    // de-CH thousands separator is the right single quote (’).
    expect(nettoValues[0]).toContain('8’054.72'); // Netto/Monat (prominent first)
    expect(nettoValues[1]).toContain('96’656.62'); // Netto/Jahr

    // Deduction table: 6 positions incl. QST + total row.
    const rows = el.querySelectorAll('.hr-abzug-table tbody tr');
    expect(rows).toHaveLength(6);
    expect(rows[rows.length - 1].textContent).toContain('Quellensteuer');
    expect(rows[rows.length - 1].textContent).toContain('10’739.63');
    expect(el.querySelector('.hr-total')?.textContent).toContain('23’343.38');
  });

  it('toggling QST off removes the position and raises the netto (live, zoneless)', async () => {
    const fixture = TestBed.createComponent(LohnrechnerComponent);
    const cmp = fixture.componentInstance;
    cmp.jahresbrutto.set(120_000);
    cmp.mitQuellensteuer.set(true);
    await fixture.whenStable();
    const el: HTMLElement = fixture.nativeElement;

    const checkbox = el.querySelector<HTMLInputElement>('#lr-qst')!;
    checkbox.checked = false;
    checkbox.dispatchEvent(new Event('change'));
    await fixture.whenStable();

    expect(el.querySelectorAll('.hr-abzug-table tbody tr')).toHaveLength(5);
    expect([...el.querySelectorAll('.hr-netto__value')][1].textContent).toContain('107’396.25');
  });
});
