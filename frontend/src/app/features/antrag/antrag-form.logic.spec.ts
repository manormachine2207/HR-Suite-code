import { describe, it, expect } from 'vitest';
import { FormControl } from '@angular/forms';

import { initialValueFor, validatorsFor } from './antrag-form.logic';
import { FormFieldDef } from '../form-designer/form-definition.model';

function controlFor(def: FormFieldDef, value: unknown): FormControl {
  return new FormControl(value, validatorsFor(def));
}

describe('validatorsFor', () => {
  it('required TEXT field rejects empty and accepts non-empty values', () => {
    const def: FormFieldDef = { key: 'name', type: 'TEXT', required: true };
    expect(controlFor(def, '').invalid).toBe(true);
    expect(controlFor(def, 'x').valid).toBe(true);
  });

  it('required BOOLEAN field stays valid either way (checkbox semantics)', () => {
    const def: FormFieldDef = { key: 'ok', type: 'BOOLEAN', required: true };
    expect(controlFor(def, false).valid).toBe(true);
    expect(controlFor(def, true).valid).toBe(true);
  });

  it('required MULTI_SELECT rejects an empty selection', () => {
    const def: FormFieldDef = { key: 'tags', type: 'MULTI_SELECT', required: true };
    expect(controlFor(def, []).invalid).toBe(true);
    expect(controlFor(def, ['a']).valid).toBe(true);
  });

  it('TEXT maxLength from the formDefinition is enforced', () => {
    const def: FormFieldDef = {
      key: 'kommentar', type: 'TEXT', required: false, validation: { maxLength: 3 }
    };
    expect(controlFor(def, 'abcd').hasError('maxlength')).toBe(true);
    expect(controlFor(def, 'abc').valid).toBe(true);
  });

  it('NUMBER min/max from the formDefinition are enforced', () => {
    const def: FormFieldDef = {
      key: 'tage', type: 'NUMBER', required: false, validation: { min: 1, max: 5 }
    };
    expect(controlFor(def, 0).hasError('min')).toBe(true);
    expect(controlFor(def, 6).hasError('max')).toBe(true);
    expect(controlFor(def, 3).valid).toBe(true);
  });

  it('NUMBER min: 0 is applied (falsy boundary is not dropped)', () => {
    const def: FormFieldDef = {
      key: 'n', type: 'NUMBER', required: false, validation: { min: 0 }
    };
    expect(controlFor(def, -1).hasError('min')).toBe(true);
    expect(controlFor(def, 0).valid).toBe(true);
  });

  it('maxLength is ignored for non-TEXT fields, min/max for non-NUMBER fields', () => {
    const num: FormFieldDef = {
      key: 'n', type: 'NUMBER', required: false, validation: { maxLength: 1 }
    };
    expect(controlFor(num, 1234).valid).toBe(true);
    const txt: FormFieldDef = {
      key: 't', type: 'TEXT', required: false, validation: { min: 5 }
    };
    expect(controlFor(txt, 'ab').valid).toBe(true);
  });
});

describe('initialValueFor', () => {
  it('uses type-appropriate initial values', () => {
    expect(initialValueFor({ key: 'b', type: 'BOOLEAN', required: false })).toBe(false);
    expect(initialValueFor({ key: 'm', type: 'MULTI_SELECT', required: false })).toEqual([]);
    expect(initialValueFor({ key: 't', type: 'TEXT', required: false })).toBe('');
    expect(initialValueFor({ key: 'n', type: 'NUMBER', required: false })).toBe('');
  });
});
