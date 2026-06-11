import {
  GraphDefinition, GraphEdge, GraphNode, GraphWarning, NodeType,
  NODE_KEY_PATTERN, EDGE_CONDITION_PATTERN,
} from './flow-graph.model';

let _seq = 0;
function uid(prefix: string): string {
  _seq += 1;
  return `${prefix}_${Date.now().toString(36)}_${_seq}`;
}

export function emptyGraph(): GraphDefinition {
  return { nodes: [], edges: [] };
}

export function cloneGraph(g: GraphDefinition): GraphDefinition {
  return JSON.parse(JSON.stringify(g)) as GraphDefinition;
}

export function addNode(g: GraphDefinition, type: NodeType, position: { x: number; y: number }): GraphDefinition {
  const node: GraphNode = { id: uid('n'), type, position, data: {} };
  return { ...g, nodes: [...g.nodes, node] };
}

export function removeNode(g: GraphDefinition, nodeId: string): GraphDefinition {
  return {
    nodes: g.nodes.filter(n => n.id !== nodeId),
    edges: g.edges.filter(e => e.source !== nodeId && e.target !== nodeId),
  };
}

export function connect(g: GraphDefinition, source: string, target: string, sourceHandle?: string): GraphDefinition {
  const edge = { id: uid('e'), source, target, sourceHandle };
  return { ...g, edges: [...g.edges, edge] };
}

/** Patches label/condition of one edge immutably (SP1 edge inspector). */
export function updateEdge(
  g: GraphDefinition,
  edgeId: string,
  patch: Pick<Partial<GraphEdge>, 'label' | 'condition'>,
): GraphDefinition {
  return {
    ...g,
    edges: g.edges.map(e => (e.id === edgeId ? { ...e, ...patch } : e)),
  };
}

/** Removes one edge immutably (SP1: a mis-drawn edge no longer requires deleting a node). */
export function removeEdge(g: GraphDefinition, edgeId: string): GraphDefinition {
  return { ...g, edges: g.edges.filter(e => e.id !== edgeId) };
}

const KEY_TYPES: NodeType[] = ['FORM', 'APPROVAL', 'ACTION', 'XOR', 'AND'];

export function validateGraph(g: GraphDefinition): GraphWarning[] {
  const w: GraphWarning[] = [];
  const byType = (t: NodeType) => g.nodes.filter(n => n.type === t);

  if (byType('START').length === 0) w.push({ code: 'NO_START', messageKey: 'flow.canvas.warning.noStart' });
  if (byType('END').length === 0) w.push({ code: 'NO_END', messageKey: 'flow.canvas.warning.noEnd' });

  // keys: required + pattern + unique (only for key-bearing types)
  const keyed = g.nodes.filter(n => KEY_TYPES.includes(n.type));
  const seen = new Map<string, number>();
  for (const n of keyed) {
    const key = n.data.key ?? '';
    if (!NODE_KEY_PATTERN.test(key)) {
      w.push({ code: 'INVALID_KEY', nodeId: n.id, messageKey: 'flow.canvas.warning.invalidKey', params: { key } });
    }
    seen.set(key, (seen.get(key) ?? 0) + 1);
  }
  for (const [key, count] of seen) {
    if (key && count > 1) w.push({ code: 'DUPLICATE_KEY', messageKey: 'flow.canvas.warning.duplicateKey', params: { key } });
  }

  // disconnected: a non-START node with no incoming AND no outgoing edge
  for (const n of g.nodes) {
    const touched = g.edges.some(e => e.source === n.id || e.target === n.id);
    if (!touched && n.type !== 'START') {
      w.push({ code: 'DISCONNECTED', nodeId: n.id, messageKey: 'flow.canvas.warning.disconnected' });
    }
  }

  // XOR semantics (aligned with the backend GraphValidator, SP1): at most ONE
  // outgoing edge without a condition (= default branch); conditions follow the
  // closed syntax `var == 'value'` — never free-form JUEL.
  for (const x of byType('XOR')) {
    const out = g.edges.filter(e => e.source === x.id);
    const unconditioned = out.filter(e => !e.condition || !e.condition.trim());
    if (out.length === 0 || (out.length > 1 && unconditioned.length > 1)) {
      w.push({ code: 'XOR_UNCONDITIONED', nodeId: x.id, messageKey: 'flow.canvas.warning.xorUnconditioned' });
    }
  }
  for (const e of g.edges) {
    const condition = e.condition?.trim();
    if (condition && !EDGE_CONDITION_PATTERN.test(condition)) {
      w.push({
        code: 'XOR_BAD_CONDITION',
        messageKey: 'flow.canvas.warning.xorBadCondition',
        params: { condition },
      });
    }
  }

  return w;
}
