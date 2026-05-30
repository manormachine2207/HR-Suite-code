import { Component, OnInit, inject, ChangeDetectorRef } from '@angular/core';
import { DOCUMENT, DatePipe } from '@angular/common';
import { FormBuilder, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { TranslateModule } from '@ngx-translate/core';
import { forkJoin } from 'rxjs';

import { AntragService } from './antrag.service';
import { Antrag } from './antrag.model';
import { AntragsTypService } from '../antragstyp/antragstyp.service';
import { AntragsTypSummary } from '../antragstyp/antragstyp.model';
import { FormFieldDef, Lang, OptionDef } from '../form-designer/form-definition.model';

/**
 * Applicant view of own Anträge plus an inline "neuer Antrag" form. The form picks a
 * LIVE Antragstyp, renders the fields of its published major (read model), then
 * create→submit in one go. Submit pins the major and starts the Flowable process
 * (ADR-009 §4/§5) — the resulting `workflowProcessId` is surfaced as a list column,
 * which is exactly the end-to-end proof of the workflow wiring.
 *
 * Built deliberately on the (working) antragstyp-list pattern: plain reactive state,
 * pure view helpers, no getters that allocate during change detection.
 */
@Component({
  selector: 'app-antrag-list',
  standalone: true,
  imports: [TranslateModule, ReactiveFormsModule, DatePipe],
  templateUrl: './antrag-list.component.html',
  styleUrl: './antrag-list.component.scss'
})
export class AntragListComponent implements OnInit {
  private readonly antragService = inject(AntragService);
  private readonly antragstypService = inject(AntragsTypService);
  private readonly fb = inject(FormBuilder);
  private readonly document = inject(DOCUMENT);
  private readonly cdr = inject(ChangeDetectorRef);

  antraege: Antrag[] = [];
  liveTypen: AntragsTypSummary[] = [];
  private typTitleById = new Map<string, string>();

  loading = true;
  failed = false;

  // --- new-antrag form state ---
  creating = false;
  selectedTypId = '';
  fields: FormFieldDef[] = [];
  form: FormGroup = this.fb.group({});
  loadingFields = false;
  submitting = false;
  errorKey = '';
  lastSubmittedId: string | null = null;

  ngOnInit(): void {
    this.reload();
  }

  private reload(): void {
    this.loading = true;
    this.failed = false;
    forkJoin({
      antraege: this.antragService.listOwn(),
      typen: this.antragstypService.list()
    }).subscribe({
      next: ({ antraege, typen }) => {
        this.antraege = antraege;
        this.liveTypen = typen.filter(t => t.status === 'LIVE');
        this.typTitleById = new Map(typen.map(t => [t.id, this.titleOf(t)]));
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

  // --- new-antrag panel ---------------------------------------------------
  openCreate(): void {
    this.creating = true;
    this.errorKey = '';
  }

  cancelCreate(): void {
    this.creating = false;
    this.selectedTypId = '';
    this.fields = [];
    this.form = this.fb.group({});
    this.errorKey = '';
  }

  onTypChange(typId: string): void {
    this.selectedTypId = typId;
    this.fields = [];
    this.form = this.fb.group({});
    this.errorKey = '';
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
        this.errorKey = 'loadFieldsError';
        this.cdr.markForCheck();
      }
    });
  }

  private buildForm(defs: FormFieldDef[]): void {
    const group: Record<string, unknown> = {};
    for (const f of defs) {
      const initial: unknown = f.type === 'BOOLEAN' ? false : f.type === 'MULTI_SELECT' ? [] : '';
      // Required applies to value-bearing controls; a checkbox is valid either way.
      group[f.key] = f.required && f.type !== 'BOOLEAN' ? [initial, Validators.required] : [initial];
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
    const payload = this.form.value as Record<string, unknown>;
    this.antragService.create(this.selectedTypId, payload).subscribe({
      next: created => {
        this.antragService.submit(created.id).subscribe({
          next: submitted => {
            this.lastSubmittedId = submitted.id;
            this.submitting = false;
            this.cancelCreate();
            this.reload();
          },
          error: () => {
            this.submitting = false;
            this.errorKey = 'submitError';
            this.reload();
          }
        });
      },
      error: () => {
        this.submitting = false;
        this.errorKey = 'createError';
        this.cdr.markForCheck();
      }
    });
  }

  // --- pure view helpers --------------------------------------------------
  private titleOf(t: AntragsTypSummary): string {
    const lang = this.document.documentElement.lang || 'de';
    return t.title?.[lang] ?? t.title?.['de'] ?? t.key;
  }

  typTitle(antragstypId: string): string {
    return this.typTitleById.get(antragstypId) ?? antragstypId;
  }

  fieldLabel(f: FormFieldDef): string {
    const lang = (this.document.documentElement.lang || 'de') as Lang;
    return f.label?.[lang] ?? f.label?.de ?? f.key;
  }

  optionLabel(o: OptionDef): string {
    const lang = (this.document.documentElement.lang || 'de') as Lang;
    return o.label?.[lang] ?? o.label?.de ?? o.value;
  }

  statusClass(status: string): string {
    switch (status) {
      case 'SUBMITTED': return 'is-submitted';
      case 'IN_REVIEW': return 'is-review';
      case 'APPROVED': return 'is-approved';
      case 'REJECTED': return 'is-rejected';
      case 'CANCELLED': return 'is-cancelled';
      case 'DRAFT': return 'is-draft';
      default: return 'is-default';
    }
  }
}
