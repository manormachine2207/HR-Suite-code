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
export const ASSIGNEE_ROLES: readonly string[] = ['hr-reviewer', 'tenant-admin'];

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
