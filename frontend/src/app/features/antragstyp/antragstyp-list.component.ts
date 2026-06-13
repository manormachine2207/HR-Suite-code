import { Component, OnInit, inject, ChangeDetectorRef } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';
import { TranslateModule, TranslateService } from '@ngx-translate/core';

import { AntragsTypService } from './antragstyp.service';
import { AntragsTypSummary } from './antragstyp.model';
import { resolveLocaleText } from '../../core/i18n/locale-text';
import { BreadcrumbComponent } from '../../shared/breadcrumb/breadcrumb.component';

/** Statuses with a `status.*` translation (new backend statuses fall back to the raw enum). */
const KNOWN_STATUSES = new Set(['DRAFT', 'LIVE', 'DEPRECATED', 'ARCHIVED']);

@Component({
  selector: 'app-antragstyp-list',
  standalone: true,
  imports: [TranslateModule, RouterLink, BreadcrumbComponent],
  templateUrl: './antragstyp-list.component.html',
  styleUrl: './antragstyp-list.component.scss'
})
export class AntragstypListComponent implements OnInit {
  private readonly service = inject(AntragsTypService);
  private readonly translate = inject(TranslateService);
  private readonly cdr = inject(ChangeDetectorRef);

  items: AntragsTypSummary[] = [];
  loading = true;
  failed = false;

  constructor() {
    // Re-render resolved jsonb titles when the user switches the UI language (BDR-005).
    this.translate.onLangChange
      .pipe(takeUntilDestroyed())
      .subscribe(() => this.cdr.markForCheck());
  }

  ngOnInit(): void {
    this.service.list().subscribe({
      next: items => {
        this.items = items;
        this.loading = false;
        this.cdr.markForCheck();
      },
      error: () => {
        this.failed = true;
        this.loading = false;
        this.cdr.markForCheck();
      }
    });
  }

  /** Resolves the i18n title for the active UI language (currentLang → de → first → key). */
  title(item: AntragsTypSummary): string {
    return resolveLocaleText(item.title, this.translate.getCurrentLang() || 'de', item.key);
  }

  /** `status.*` i18n key for known lifecycle statuses, null for unknown (renders raw). */
  statusKey(status: string): string | null {
    return KNOWN_STATUSES.has(status) ? `status.${status}` : null;
  }

  /** Maps the lifecycle status to a badge modifier class (see component SCSS). */
  statusClass(status: string): string {
    switch (status) {
      case 'LIVE': return 'is-live';
      case 'DRAFT': return 'is-draft';
      case 'DEPRECATED': return 'is-deprecated';
      case 'ARCHIVED': return 'is-archived';
      default: return 'is-default';
    }
  }
}
