import { ValidatorFn, Validators } from '@angular/forms';

import { FormFieldDef } from '../form-designer/form-definition.model';

/**
 * Pure mapping from a FormFieldDef (published formDefinition, ADR-009) to Angular
 * validators. Mirrors the backend PayloadValidator rules so the applicant gets inline
 * feedback before the server rejects the payload:
 * - required: value-bearing controls only (a BOOLEAN checkbox is valid either way)
 * - TEXT:   validation.maxLength
 * - NUMBER: validation.min / validation.max (0 is a valid boundary)
 */
export function validatorsFor(def: FormFieldDef): ValidatorFn[] {
  const v: ValidatorFn[] = [];
  if (def.required && def.type !== 'BOOLEAN') {
    v.push(Validators.required);
  }
  if (def.type === 'TEXT' && def.validation?.maxLength != null) {
    v.push(Validators.maxLength(def.validation.maxLength));
  }
  if (def.type === 'NUMBER') {
    if (def.validation?.min != null) {
      v.push(Validators.min(def.validation.min));
    }
    if (def.validation?.max != null) {
      v.push(Validators.max(def.validation.max));
    }
  }
  return v;
}

/** Type-appropriate initial control value for a fresh Antrag form. */
export function initialValueFor(def: FormFieldDef): unknown {
  if (def.type === 'BOOLEAN') {
    return false;
  }
  if (def.type === 'MULTI_SELECT') {
    return [];
  }
  return '';
}
