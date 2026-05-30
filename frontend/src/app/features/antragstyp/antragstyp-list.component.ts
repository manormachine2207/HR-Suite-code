import { Component, OnInit, inject, ChangeDetectorRef } from '@angular/core';
import { DOCUMENT } from '@angular/common';
import { RouterLink } from '@angular/router';
import { TranslateModule } from '@ngx-translate/core';

import { AntragsTypService } from './antragstyp.service';
import { AntragsTypSummary } from './antragstyp.model';

@Component({
  selector: 'app-antragstyp-list',
  standalone: true,
  imports: [TranslateModule, RouterLink],
  templateUrl: './antragstyp-list.component.html',
  styleUrl: './antragstyp-list.component.scss'
})
export class AntragstypListComponent implements OnInit {
  private readonly service = inject(AntragsTypService);
  private readonly document = inject(DOCUMENT);
  private readonly cdr = inject(ChangeDetectorRef);

  items: AntragsTypSummary[] = [];
  loading = true;
  failed = false;

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

  /** Resolves the i18n title for the active UI language (set on <html lang> by App). */
  title(item: AntragsTypSummary): string {
    const lang = this.document.documentElement.lang || 'de';
    return item.title?.[lang] ?? item.title?.['de'] ?? item.key;
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
