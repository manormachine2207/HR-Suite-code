import { ChangeDetectionStrategy, Component, OnDestroy, OnInit, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { Router } from '@angular/router';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { MatIconModule } from '@angular/material/icon';

import { NotificationApiService } from './notification.service';
import { NotificationItem } from './notification.model';
import { antragStatusClass, antragStatusKey } from '../antrag/antrag-status';
import { dateLocaleFor } from '../../core/i18n/locale-text';

/**
 * Header notification bell (ADR-017 Stufe 2). Shows the unread count as a badge,
 * opens a dropdown of the recipient's notifications, and deep-links to the antrag.
 * Polls the unread count every 30s; the list is fetched when the bell is opened.
 *
 * Signal-based so the zoneless scheduler re-renders on every state change — including
 * the interval poll, which runs outside Angular but updates a signal.
 */
@Component({
  selector: 'app-notification-bell',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [TranslateModule, MatIconModule, DatePipe],
  templateUrl: './notification-bell.component.html',
  styleUrl: './notification-bell.component.scss',
})
export class NotificationBellComponent implements OnInit, OnDestroy {
  private readonly api = inject(NotificationApiService);
  private readonly router = inject(Router);
  private readonly translate = inject(TranslateService);

  readonly unread = signal(0);
  readonly items = signal<NotificationItem[]>([]);
  readonly open = signal(false);
  readonly dateLocale = signal(dateLocaleFor('de'));

  private pollHandle: ReturnType<typeof setInterval> | null = null;

  constructor() {
    this.dateLocale.set(dateLocaleFor(this.translate.getCurrentLang() || 'de'));
    this.translate.onLangChange.pipe(takeUntilDestroyed())
      .subscribe(e => this.dateLocale.set(dateLocaleFor(e.lang)));
  }

  ngOnInit(): void {
    this.refreshCount();
    // Poll the unread count; the count query is cheap and tenant-scoped.
    this.pollHandle = setInterval(() => this.refreshCount(), 30_000);
  }

  ngOnDestroy(): void {
    if (this.pollHandle) {
      clearInterval(this.pollHandle);
    }
  }

  toggle(): void {
    const next = !this.open();
    this.open.set(next);
    if (next) {
      this.loadList();
    }
  }

  close(): void {
    this.open.set(false);
  }

  onItem(n: NotificationItem): void {
    if (!n.read) {
      this.api.markRead(n.id).subscribe({ next: () => this.refreshCount(), error: () => {} });
    }
    this.close();
    if (n.antragId) {
      this.router.navigate(['/antraege', n.antragId]);
    }
  }

  markAllRead(): void {
    this.api.markAllRead().subscribe({
      next: () => { this.loadList(); this.refreshCount(); },
      error: () => {},
    });
  }

  private refreshCount(): void {
    // A 403/None (e.g. platform-admin without tenant) just yields 0 — never an error toast.
    this.api.unreadCount().subscribe({
      next: r => this.unread.set(r.count),
      error: () => this.unread.set(0),
    });
  }

  private loadList(): void {
    this.api.list().subscribe({
      next: list => this.items.set(list),
      error: () => this.items.set([]),
    });
  }

  // --- view helpers -------------------------------------------------------
  statusKey(n: NotificationItem): string | null {
    return antragStatusKey(String(n.params?.['status'] ?? ''));
  }

  statusClass(n: NotificationItem): string {
    return antragStatusClass(String(n.params?.['status'] ?? ''));
  }
}
