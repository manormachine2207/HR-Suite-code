import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { DecimalPipe } from '@angular/common';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { MatIconModule } from '@angular/material/icon';

import { dateLocaleFor } from '../../core/i18n/locale-text';
import { BreadcrumbComponent } from '../../shared/breadcrumb/breadcrumb.component';
import {
  KANTONE,
  Kanton,
  LohnrechnerErgebnis,
  SAETZE_2025,
  berechneAbzuege,
} from './lohnrechner.logic';

/**
 * Lohnrechner `/lohnrechner` (ADR-018): rein clientseitige Brutto→Netto-Simulation
 * mit den Richtwerten 2025 — keine Backend-Calls, keine Persistenz, keine
 * Personendaten. Eingaben leben ausschliesslich in Signals dieses Components.
 *
 * Zoneless-Disziplin: alle Eingaben sind Signals, das Ergebnis ist ein `computed`
 * über die pure `berechneAbzuege` — jede Formularänderung rechnet live neu, ohne
 * markForCheck. Zahlen werden mit der aktiven Schweizer Locale formatiert
 * (DecimalPipe + `dateLocaleFor`, dieselbe Sprach→Locale-Abbildung wie die
 * Datumsformatierung im Bestand; de-CH liefert die Tausender-Trennung mit ’).
 */
@Component({
  selector: 'app-lohnrechner',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [TranslateModule, DecimalPipe, MatIconModule, BreadcrumbComponent],
  templateUrl: './lohnrechner.component.html',
  styleUrl: './lohnrechner.component.scss',
})
export class LohnrechnerComponent {
  private readonly translate = inject(TranslateService);

  readonly kantone = KANTONE;
  /** Versionierte Richtwerte — im Template für den Banner-/Jahres-Kontext sichtbar. */
  readonly saetze = SAETZE_2025;

  // --- Eingaben (Simulations-Defaults, bewusst kein leeres Formular: geführt) -----
  readonly alter = signal(40);
  readonly kanton = signal<Kanton>('BE');
  readonly jahresbrutto = signal(90_000);
  readonly pensum = signal(100);
  readonly mitQuellensteuer = signal(false);

  /** Aktive UI-Sprache → Schweizer Zahlen-Locale für die DecimalPipe (BDR-005). */
  private readonly lang = signal(this.translate.getCurrentLang() || 'de');
  readonly numberLocale = computed(() => dateLocaleFor(this.lang()));

  /** Live-Neuberechnung: pure Funktion über alle Eingabe-Signals. */
  readonly ergebnis = computed<LohnrechnerErgebnis>(() =>
    berechneAbzuege({
      alter: this.alter(),
      kanton: this.kanton(),
      jahresbrutto: this.jahresbrutto(),
      pensumProzent: this.pensum(),
      mitQuellensteuer: this.mitQuellensteuer(),
    })
  );

  constructor() {
    this.translate.onLangChange
      .pipe(takeUntilDestroyed())
      .subscribe(e => this.lang.set(e.lang));
  }

  /** Zahleneingabe defensiv lesen — leeres/ungültiges Feld zählt als 0. */
  readNumber(event: Event): number {
    const value = (event.target as HTMLInputElement).valueAsNumber;
    return Number.isFinite(value) ? value : 0;
  }

  readKanton(event: Event): Kanton {
    const value = (event.target as HTMLSelectElement).value as Kanton;
    return KANTONE.includes(value) ? value : 'BE';
  }
}
