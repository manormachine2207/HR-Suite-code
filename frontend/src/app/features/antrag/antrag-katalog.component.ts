import {
  ChangeDetectionStrategy,
  ChangeDetectorRef,
  Component,
  OnInit,
  computed,
  inject,
  signal,
} from '@angular/core';
import { Router } from '@angular/router';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { TranslateModule, TranslateService } from '@ngx-translate/core';

import { AntragsTypService } from '../antragstyp/antragstyp.service';
import { AntragsTypSummary } from '../antragstyp/antragstyp.model';
import {
  ANTRAGS_KATEGORIEN,
  AntragsKategorie,
  kategorieOf,
} from '../antragstyp/kategorie.model';
import { resolveLocaleText } from '../../core/i18n/locale-text';
import { BreadcrumbComponent } from '../../shared/breadcrumb/breadcrumb.component';

/**
 * Applicant-facing catalog of all LIVE Antragstypen, grouped by category (ADR-021).
 * Renders one clickable tile per type; clicking navigates to /antraege?neu=<id> so the
 * inline new-request form in AntragListComponent can pick up the selected type.
 *
 * Zoneless discipline: all view state is Signals; subscribe callbacks call
 * cdr.markForCheck() (component is OnPush).
 */
@Component({
  selector: 'app-antrag-katalog',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [TranslateModule, BreadcrumbComponent],
  templateUrl: './antrag-katalog.component.html',
  styleUrl: './antrag-katalog.component.scss',
})
export class AntragKatalogComponent implements OnInit {
  private readonly service = inject(AntragsTypService);
  private readonly router = inject(Router);
  private readonly translate = inject(TranslateService);
  private readonly cdr = inject(ChangeDetectorRef);

  /** All LIVE types returned by the backend. */
  readonly types = signal<AntragsTypSummary[]>([]);
  readonly loading = signal(true);
  readonly failed = signal(false);

  /** Active category filter — 'ALL' means no filter. */
  readonly selectedCategory = signal<AntragsKategorie | 'ALL'>('ALL');

  /** Language signal to re-compute titles on language switch (BDR-005). */
  private readonly lang = signal(this.translate.getCurrentLang() || 'de');

  /**
   * Categories that actually have ≥1 LIVE type, in canonical order.
   * lang() is listed as a dependency so the computed re-evaluates after a
   * language switch (title resolution changes).
   */
  readonly presentCategories = computed<AntragsKategorie[]>(() => {
    this.lang(); // reactive dependency
    const seen = new Set<AntragsKategorie>();
    for (const t of this.types()) {
      seen.add(kategorieOf(t.category));
    }
    // Maintain canonical enum order.
    return ANTRAGS_KATEGORIEN.filter(k => seen.has(k));
  });

  /** Filtered tile list based on the selected category chip. */
  readonly filtered = computed<AntragsTypSummary[]>(() => {
    const cat = this.selectedCategory();
    const all = this.types();
    if (cat === 'ALL') {
      return all;
    }
    return all.filter(t => kategorieOf(t.category) === cat);
  });

  constructor() {
    // Re-evaluate derived titles when the UI language changes (BDR-005).
    this.translate.onLangChange
      .pipe(takeUntilDestroyed())
      .subscribe(e => {
        this.lang.set(e.lang);
        this.cdr.markForCheck();
      });
  }

  ngOnInit(): void {
    this.service.list().subscribe({
      next: types => {
        this.types.set(types.filter(t => t.status === 'LIVE'));
        this.loading.set(false);
        this.cdr.markForCheck();
      },
      error: () => {
        this.failed.set(true);
        this.loading.set(false);
        this.cdr.markForCheck();
      },
    });
  }

  /** Navigate to the inline new-request form pre-selecting this type. */
  start(typId: string): void {
    this.router.navigate(['/antraege'], { queryParams: { neu: typId } });
  }

  /** Resolved localized title for a type (active lang → de → first → key). */
  titleOf(t: AntragsTypSummary): string {
    return resolveLocaleText(t.title, this.lang(), t.key);
  }

  /** Expose kategorieOf so the template can call it without importing it. */
  kategorieOf(value: string | null | undefined): AntragsKategorie {
    return kategorieOf(value);
  }
}
