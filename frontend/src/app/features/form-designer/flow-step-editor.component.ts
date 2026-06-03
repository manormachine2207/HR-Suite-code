import { Component, inject, input } from '@angular/core';
import { AbstractControl, FormArray, FormBuilder, FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { TranslateModule } from '@ngx-translate/core';
import { UpperCasePipe } from '@angular/common';

import { LANGS, Lang, LocaleMap } from './form-definition.model';
import {
  ASSIGNEE_ROLES, ActionStepDef, ApprovalStepDef, FlowDefinition, FlowStepDef,
  FormStepDef, STEP_KEY_PATTERN, StepKind,
} from './flow-definition.model';

/** Typed controls for the title sub-group (one FormControl per lang). */
type TitleControls = { [K in Lang]: FormControl<string | null> };

/** All possible controls in a step group; optional controls are `FormControl | undefined`. */
export interface StepControls {
  kind:          FormControl<string | null>;
  key:           FormControl<string | null>;
  title:         FormGroup<TitleControls>;
  assigneeRole?: FormControl<string | null>;
  ref?:          FormControl<string | null>;
  inputMapping?: FormArray<FormGroup<MappingRowControls>>;
}

/** Typed controls for an input-mapping key/value row. */
export interface MappingRowControls {
  k: FormControl<string | null>;
  v: FormControl<string | null>;
}

export type StepGroup    = FormGroup<StepControls>;
export type MappingGroup = FormGroup<MappingRowControls>;

@Component({
  selector: 'app-flow-step-editor',
  standalone: true,
  imports: [ReactiveFormsModule, TranslateModule, UpperCasePipe],
  templateUrl: './flow-step-editor.component.html',
  styleUrl: './flow-step-editor.component.scss',
})
export class FlowStepEditorComponent {
  private readonly fb = inject(FormBuilder);

  /** Action refs allow-listed for the tenant (from GET /action/refs). */
  readonly availableRefs = input<string[]>([]);
  readonly langs = LANGS;
  readonly assigneeRoles = ASSIGNEE_ROLES;

  readonly steps = new FormArray<StepGroup>([]);

  // ---- structure --------------------------------------------------------
  addStep(kind: StepKind): void {
    this.steps.push(this.buildStep(kind));
  }

  removeStep(i: number): void {
    this.steps.removeAt(i);
  }

  moveUp(i: number): void {
    if (i <= 0) return;
    const g = this.steps.at(i);
    this.steps.removeAt(i);
    this.steps.insert(i - 1, g);
  }

  moveDown(i: number): void {
    if (i >= this.steps.length - 1) return;
    const g = this.steps.at(i);
    this.steps.removeAt(i);
    this.steps.insert(i + 1, g);
  }

  // ---- ACTION inputMapping rows ----------------------------------------
  inputMapping(i: number): FormArray<MappingGroup> {
    return this.steps.at(i).controls.inputMapping as FormArray<MappingGroup>;
  }

  addInputMappingRow(i: number): void {
    this.inputMapping(i).push(
      this.fb.group({ k: this.fb.control(''), v: this.fb.control('') }) as MappingGroup,
    );
  }

  removeInputMappingRow(i: number, r: number): void {
    this.inputMapping(i).removeAt(r);
  }

  // ---- accessors used by the template ----------------------------------
  titleGroup(g: StepGroup): FormGroup<TitleControls> {
    return g.controls.title;
  }

  kindOf(g: StepGroup): StepKind {
    return g.controls.kind.value as StepKind;
  }

  // ---- (de)serialisation ------------------------------------------------
  /** Returns null when there are no steps (form-only antragstyp -> omit flowDefinition). */
  toFlowDefinition(): FlowDefinition | null {
    if (this.steps.length === 0) return null;
    const steps: FlowStepDef[] = this.steps.controls.map(g => this.toStep(g));
    return { steps };
  }

  loadFlow(flow: FlowDefinition | null | undefined): void {
    this.steps.clear();
    for (const s of flow?.steps ?? []) {
      if (s.kind === 'FORM' || s.kind === 'APPROVAL' || s.kind === 'ACTION') {
        this.steps.push(this.buildStep(s.kind, s));
      }
      // unknown kinds (e.g. BRANCH) are dropped — not editable in Cut C
    }
  }

  /** True if every step key matches the pattern and is unique. */
  isValid(): boolean {
    const keys = this.steps.controls.map(g => g.controls.key.value);
    const unique = new Set(keys).size === keys.length;
    return unique && this.steps.controls.every(g =>
      g.controls.key.valid &&
      (this.kindOf(g) !== 'ACTION' || !!(g.controls.ref?.value))
    );
  }

  // ---- internals --------------------------------------------------------
  private buildStep(kind: StepKind, existing?: FlowStepDef): StepGroup {
    const titleControls = Object.fromEntries(
      LANGS.map(l => [l, this.fb.control(
        (existing?.title as LocaleMap | undefined)?.[l] ?? ''
      )])
    ) as { [K in Lang]: FormControl<string | null> };

    const controls: Record<string, AbstractControl> = {
      kind:  this.fb.control(kind),
      key:   this.fb.control(existing?.key ?? '',
               [Validators.required, Validators.pattern(STEP_KEY_PATTERN)]),
      title: this.fb.group(titleControls) as FormGroup<TitleControls>,
    };

    if (kind === 'APPROVAL') {
      const a = existing as ApprovalStepDef | undefined;
      controls['assigneeRole'] = this.fb.control(a?.assigneeRole ?? 'hr-reviewer', Validators.required);
    }
    if (kind === 'ACTION') {
      const a = existing as ActionStepDef | undefined;
      controls['ref'] = this.fb.control(a?.ref ?? '', Validators.required);
      const rows: MappingGroup[] = (a?.inputMapping ? Object.entries(a.inputMapping) : [])
        .map(([k, v]) =>
          this.fb.group({ k: this.fb.control(k), v: this.fb.control(v) }) as MappingGroup
        );
      controls['inputMapping'] = this.fb.array(rows) as FormArray<MappingGroup>;
    }

    return new FormGroup(controls) as unknown as StepGroup;
  }

  private toStep(g: StepGroup): FlowStepDef {
    const kind   = this.kindOf(g);
    const key    = g.controls.key.value as string;
    const title  = this.compactTitle(g.controls.title);

    if (kind === 'FORM') {
      return { kind, key, title } as FormStepDef;
    }
    if (kind === 'APPROVAL') {
      return {
        kind, key, title,
        assigneeRole: g.controls.assigneeRole!.value as string,
        outcomes: ['approve', 'reject'],
      } as ApprovalStepDef;
    }
    // ACTION
    const inputMapping: Record<string, string> = {};
    for (const row of (g.controls.inputMapping as FormArray<MappingGroup>).controls) {
      const k = row.controls.k.value as string;
      const v = row.controls.v.value as string;
      if (k && k.trim()) inputMapping[k] = v ?? '';
    }
    return {
      kind, key, title,
      ref: g.controls.ref!.value as string,
      inputMapping,
    } as ActionStepDef;
  }

  private compactTitle(group: FormGroup<TitleControls>): LocaleMap {
    const out: LocaleMap = {};
    for (const l of LANGS) {
      const v = group.controls[l].value as string | null;
      if (v && v.trim()) out[l as Lang] = v;
    }
    return out;
  }
}
