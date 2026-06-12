import { LocaleMap } from './form-definition.model';

export type NodeType = 'START' | 'FORM' | 'APPROVAL' | 'ACTION' | 'XOR' | 'AND' | 'END';
export const NODE_TYPES: readonly NodeType[] = ['START', 'FORM', 'APPROVAL', 'ACTION', 'XOR', 'AND', 'END'];

/** Node key/title carriers (START/END carry no data). */
export interface NodeData {
  key?: string;
  title?: LocaleMap;
  assigneeRole?: string;                 // APPROVAL
  ref?: string;                          // ACTION
  inputMapping?: Record<string, string>; // ACTION
}

export interface GraphNode {
  id: string;
  type: NodeType;
  position: { x: number; y: number };
  data: NodeData;
}
export interface GraphEdge {
  id: string;
  source: string;
  target: string;
  sourceHandle?: string;
  label?: string;
  condition?: string;     // XOR outgoing edges only
}
export interface GraphDefinition {
  nodes: GraphNode[];
  edges: GraphEdge[];
}

/** Mirrors backend BpmnCompiler key constraint (becomes BPMN id + JUEL var in SP1). */
export const NODE_KEY_PATTERN = /^[A-Za-z][A-Za-z0-9_]*$/;

/**
 * Mirrors the backend GraphValidator CONDITION_PATTERN (SP1): XOR edge conditions are a
 * closed language — `variable == 'value'` or `!=` — never free-form JUEL (injection channel).
 */
export const EDGE_CONDITION_PATTERN =
  /^\s*[A-Za-z][A-Za-z0-9_]*\s*(==|!=|>=|<=|>|<)\s*('[A-Za-z0-9_ .\-äöüÄÖÜéèêàçÉÈÀ]*'|\d+(\.\d+)?)\s*$/;
export const EDGE_ORDERING_OPERATORS = ['>', '>=', '<', '<='];

/**
 * APPROVAL assignee groups offered in the designer (ADR-016): the three tenant-scoped
 * approver groups (VG / HR-BP / HAL) plus the two pre-existing roles. Values become
 * Flowable candidateGroups via the backend graph compiler; labels live under the
 * i18n keys `flow.canvas.role.<value>`. Unknown values from old graphs are surfaced
 * as an extra "(current: …)" option instead of being silently rewritten.
 */
export const ASSIGNEE_ROLES: readonly string[] = [
  'approver-vg',
  'approver-hr-bp',
  'approver-hal',
  'hr-reviewer',
  'tenant-admin',
];

/** Default role of a newly added APPROVAL node (ADR-016 §4). */
export const DEFAULT_ASSIGNEE_ROLE = 'approver-vg';

/**
 * Canvas validation warning. `messageKey` is an i18n key (BDR-005 — no hardcoded UI
 * language in logic); `params` feeds ngx-translate interpolation (e.g. the offending key).
 */
export interface GraphWarning {
  code: string;
  nodeId?: string;
  messageKey: string;
  params?: Record<string, string>;
}
