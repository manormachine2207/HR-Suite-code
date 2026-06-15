import { Component, OnInit, inject, ChangeDetectorRef } from '@angular/core';
import { DatePipe } from '@angular/common';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormBuilder, FormGroup, ReactiveFormsModule } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { forkJoin } from 'rxjs';

import { AntragService } from './antrag.service';
import { Antrag } from './antrag.model';
import { initialValueFor, validatorsFor } from './antrag-form.logic';
import { antragStatusClass, antragStatusKey } from './antrag-status';
import { AntragsTypService } from '../antragstyp/antragstyp.service';
import { AntragsTypSummary } from '../antragstyp/antragstyp.model';
import { FormFieldDef, OptionDef } from '../form-designer/form-definition.model';
import { dateLocaleFor, resolveLocaleText } from '../../core/i18n/locale-text';
import { BreadcrumbComponent } from '../../shared/breadcrumb/breadcrumb.component';

/**
 * Applicant view of own Anträge plus an inline "neuer Antrag" form. The form picks a
 * LIVE Antragstyp, renders the fields of its published major (read model), then
 * create→submit in one go. Submit pins the major and starts the Flowable process
 * (ADR-009 §4/§5) — the resulting `workflowProcessId` is surfaced as a list column,
 * which is exactly the end-to-end proof of the workflow wiring.
 *
 * Zoneless discipline: every async subscribe callback that mutates view state calls
 * cdr.markForCheck() — including the nested create→submit chain (a missing call there
 * froze the submit button and tripped NG0100 in dev mode).
 */
@Component({
  selector: 'app-antrag-list',
  standalone: true,
  imports: [TranslateModule, ReactiveFormsModule, DatePipe, RouterLink, BreadcrumbComponent],
  templateUrl: './antrag-list.component.html',
  styleUrl: './antrag-list.component.scss'
})
export class AntragListComponent implements OnInit {
  private readonly antragService = inject(AntragService);
  private readonly antragstypService = inject(AntragsTypService);
  private readonly route = inject(ActivatedRoute);
  private readonly fb = inject(FormBuilder);
  private readonly translate = inject(TranslateService);
  private readonly cdr = inject(ChangeDetectorRef);

  antraege: Antrag[] = [];
  liveTypen: AntragsTypSummary[] = [];
  private typen: AntragsTypSummary[] = [];
  private typTitleById = new Map<string, string>();

  loading = true;
  failed = false;
  /** DatePipe locale for the active UI language (de-CH/fr-CH/it-CH/en-CH). */
  dateLocale = dateLocaleFor('de');

  // --- new-antrag form state ---
  creating = false;
  selectedTypId = '';
  fields: FormFieldDef[] = [];
  form: FormGroup = this.fb.group({});
  loadingFields = false;
  submitting = false;
  /** Full i18n key of the current error (translated in the template). */
  errorKey = '';
  /** Server-provided ProblemDetail.detail (e.g. submit-422) — shown verbatim. */
  errorDetail = '';
  /** i18n key of the last success confirmation (role=status). */
  successKey = '';
  lastSubmittedId: string | null = null;
  /** Antrag id whose row action (submit/cancel) is in flight. */
  rowBusyId: string | null = null;

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
    // Capture the ?neu= param BEFORE reload() so it's available in the
    // type-list callback — the preselect must happen AFTER liveTypen is
    // populated, otherwise the native <select> has no matching <option> yet.
    const neu = this.route.snapshot.queryParamMap.get('neu');
    this.reload(neu ?? undefined);
  }

  private reload(preselectTypId?: string): void {
    this.loading = true;
    this.failed = false;
    forkJoin({
      antraege: this.antragService.listOwn(),
      typen: this.antragstypService.list()
    }).subscribe({
      next: ({ antraege, typen }) => {
        this.antraege = antraege;
        this.typen = typen;
        this.liveTypen = typen.filter(t => t.status === 'LIVE');
        this.rebuildTitleMap();
        this.loading = false;
        // Preselect the type AFTER liveTypen is set so the native <select>
        // already has its <option> elements when [value] is bound (ADR-021).
        if (preselectTypId) {
          this.openCreate();
          this.onTypChange(preselectTypId);
        }
        this.cdr.markForCheck();
      },
      error: () => {
        this.failed = true;
        this.loading = false;
        this.cdr.markForCheck();
      }
    });
  }

  private rebuildTitleMap(): void {
    this.typTitleById = new Map(this.typen.map(t => [t.id, this.titleOf(t)]));
  }

  // --- new-antrag panel ---------------------------------------------------
  openCreate(): void {
    this.creating = true;
    this.errorKey = '';
    this.errorDetail = '';
    this.successKey = '';
  }

  cancelCreate(): void {
    this.creating = false;
    this.selectedTypId = '';
    this.fields = [];
    this.form = this.fb.group({});
    this.errorKey = '';
    this.errorDetail = '';
  }

  onTypChange(typId: string): void {
    this.selectedTypId = typId;
    this.fields = [];
    this.form = this.fb.group({});
    this.errorKey = '';
    this.errorDetail = '';
    if (!typId) {
      return;
    }
    this.loadingFields = true;
    this.antragstypService.listVersions(typId).subscribe({
      next: versions => {
        const published =
          versions.find(v => v.status === 'PUBLISHED') ?? versions.find(v => v.status === 'DEPRECATED');
        this.buildForm(published?.formDefinition?.fields ?? []);
        this.loadingFields = false;
        this.cdr.markForCheck();
      },
      error: () => {
        this.loadingFields = false;
        this.errorKey = 'antrag.loadFieldsError';
        this.cdr.markForCheck();
      }
    });
  }

  private buildForm(defs: FormFieldDef[]): void {
    const group: Record<string, unknown> = {};
    for (const f of defs) {
      // Validators are derived from the published formDefinition (required + TEXT
      // maxLength + NUMBER min/max) so the user gets inline feedback pre-submit.
      group[f.key] = [initialValueFor(f), validatorsFor(f)];
    }
    this.fields = defs;
    this.form = this.fb.group(group);
  }

  createAndSubmit(): void {
    if (!this.selectedTypId || this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.submitting = true;
    this.errorKey = '';
    this.errorDetail = '';
    this.successKey = '';
    const payload = this.form.value as Record<string, unknown>;
    this.antragService.create(this.selectedTypId, payload).subscribe({
      next: created => {
        this.antragService.submit(created.id).subscribe({
          next: submitted => {
            this.lastSubmittedId = submitted.id;
            this.submitting = false;
            this.cancelCreate();
            this.successKey = 'antrag.submitSuccess';
            this.cdr.markForCheck();
            this.reload();
          },
          error: e => {
            this.submitting = false;
            // The draft exists — after reload its row offers Einreichen/Stornieren.
            this.applyError(e, 'antrag.submitError');
            this.cdr.markForCheck();
            this.reload();
          }
        });
      },
      error: e => {
        this.submitting = false;
        this.applyError(e, 'antrag.createError');
        this.cdr.markForCheck();
      }
    });
  }

  // --- row actions for DRAFT Anträge (failed submits) ----------------------
  submitDraft(a: Antrag): void {
    this.rowBusyId = a.id;
    this.errorKey = '';
    this.errorDetail = '';
    this.successKey = '';
    this.antragService.submit(a.id).subscribe({
      next: submitted => {
        this.rowBusyId = null;
        this.lastSubmittedId = submitted.id;
        this.successKey = 'antrag.submitSuccess';
        this.cdr.markForCheck();
        this.reload();
      },
      error: e => {
        this.rowBusyId = null;
        this.applyError(e, 'antrag.submitError');
        this.cdr.markForCheck();
      }
    });
  }

  cancelDraft(a: Antrag): void {
    this.rowBusyId = a.id;
    this.errorKey = '';
    this.errorDetail = '';
    this.successKey = '';
    this.antragService.cancel(a.id).subscribe({
      next: () => {
        this.rowBusyId = null;
        this.successKey = 'antrag.cancelSuccess';
        this.cdr.markForCheck();
        this.reload();
      },
      error: e => {
        this.rowBusyId = null;
        this.applyError(e, 'antrag.cancelError');
        this.cdr.markForCheck();
      }
    });
  }

  /**
   * Prefers the backend ProblemDetail.detail (e.g. the 422 payload-validation message
   * from POST /antrag/{id}/submit) and falls back to a translated generic key.
   */
  private applyError(e: unknown, fallbackKey: string): void {
    const detail = (e as { error?: { detail?: unknown } })?.error?.detail;
    if (typeof detail === 'string' && detail.trim().length > 0) {
      this.errorDetail = detail;
      this.errorKey = '';
    } else {
      this.errorDetail = '';
      this.errorKey = fallbackKey;
    }
  }

  // --- pure view helpers --------------------------------------------------
  private activeLang(): string {
    return this.translate.getCurrentLang() || 'de';
  }

  private titleOf(t: AntragsTypSummary): string {
    return resolveLocaleText(t.title, this.activeLang(), t.key);
  }

  typTitle(antragstypId: string): string {
    return this.typTitleById.get(antragstypId) ?? antragstypId;
  }

  fieldLabel(f: FormFieldDef): string {
    return resolveLocaleText(f.label, this.activeLang(), f.key);
  }

  optionLabel(o: OptionDef): string {
    return resolveLocaleText(o.label, this.activeLang(), o.value);
  }

  /** i18n key of the first validation error of a field, or null when the field is fine. */
  fieldErrorKey(f: FormFieldDef): string | null {
    const c = this.form.get(f.key);
    if (!c || c.valid || !(c.touched || c.dirty)) {
      return null;
    }
    if (c.hasError('required')) {
      return 'antrag.fieldError.required';
    }
    if (c.hasError('maxlength')) {
      return 'antrag.fieldError.maxLength';
    }
    if (c.hasError('min')) {
      return 'antrag.fieldError.min';
    }
    if (c.hasError('max')) {
      return 'antrag.fieldError.max';
    }
    return 'antrag.fieldError.invalid';
  }

  /** Interpolation params ({{limit}}) for the field error message. */
  fieldErrorParams(f: FormFieldDef): Record<string, unknown> {
    return {
      limit: f.validation?.maxLength ?? '',
      min: f.validation?.min ?? '',
      max: f.validation?.max ?? ''
    };
  }

  /** `status.*` i18n key for known Antrag statuses, null for unknown (renders raw). */
  statusKey(status: string): string | null {
    return antragStatusKey(status);
  }

  statusClass(status: string): string {
    return antragStatusClass(status);
  }
}
