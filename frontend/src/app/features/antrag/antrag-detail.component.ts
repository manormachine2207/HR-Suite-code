import { ChangeDetectionStrategy, ChangeDetectorRef, Component, OnInit, inject } from '@angular/core';
import { DatePipe } from '@angular/common';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { forkJoin, of } from 'rxjs';
import { catchError } from 'rxjs/operators';

import { AntragService } from './antrag.service';
import { Antrag, AntragProgress, AntragProgressStep } from './antrag.model';
import { StepState, canCancel, stepStates } from './antrag-detail.logic';
import { antragStatusClass, antragStatusKey } from './antrag-status';
import { AntragsTypService } from '../antragstyp/antragstyp.service';
import { AntragsTypSummary } from '../antragstyp/antragstyp.model';
import { PayloadRow, payloadRows } from '../review/review.logic';
import { dateLocaleFor, resolveLocaleText } from '../../core/i18n/locale-text';
import { BreadcrumbComponent } from '../../shared/breadcrumb/breadcrumb.component';

/** One precomputed stepper row (zoneless NG0100 discipline: nothing derived in the template). */
interface StepRow {
  step: AntragProgressStep;
  state: StepState;
}

/** Trace fields that offer a copy-to-clipboard button. */
type CopyField = 'antragId' | 'processId';

/**
 * Antrag detail page (`/antraege/:id`, Cut D): head with localized Antragstyp title
 * + status badge, the approval-chain stepper fed by `GET /antrag/{id}/progress`
 * (Flowable history, ADR-009 §5), the payload as key/value table, a trace box
 * (Antrag-ID / process id, copyable) and the withdraw action for DRAFT/SUBMITTED.
 *
 * A 404/erroring progress call degrades to an empty step list (the endpoint ships
 * with the same cut as this page) — the page must render, not crash.
 */
@Component({
  selector: 'app-antrag-detail',
  standalone: true,
  imports: [TranslateModule, RouterLink, DatePipe, BreadcrumbComponent],
  templateUrl: './antrag-detail.component.html',
  styleUrl: './antrag-detail.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AntragDetailComponent implements OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly antragService = inject(AntragService);
  private readonly antragstypService = inject(AntragsTypService);
  private readonly translate = inject(TranslateService);
  private readonly cdr = inject(ChangeDetectorRef);

  private readonly id = this.route.snapshot.paramMap.get('id') ?? '';
  private typen: AntragsTypSummary[] = [];

  antrag: Antrag | null = null;
  progress: AntragProgress | null = null;
  /** Stepper rows with their derived visual state, in execution order. */
  stepRows: StepRow[] = [];
  rows: PayloadRow[] = [];
  /** Localized Antragstyp title; falls back to the Antrag id. */
  title = '';
  statusKey: string | null = null;
  statusClass = 'is-default';
  cancelAllowed = false;

  loading = true;
  failed = false;
  cancelling = false;
  /** Full i18n key of the current error (translated in the template). */
  errorKey = '';
  /** Server-provided ProblemDetail.detail — shown verbatim. */
  errorDetail = '';
  /** i18n key of the last success confirmation (role=status). */
  successKey = '';
  /** Trace field whose value was just copied (drives the "Copied" feedback). */
  copied: CopyField | null = null;
  /** DatePipe locale for the active UI language (de-CH/fr-CH/it-CH/en-CH). */
  dateLocale = dateLocaleFor('de');

  constructor() {
    this.dateLocale = dateLocaleFor(this.translate.getCurrentLang() || 'de');
    // Language switch: re-resolve the cached jsonb title + date locale in place (BDR-005).
    this.translate.onLangChange
      .pipe(takeUntilDestroyed())
      .subscribe(e => {
        this.dateLocale = dateLocaleFor(e.lang);
        this.applyTitle();
        this.cdr.markForCheck();
      });
  }

  ngOnInit(): void {
    this.reload();
  }

  private reload(): void {
    this.loading = true;
    this.failed = false;
    forkJoin({
      antrag: this.antragService.get(this.id),
      // Progress ships with the same cut as this page: a 404 from an older backend
      // (or any error) degrades to "no steps yet" instead of failing the page.
      progress: this.antragService.progress(this.id).pipe(
        catchError(() => of({ antragId: this.id, workflowProcessId: null, steps: [] } as AntragProgress))
      ),
      // The Antragstyp title is cosmetic — degrade to the raw id when the list fails.
      typen: this.antragstypService.list().pipe(catchError(() => of([] as AntragsTypSummary[]))),
    }).subscribe({
      next: ({ antrag, progress, typen }) => {
        this.antrag = antrag;
        this.progress = progress;
        this.typen = typen;
        this.applyDerivedState();
        this.loading = false;
        this.cdr.markForCheck();
      },
      error: () => {
        this.failed = true;
        this.loading = false;
        this.cdr.markForCheck();
      },
    });
  }

  /** Precomputes everything the template renders (zoneless NG0100 discipline). */
  private applyDerivedState(): void {
    const antrag = this.antrag;
    if (!antrag) {
      return;
    }
    const steps = this.progress?.steps ?? [];
    const states = stepStates(steps, antrag.status);
    this.stepRows = steps.map((step, i) => ({ step, state: states[i] }));
    this.rows = payloadRows(antrag.payload);
    this.statusKey = antragStatusKey(antrag.status);
    this.statusClass = antragStatusClass(antrag.status);
    this.cancelAllowed = canCancel(antrag.status);
    this.applyTitle();
  }

  private applyTitle(): void {
    const antrag = this.antrag;
    if (!antrag) {
      return;
    }
    const typ = this.typen.find(t => t.id === antrag.antragstypId);
    const lang = this.translate.getCurrentLang() || 'de';
    this.title = typ ? resolveLocaleText(typ.title, lang, typ.key) : antrag.id;
  }

  /** Withdraw (cancel) the Antrag after confirmation, then reload Antrag + progress. */
  withdraw(): void {
    if (!this.antrag || !this.cancelAllowed || this.cancelling) {
      return;
    }
    if (!window.confirm(this.translate.instant('antrag.detail.cancelConfirm'))) {
      return;
    }
    this.cancelling = true;
    this.errorKey = '';
    this.errorDetail = '';
    this.successKey = '';
    this.antragService.cancel(this.antrag.id).subscribe({
      next: () => {
        this.cancelling = false;
        this.successKey = 'antrag.detail.cancelSuccess';
        this.cdr.markForCheck();
        this.reload();
      },
      error: e => {
        this.cancelling = false;
        const detail = (e as { error?: { detail?: unknown } })?.error?.detail;
        if (typeof detail === 'string' && detail.trim().length > 0) {
          this.errorDetail = detail;
          this.errorKey = '';
        } else {
          this.errorDetail = '';
          this.errorKey = 'antrag.detail.cancelError';
        }
        this.cdr.markForCheck();
      },
    });
  }

  /** Copies a trace value and flashes a short "Copied" confirmation on the button. */
  copy(value: string, field: CopyField): void {
    navigator.clipboard?.writeText(value).then(
      () => {
        this.copied = field;
        this.cdr.markForCheck();
        setTimeout(() => {
          this.copied = null;
          this.cdr.markForCheck();
        }, 2000);
      },
      () => {
        /* clipboard denied — silently keep the value selectable as text */
      }
    );
  }
}
