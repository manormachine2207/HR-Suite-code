import { Component, OnInit, inject, ChangeDetectorRef } from '@angular/core';
import { DatePipe } from '@angular/common';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { TranslateModule, TranslateService } from '@ngx-translate/core';

import { ReviewService, ReviewTask } from './review.service';
import { PayloadRow, outcomeLabelKey, payloadRows, problemDetailOf, statusKeyOf } from './review.logic';
import { dateLocaleFor, resolveLocaleText } from '../../core/i18n/locale-text';

/**
 * Reviewer worklist (`/aufgaben`, ADR-013): open Flowable review tasks of the caller's
 * tenant. Selecting a row loads the task detail (payload key/value table), offers a
 * comment box and one action button per declared outcome — or a single "complete"
 * button when the step declares none. A successful complete shows the NEW Antrag
 * status (role=status) and reloads the list; 404/422 surface the backend
 * ProblemDetail.detail (role=alert). Callers without hr-reviewer/tenant-admin get a
 * translated 403 notice instead of the generic load error.
 *
 * Zoneless discipline: every async callback (next AND error) calls cdr.markForCheck();
 * titles/payload rows are precomputed (no allocating calls in the template) and
 * re-resolved on TranslateService.onLangChange (BDR-005).
 */
@Component({
  selector: 'app-task-list',
  standalone: true,
  imports: [TranslateModule, DatePipe],
  templateUrl: './task-list.component.html',
  styleUrl: './task-list.component.scss'
})
export class TaskListComponent implements OnInit {
  private readonly reviewService = inject(ReviewService);
  private readonly translate = inject(TranslateService);
  private readonly cdr = inject(ChangeDetectorRef);

  tasks: ReviewTask[] = [];
  private titleByTaskId = new Map<string, string>();

  loading = true;
  failed = false;
  /** 403 from the list endpoint — caller lacks hr-reviewer/tenant-admin. */
  forbidden = false;
  /** DatePipe locale for the active UI language (de-CH/fr-CH/it-CH/en-CH). */
  dateLocale = dateLocaleFor('de');

  // --- detail / complete state ---
  selectedId: string | null = null;
  detail: ReviewTask | null = null;
  detailLoading = false;
  /** Precomputed payload key/value rows of the loaded detail. */
  detailRows: PayloadRow[] = [];
  comment = '';
  completing = false;
  /** Full i18n key of the current error (translated in the template). */
  errorKey = '';
  /** Server-provided ProblemDetail.detail (404/422) — shown verbatim. */
  errorDetail = '';
  /** i18n key of the last success confirmation (role=status). */
  successKey = '';
  /** Raw NEW Antrag status returned by complete — rendered translated next to successKey. */
  successStatus: string | null = null;

  constructor() {
    this.dateLocale = dateLocaleFor(this.translate.getCurrentLang() || 'de');
    // Language switch: re-resolve cached jsonb titles + date locale in place (BDR-005).
    this.translate.onLangChange
      .pipe(takeUntilDestroyed())
      .subscribe(e => {
        this.dateLocale = dateLocaleFor(e.lang);
        this.rebuildTitleMap();
        this.cdr.markForCheck();
      });
  }

  ngOnInit(): void {
    this.reload();
  }

  private reload(): void {
    this.loading = true;
    this.failed = false;
    this.forbidden = false;
    this.reviewService.list().subscribe({
      next: tasks => {
        this.tasks = tasks;
        this.rebuildTitleMap();
        this.loading = false;
        this.cdr.markForCheck();
      },
      error: (e: { status?: number }) => {
        this.loading = false;
        if (e?.status === 403) {
          this.forbidden = true;
        } else {
          this.failed = true;
        }
        this.cdr.markForCheck();
      }
    });
  }

  private rebuildTitleMap(): void {
    const lang = this.translate.getCurrentLang() || 'de';
    this.titleByTaskId = new Map(this.tasks.map(t =>
      [t.taskId, resolveLocaleText(t.antragstypTitle, lang, t.stepKey || t.name)]));
  }

  // --- detail panel ---------------------------------------------------------
  toggleDetail(t: ReviewTask): void {
    if (this.selectedId === t.taskId) {
      this.closeDetail();
      return;
    }
    this.selectedId = t.taskId;
    this.detail = null;
    this.detailRows = [];
    this.comment = '';
    this.errorKey = '';
    this.errorDetail = '';
    this.successKey = '';
    this.successStatus = null;
    this.detailLoading = true;
    this.reviewService.get(t.taskId).subscribe({
      next: detail => {
        this.detail = detail;
        this.detailRows = payloadRows(detail.payload);
        this.detailLoading = false;
        this.cdr.markForCheck();
      },
      error: e => {
        this.detailLoading = false;
        this.selectedId = null;
        this.applyError(e, 'review.loadDetailError');
        this.cdr.markForCheck();
      }
    });
  }

  closeDetail(): void {
    this.selectedId = null;
    this.detail = null;
    this.detailRows = [];
    this.comment = '';
    this.detailLoading = false;
  }

  /** Completes the selected task; `outcome` is null for steps without declared outcomes. */
  completeTask(outcome: string | null): void {
    if (!this.detail || this.completing) {
      return;
    }
    this.completing = true;
    this.errorKey = '';
    this.errorDetail = '';
    this.successKey = '';
    this.successStatus = null;
    this.reviewService.complete(this.detail.taskId, outcome, this.comment).subscribe({
      next: completed => {
        this.completing = false;
        this.successKey = 'review.completeSuccess';
        this.successStatus = completed.antragStatus ?? null;
        this.closeDetail();
        this.cdr.markForCheck();
        this.reload();
      },
      error: (e: { status?: number }) => {
        this.completing = false;
        this.applyError(e, 'review.completeError');
        this.cdr.markForCheck();
        if (e?.status === 404) {
          // Task vanished (completed elsewhere / foreign tenant) — resync the list.
          this.closeDetail();
          this.reload();
        }
      }
    });
  }

  /** Prefers the backend ProblemDetail.detail (404/422) over the generic fallback key. */
  private applyError(e: unknown, fallbackKey: string): void {
    const detail = problemDetailOf(e);
    if (detail) {
      this.errorDetail = detail;
      this.errorKey = '';
    } else {
      this.errorDetail = '';
      this.errorKey = fallbackKey;
    }
  }

  // --- pure view helpers (non-allocating, safe for zoneless templates) ------
  titleOf(taskId: string): string {
    return this.titleByTaskId.get(taskId) ?? taskId;
  }

  outcomeKey(outcome: string): string | null {
    return outcomeLabelKey(outcome);
  }

  statusKey(status: string): string | null {
    return statusKeyOf(status);
  }

  statusClass(status: string): string {
    switch (status) {
      case 'SUBMITTED': return 'is-submitted';
      case 'IN_REVIEW': return 'is-review';
      case 'APPROVED': return 'is-approved';
      case 'COMPLETED': return 'is-approved';
      case 'REJECTED': return 'is-rejected';
      case 'CANCELLED': return 'is-cancelled';
      case 'DRAFT': return 'is-draft';
      default: return 'is-default';
    }
  }
}
