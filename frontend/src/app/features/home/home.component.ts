import { Component, afterNextRender, computed, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { MatIconModule } from '@angular/material/icon';

import { MODULE_CATALOG, ModuleCardDef, filterModules } from './module-catalog';
import { countDecidedAntraege, countOpenAntraege } from './dashboard-kpis.logic';
import { TourService } from '../../core/tour/tour.service';
import { AntragService } from '../antrag/antrag.service';
import { ReviewService } from '../review/review.service';

/** localStorage key holding the favorite module ids as a JSON string[]. */
export const FAVORITES_STORAGE_KEY = 'hrsuite.favoriteModules';

/** Load state of one KPI data source ('hidden' = card disappears, see below). */
export type KpiState = 'loading' | 'loaded' | 'error' | 'hidden';

/**
 * Module dashboard on '/' (ADR-014, FATIP card pattern): greeting, live search over
 * the translated card texts, favorites-only toggle and a responsive card grid.
 *
 * „Meine Übersicht" (ADR-017 Stufe 1): a read-only KPI row above the module grid,
 * fed from the existing list APIs — own Anträge (`/antrag`) yield "offen" and
 * "entschieden" counts (pure logic in dashboard-kpis.logic.ts), the reviewer inbox
 * (`/task`) yields the open-task count. `/task` answers 403 for non-reviewers, so
 * ANY task error hides that card entirely (role-dependent visibility by error —
 * the dashboard has no real role model until identity-sp lands). Antrag errors
 * keep the cards visible with an em-dash placeholder instead.
 *
 * Zoneless discipline: all view state is Signals; the visible list is a `computed`
 * over catalog × search × favorites × active language (the `lang` signal makes the
 * computed re-evaluate after a language switch, because the search matches against
 * *translated* texts — BDR-005). The KPI subscribe callbacks only set() Signals,
 * which schedules zoneless CD — so no markForCheck needed anywhere.
 *
 * A11y (SDR-003): the whole card is one real <a> (stretched-link pattern keeps the
 * favorite <button> outside the anchor — no nested interactive controls), the star
 * carries aria-pressed, the search input has an explicit <label>. Each KPI card is
 * a real <a> whose aria-label carries count + label.
 */
@Component({
  selector: 'app-home',
  standalone: true,
  imports: [TranslateModule, RouterLink, MatIconModule],
  templateUrl: './home.component.html',
  styleUrl: './home.component.scss'
})
export class HomeComponent {
  private readonly translate = inject(TranslateService);
  private readonly tour = inject(TourService);
  private readonly antragService = inject(AntragService);
  private readonly reviewService = inject(ReviewService);

  // --- KPI row („Meine Übersicht", ADR-017 Stufe 1) -------------------------
  /** Own-Antrag KPIs: 'error' renders the cards with an em-dash placeholder. */
  readonly antragKpiState = signal<KpiState>('loading');
  readonly openAntraegeCount = signal(0);
  readonly decidedAntraegeCount = signal(0);
  /** Task KPI: 'hidden' (any HTTP error, esp. 403) removes the card entirely. */
  readonly taskKpiState = signal<KpiState>('loading');
  readonly openTaskCount = signal(0);

  readonly search = signal('');
  readonly onlyFavorites = signal(false);
  readonly favorites = signal<readonly string[]>(readStoredFavorites());
  /** Active UI language — dependency that re-runs the filter on language switch. */
  private readonly lang = signal(this.translate.getCurrentLang() || 'de');

  /** Cards visible under the current search / favorites / language. */
  readonly modules = computed<ModuleCardDef[]>(() => {
    this.lang(); // establish the language dependency (search matches translated texts)
    return filterModules(
      MODULE_CATALOG,
      this.search(),
      this.onlyFavorites(),
      this.favorites(),
      key => this.translate.instant(key)
    );
  });

  constructor() {
    this.translate.onLangChange
      .pipe(takeUntilDestroyed())
      .subscribe(e => this.lang.set(e.lang));

    // KPI sources (ADR-017 Stufe 1) — read-only counts from the existing APIs.
    this.antragService.listOwn()
      .pipe(takeUntilDestroyed())
      .subscribe({
        next: antraege => {
          this.openAntraegeCount.set(countOpenAntraege(antraege));
          this.decidedAntraegeCount.set(countDecidedAntraege(antraege));
          this.antragKpiState.set('loaded');
        },
        error: () => this.antragKpiState.set('error'),
      });
    this.reviewService.list()
      .pipe(takeUntilDestroyed())
      .subscribe({
        next: tasks => {
          this.openTaskCount.set(tasks.length);
          this.taskKpiState.set('loaded');
        },
        // 403 for applicant/hr-designer (and any other failure): hide the card.
        error: () => this.taskKpiState.set('hidden'),
      });

    // ADR-015 Ebene 3: auto-start the dashboard tour on the very first visit
    // (localStorage-flagged, never again). afterNextRender = browser-only and
    // the card grid exists, so the tour anchors are measurable (zoneless-safe:
    // driver.js only overlays the DOM, no Angular bindings involved).
    afterNextRender(() => this.tour.maybeAutoStartDashboardTour());
  }

  isFavorite(id: string): boolean {
    return this.favorites().includes(id);
  }

  toggleFavorite(id: string): void {
    const next = this.isFavorite(id)
      ? this.favorites().filter(f => f !== id)
      : [...this.favorites(), id];
    this.favorites.set(next);
    try {
      localStorage.setItem(FAVORITES_STORAGE_KEY, JSON.stringify(next));
    } catch {
      // Storage unavailable (e.g. private mode) — favorites stay session-only.
    }
  }

  /** Translated toggle label for the star button (aria-label, per card). */
  favoriteLabel(m: ModuleCardDef): string {
    const action = this.isFavorite(m.id) ? 'home.favoriteRemove' : 'home.favoriteAdd';
    return `${this.translate.instant(action)} ${this.translate.instant(m.titleKey)}`;
  }

  // --- KPI view helpers (ADR-017 Stufe 1) -----------------------------------
  /** Rendered value of an Antrag-fed KPI — em-dash when the Antrag call failed. */
  antragKpiValue(count: number): string {
    return this.antragKpiState() === 'error' ? '–' : String(count);
  }

  /** aria-label "<Label>: <Zahl>" for a KPI link card (SDR-003). */
  kpiAria(labelKey: string, value: string | number): string {
    return `${this.translate.instant(labelKey)}: ${value}`;
  }
}

/** Reads the persisted favorites defensively — bad/legacy payloads degrade to []. */
function readStoredFavorites(): string[] {
  try {
    const parsed: unknown = JSON.parse(localStorage.getItem(FAVORITES_STORAGE_KEY) ?? '[]');
    return Array.isArray(parsed) ? parsed.filter((x): x is string => typeof x === 'string') : [];
  } catch {
    return [];
  }
}
