import { Component, OnInit, inject } from '@angular/core';
import { UpperCasePipe } from '@angular/common';
import { FormArray, FormBuilder, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { TranslateModule } from '@ngx-translate/core';
import { forkJoin, of } from 'rxjs';
import { catchError } from 'rxjs/operators';

import { AntragsTypService } from '../antragstyp/antragstyp.service';
import { AntragsTypSummary } from '../antragstyp/antragstyp.model';
import { AntragsTypVersion } from '../antragstyp/antragstyp-version.model';
import {
  FieldType, FIELD_TYPES, Lang, LANGS, LocaleMap,
  FormDefinition, FormFieldDef, OptionDef, Validation
} from './form-definition.model';

@Component({
  selector: 'app-form-designer',
  standalone: true,
  imports: [ReactiveFormsModule, TranslateModule, RouterLink, UpperCasePipe],
  templateUrl: './form-designer.component.html',
  styleUrl: './form-designer.component.scss'
})
export class FormDesignerComponent implements OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly service = inject(AntragsTypService);
  private readonly fb = inject(FormBuilder);

  readonly langs = LANGS;
  readonly fieldTypes = FIELD_TYPES;

  antragstypId = '';
  antragsTyp?: AntragsTypSummary;
  antragsTypLabel = '';

  readonly fields = this.fb.array<FormGroup>([]);

  loading = true;
  saving = false;
  savedMajor: number | null = null;
  errorMsg = '';

  ngOnInit(): void {
    this.antragstypId = this.route.snapshot.paramMap.get('id') ?? '';
    forkJoin({
      antragsTyp: this.service.getById(this.antragstypId).pipe(catchError(() => of(undefined))),
      versions: this.service.listVersions(this.antragstypId).pipe(catchError(() => of([] as AntragsTypVersion[])))
    }).subscribe(({ antragsTyp, versions }) => {
      this.antragsTyp = antragsTyp;
      this.antragsTypLabel = this.resolveLabel(antragsTyp);
      const defs = versions[0]?.formDefinition?.fields ?? [];
      for (const f of (defs.length ? defs : [null])) {
        this.fields.push(this.buildFieldGroup(f));
      }
      this.loading = false;
    });
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

  get previewJson(): string {
    return JSON.stringify(this.toFormDefinition(), null, 2);
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

  // ---- save -------------------------------------------------------------
  save(): void {
    if (!this.canSave) {
      return;
    }
    this.saving = true;
    this.errorMsg = '';
    this.savedMajor = null;
    this.service.createDraftVersion(this.antragstypId, this.toFormDefinition()).subscribe({
      next: version => {
        this.saving = false;
        this.savedMajor = version.major;
      },
      error: (err: { status?: number }) => {
        this.saving = false;
        this.errorMsg = err?.status ? `HTTP ${err.status}` : '';
      }
    });
  }

  // ---- (de)serialization ------------------------------------------------
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

  private toFormDefinition(): FormDefinition {
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
    const lang = (document.documentElement.lang || 'de') as Lang;
    return a.title?.[lang] ?? a.title?.['de'] ?? a.key;
  }
}
