/**
 * Frontend mirror of the backend FormDefinition model (`antragstyp/form/`).
 * Provisional schema (Decision-Draft DRAFT-form-definition-schema, ADR-009).
 */

export type Lang = 'de' | 'fr' | 'it' | 'en';
export const LANGS: readonly Lang[] = ['de', 'fr', 'it', 'en'];

export type FieldType = 'TEXT' | 'NUMBER' | 'DATE' | 'BOOLEAN' | 'SELECT' | 'MULTI_SELECT';
export const FIELD_TYPES: readonly FieldType[] = ['TEXT', 'NUMBER', 'DATE', 'BOOLEAN', 'SELECT', 'MULTI_SELECT'];

export type LocaleMap = Partial<Record<Lang, string>>;

export interface Validation {
  maxLength?: number | null;
  min?: number | null;
  max?: number | null;
}

export interface OptionDef {
  value: string;
  label?: LocaleMap;
}

export interface FormFieldDef {
  key: string;
  type: FieldType;
  required: boolean;
  label?: LocaleMap;
  helpText?: LocaleMap;
  validation?: Validation;
  options?: OptionDef[];
  defaultValue?: string;
}

export interface FormDefinition {
  fields: FormFieldDef[];
}

export function fieldTypeHasMaxLength(type: FieldType): boolean {
  return type === 'TEXT';
}

export function fieldTypeHasRange(type: FieldType): boolean {
  return type === 'NUMBER';
}

export function fieldTypeHasOptions(type: FieldType): boolean {
  return type === 'SELECT' || type === 'MULTI_SELECT';
}
