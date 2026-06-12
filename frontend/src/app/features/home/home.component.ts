import { Component, afterNextRender, computed, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { MatIconModule } from '@angular/material/icon';

import { MODULE_CATALOG, ModuleCardDef, filterModules } from './module-catalog';
import { TourService } from '../../core/tour/tour.service';

/** localStorage key holding the favorite module ids as a JSON string[]. */
export const FAVORITES_STORAGE_KEY = 'hrsuite.favoriteModules';

/**
 * Module dashboard on '/' (ADR-014, FATIP card pattern): greeting, live search over
 * the translated card texts, favorites-only toggle and a responsive card grid.
 *
 * Zoneless discipline: all view state is Signals; the visible list is a `computed`
 * over catalog × search × favorites × active language (the `lang` signal makes the
 * computed re-evaluate after a language switch, because the search matches against
 * *translated* texts — BDR-005). No subscribe-mutates-field, so no markForCheck.
 *
 * A11y (SDR-003): the whole card is one real <a> (stretched-link pattern keeps the
 * favorite <button> outside the anchor — no nested interactive controls), the star
 * carries aria-pressed, the search input has an explicit <label>.
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
