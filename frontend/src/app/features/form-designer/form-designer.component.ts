import { Component, OnInit, AfterViewInit, AfterViewChecked, ViewChild, inject, ChangeDetectorRef } from '@angular/core';
import { UpperCasePipe } from '@angular/common';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormArray, FormBuilder, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { forkJoin, of, Observable } from 'rxjs';
import { catchError, switchMap, tap } from 'rxjs/operators';

import { AntragsTypService } from '../antragstyp/antragstyp.service';
import { AntragsTypSummary } from '../antragstyp/antragstyp.model';
import { AntragsTypVersion } from '../antragstyp/antragstyp-version.model';
import {
  FieldType, FIELD_TYPES, Lang, LANGS, LocaleMap,
  FormDefinition, FormFieldDef, OptionDef, Validation
} from './form-definition.model';
import { FlowCanvasEditorComponent } from './flow-canvas-editor.component';
import { GraphDefinition } from './flow-graph.model';
import { resolveLocaleText } from '../../core/i18n/locale-text';

@Component({
  selector: 'app-form-designer',
  standalone: true,
  imports: [ReactiveFormsModule, TranslateModule, RouterLink, UpperCasePipe, FlowCanvasEditorComponent],
  templateUrl: './form-designer.component.html',
  styleUrl: './form-designer.component.scss'
})
export class FormDesignerComponent implements OnInit, AfterViewInit, AfterViewChecked {
  private readonly route = inject(ActivatedRoute);
  private readonly service = inject(AntragsTypService);
  private readonly fb = inject(FormBuilder);
  private readonly translate = inject(TranslateService);
  private readonly cdr = inject(ChangeDetectorRef);

  @ViewChild(FlowCanvasEditorComponent) flowCanvas?: FlowCanvasEditorComponent;

  readonly langs = LANGS;
  readonly fieldTypes = FIELD_TYPES;

  antragstypId = '';
  antragsTyp?: AntragsTypSummary;
  antragsTypLabel = '';

  readonly fields = this.fb.array<FormGroup>([]);

  loading = true;
  saving = false;
  savedMajor: number | null = null;
  /** i18n key of the current error (BDR-005); `errorDetail` carries ProblemDetail.detail verbatim. */
  errorKey = '';
  errorDetail = '';

  section: 'form' | 'flow' | 'publish' = 'form';
  availableRefs: string[] = [];
  draftVersionId: string | null = null;
  publishedKey: string | null = null;
  publishing = false;
  /** True after a successful publish — blocks a second (409) publish of the same draft. */
  publishDone = false;

  private pendingGraph: GraphDefinition | null = null;
  private canvasSeeded = false;
  /** Serialized editor state at the last successful save (or at load of an existing draft). */
  private lastSavedSnapshot: string | null = null;

  constructor() {
    // Language switch: re-resolve the Antragstyp title shown in the header (BDR-005).
    this.translate.onLangChange
      .pipe(takeUntilDestroyed())
      .subscribe(() => {
        this.antragsTypLabel = this.resolveLabel(this.antragsTyp);
        this.cdr.markForCheck();
      });
  }

  ngOnInit(): void {
    this.antragstypId = this.route.snapshot.paramMap.get('id') ?? '';
    forkJoin({
      antragsTyp: this.service.getById(this.antragstypId).pipe(catchError(() => of(undefined))),
      versions: this.service.listVersions(this.antragstypId).pipe(catchError(() => of([] as AntragsTypVersion[]))),
      refs: this.service.listActionRefs().pipe(catchError(() => of([] as string[]))),
    }).subscribe(({ antragsTyp, versions, refs }) => {
      this.antragsTyp = antragsTyp;
      this.antragsTypLabel = this.resolveLabel(antragsTyp);
      this.availableRefs = refs;

      const draft = versions.find(v => v.status === 'DRAFT');
      const source = draft ?? versions[0];
      this.draftVersionId = draft?.id ?? null;

      const defs = source?.formDefinition?.fields ?? [];
      for (const f of (defs.length ? defs : [null])) {
        this.fields.push(this.buildFieldGroup(f));
      }
      // seed canvas editor once the view (and child) exist
      this.pendingGraph = source?.graphDefinition ?? null;
      this.loading = false;
      this.seedCanvas();
      this.cdr.markForCheck();
    });
  }

  ngAfterViewInit(): void {
    this.seedCanvas();
  }

  ngAfterViewChecked(): void {
    this.seedCanvas();
  }

  private seedCanvas(): void {
    if (!this.canvasSeeded && this.flowCanvas && !this.loading) {
      this.flowCanvas.loadGraph(this.pendingGraph);
      this.canvasSeeded = true;
      // The loaded draft IS the server state — publish without a prior save is fine.
      if (this.draftVersionId) {
        this.lastSavedSnapshot = this.currentSnapshot();
      }
      this.cdr.markForCheck();
    }
  }

  // ---- typed accessors --------------------------------------------------
  labelGroup(field: FormGroup): FormGroup {
    return field.get('label') as FormGroup;
  }

  options(field: FormGroup): FormArray<FormGroup> {
    return field.get('options') as FormArray<FormGroup>;
  }

  optionLabel(option: FormGroup): FormGroup {
    return option.get('label') as FormGroup;
  }

  fieldType(field: FormGroup): FieldType {
    return field.get('type')!.value as FieldType;
  }

  keyError(field: FormGroup): 'required' | 'duplicate' | null {
    const key = (field.get('key')!.value as string).trim();
    if (!key) {
      return 'required';
    }
    const count = this.fields.controls.filter(c => ((c.get('key')!.value as string) ?? '').trim() === key).length;
    return count > 1 ? 'duplicate' : null;
  }

  get canSave(): boolean {
    return !this.saving && this.fields.length > 0 && this.fields.controls.every(c => this.keyError(c as FormGroup) === null);
  }

  /** Publish is disabled whenever a graph exists (compiler is SP1). */
  get canPublish(): boolean {
    return !!this.draftVersionId && !this.flowCanvas?.toGraphDefinition();
  }

  /** True when the editor state differs from the last saved server state. */
  get isDirty(): boolean {
    return this.lastSavedSnapshot === null || this.lastSavedSnapshot !== this.currentSnapshot();
  }

  get previewJson(): string {
    return JSON.stringify(this.collectFormDefinition(), null, 2);
  }

  private currentSnapshot(): string {
    return JSON.stringify({
      form: this.collectFormDefinition(),
      graph: this.flowCanvas?.toGraphDefinition() ?? null
    });
  }

  // ---- mutators ---------------------------------------------------------
  addField(): void {
    this.fields.push(this.buildFieldGroup(null));
  }

  removeField(index: number): void {
    this.fields.removeAt(index);
  }

  moveUp(index: number): void {
    if (index > 0) {
      const g = this.fields.at(index);
      this.fields.removeAt(index);
      this.fields.insert(index - 1, g);
    }
  }

  moveDown(index: number): void {
    if (index < this.fields.length - 1) {
      const g = this.fields.at(index);
      this.fields.removeAt(index);
      this.fields.insert(index + 1, g);
    }
  }

  addOption(field: FormGroup): void {
    this.options(field).push(this.buildOptionGroup(null));
  }

  removeOption(field: FormGroup, index: number): void {
    this.options(field).removeAt(index);
  }

  // ---- save + publish ---------------------------------------------------
  /** Persists the current editor state as DRAFT and refreshes the save bookkeeping. */
  private persistDraft$(): Observable<AntragsTypVersion> {
    const formDefinition = this.collectFormDefinition();
    const graph = this.flowCanvas?.toGraphDefinition() ?? null;

    const save$ = this.draftVersionId
      ? this.service.editDraft(this.draftVersionId, formDefinition, null, graph)
      : this.service.createDraftVersion(this.antragstypId, formDefinition, null, graph);

    return save$.pipe(tap(v => {
      this.savedMajor = v.major;
      this.draftVersionId = v.id;
      this.lastSavedSnapshot = this.currentSnapshot();
      this.publishDone = false;
    }));
  }

  save(): void {
    if (!this.canSave) {
      return;
    }
    this.saving = true;
    this.errorKey = '';
    this.errorDetail = '';
    this.savedMajor = null;

    this.persistDraft$().subscribe({
      next: () => {
        this.saving = false;
        this.cdr.markForCheck();
      },
      error: (e) => {
        this.saving = false;
        this.applyHttpError(e, { 422: 'designer.error.incompatible', 409: 'designer.error.conflict' });
        this.cdr.markForCheck();
      }
    });
  }

  publish(): void {
    if (!this.draftVersionId) {
      return; // button is disabled; the template shows a translated hint instead
    }
    this.errorKey = '';
    this.errorDetail = '';
    if (this.isDirty && !this.canSave) {
      // Unsaved changes that cannot be auto-saved (invalid editor state).
      this.errorKey = 'designer.error.saveBeforePublish';
      return;
    }
    this.publishing = true;
    // Publish always targets the last saved server state — auto-save dirty edits first
    // so the user never publishes an older draft than what the editor shows.
    const ensureSaved$: Observable<unknown> = this.isDirty ? this.persistDraft$() : of(null);
    ensureSaved$.pipe(
      switchMap(() => this.service.publish(this.draftVersionId!))
    ).subscribe({
      next: (v) => {
        this.publishing = false;
        this.publishDone = true;
        this.publishedKey = v.processDefinitionKey ?? null;
        this.cdr.markForCheck();
      },
      error: (e) => {
        this.publishing = false;
        // 422 here is the publish gate (e.g. "i18n incomplete (BDR-005…)") — the
        // ProblemDetail.detail is passed through verbatim by applyHttpError.
        this.applyHttpError(e, { 422: 'designer.error.publishRejected', 409: 'designer.error.conflict' });
        this.cdr.markForCheck();
      }
    });
  }

  /** Maps an HTTP error to an i18n key and passes a ProblemDetail.detail through verbatim. */
  private applyHttpError(e: unknown, byStatus: Record<number, string>): void {
    const err = e as { status?: number; error?: { detail?: unknown; message?: unknown } };
    this.errorKey = (err?.status != null && byStatus[err.status]) || 'designer.error.generic';
    const detail = err?.error?.detail ?? err?.error?.message;
    this.errorDetail = typeof detail === 'string' ? detail : '';
  }

  // ---- (de)serialization ------------------------------------------------
  private collectFormDefinition(): FormDefinition {
    return {
      fields: this.fields.controls.map(c => {
        const g = c as FormGroup;
        const type = this.fieldType(g);
        const field: FormFieldDef = {
          key: (g.get('key')!.value as string).trim(),
          type,
          required: g.get('required')!.value as boolean
        };
        const label = this.cleanLabel(this.labelGroup(g).value as Record<Lang, string>);
        if (label) {
          field.label = label;
        }
        const validation: Validation = {};
        if (type === 'TEXT' && g.get('maxLength')!.value != null) {
          validation.maxLength = g.get('maxLength')!.value as number;
        }
        if (type === 'NUMBER') {
          if (g.get('min')!.value != null) {
            validation.min = g.get('min')!.value as number;
          }
          if (g.get('max')!.value != null) {
            validation.max = g.get('max')!.value as number;
          }
        }
        if (Object.keys(validation).length) {
          field.validation = validation;
        }
        if (type === 'SELECT' || type === 'MULTI_SELECT') {
          field.options = this.options(g).controls.map(oc => {
            const og = oc as FormGroup;
            const opt: OptionDef = { value: (og.get('value')!.value as string).trim() };
            const label = this.cleanLabel(this.optionLabel(og).value as Record<Lang, string>);
            if (label) {
              opt.label = label;
            }
            return opt;
          });
        }
        return field;
      })
    };
  }

  private buildFieldGroup(def: FormFieldDef | null): FormGroup {
    return this.fb.group({
      key: this.fb.control(def?.key ?? '', { nonNullable: true, validators: [Validators.required] }),
      type: this.fb.control<FieldType>(def?.type ?? 'TEXT', { nonNullable: true }),
      required: this.fb.control(def?.required ?? false, { nonNullable: true }),
      label: this.buildLabelGroup(def?.label),
      maxLength: this.fb.control<number | null>(def?.validation?.maxLength ?? null),
      min: this.fb.control<number | null>(def?.validation?.min ?? null),
      max: this.fb.control<number | null>(def?.validation?.max ?? null),
      options: this.fb.array((def?.options ?? []).map(o => this.buildOptionGroup(o)))
    });
  }

  private buildOptionGroup(def: OptionDef | null): FormGroup {
    return this.fb.group({
      value: this.fb.control(def?.value ?? '', { nonNullable: true }),
      label: this.buildLabelGroup(def?.label)
    });
  }

  private buildLabelGroup(map?: LocaleMap): FormGroup {
    const group: Record<string, unknown> = {};
    for (const l of LANGS) {
      group[l] = this.fb.control(map?.[l] ?? '', { nonNullable: true });
    }
    return this.fb.group(group);
  }

  private cleanLabel(map: Record<Lang, string>): LocaleMap | undefined {
    const out: LocaleMap = {};
    for (const l of LANGS) {
      const v = (map[l] ?? '').trim();
      if (v) {
        out[l] = v;
      }
    }
    return Object.keys(out).length ? out : undefined;
  }

  private resolveLabel(a?: AntragsTypSummary): string {
    if (!a) {
      return '';
    }
    return resolveLocaleText(a.title, this.translate.getCurrentLang() || 'de', a.key);
  }
}
