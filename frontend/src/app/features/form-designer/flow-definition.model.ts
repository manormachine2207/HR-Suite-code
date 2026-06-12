import { LocaleMap } from './form-definition.model';

/** Step kinds the Cut C editor supports (BRANCH is intentionally excluded — not yet compilable). */
export type StepKind = 'FORM' | 'APPROVAL' | 'ACTION';
export const STEP_KINDS: readonly StepKind[] = ['FORM', 'APPROVAL', 'ACTION'];

export interface FormStepDef {
  kind: 'FORM';
  key: string;
  title: LocaleMap;
}

export interface ApprovalStepDef {
  kind: 'APPROVAL';
  key: string;
  title: LocaleMap;
  assigneeRole: string;
  outcomes: ['approve', 'reject'];
}

export interface ActionStepDef {
  kind: 'ACTION';
  key: string;
  title: LocaleMap;
  ref: string;
  inputMapping: Record<string, string>;
}

export type FlowStepDef = FormStepDef | ApprovalStepDef | ActionStepDef;

export interface FlowDefinition {
  steps: FlowStepDef[];
}

/** Step key must be a BPMN id + JUEL variable name (mirrors backend BpmnCompiler.KEY_PATTERN). */
export const STEP_KEY_PATTERN = /^[A-Za-z][A-Za-z0-9_]*$/;

/** Assignee roles offerable for APPROVAL steps (kept in sync with flow-graph.model ASSIGNEE_ROLES, ADR-016). */
export const ASSIGNEE_ROLES: readonly string[] = [
  'approver-vg',
  'approver-hr-bp',
  'approver-hal',
  'hr-reviewer',
  'tenant-admin',
];
