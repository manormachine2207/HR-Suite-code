import {
  GraphDefinition, GraphNode, GraphWarning, NodeType, NODE_KEY_PATTERN,
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

const KEY_TYPES: NodeType[] = ['FORM', 'APPROVAL', 'ACTION', 'XOR', 'AND'];

export function validateGraph(g: GraphDefinition): GraphWarning[] {
  const w: GraphWarning[] = [];
  const byType = (t: NodeType) => g.nodes.filter(n => n.type === t);

  if (byType('START').length === 0) w.push({ code: 'NO_START', message: 'Kein START-Knoten.' });
  if (byType('END').length === 0) w.push({ code: 'NO_END', message: 'Kein END-Knoten.' });

  // keys: required + pattern + unique (only for key-bearing types)
  const keyed = g.nodes.filter(n => KEY_TYPES.includes(n.type));
  const seen = new Map<string, number>();
  for (const n of keyed) {
    const key = n.data.key ?? '';
    if (!NODE_KEY_PATTERN.test(key)) {
      w.push({ code: 'INVALID_KEY', nodeId: n.id, message: `Ungültiger key "${key}".` });
    }
    seen.set(key, (seen.get(key) ?? 0) + 1);
  }
  for (const [key, count] of seen) {
    if (key && count > 1) w.push({ code: 'DUPLICATE_KEY', message: `Doppelter key "${key}".` });
  }

  // disconnected: a non-START node with no incoming AND no outgoing edge
  for (const n of g.nodes) {
    const touched = g.edges.some(e => e.source === n.id || e.target === n.id);
    if (!touched && n.type !== 'START') {
      w.push({ code: 'DISCONNECTED', nodeId: n.id, message: 'Knoten ist nicht verbunden.' });
    }
  }

  // XOR outgoing edges must all carry a condition
  for (const x of byType('XOR')) {
    const out = g.edges.filter(e => e.source === x.id);
    if (out.length === 0 || out.some(e => !e.condition || !e.condition.trim())) {
      w.push({ code: 'XOR_UNCONDITIONED', nodeId: x.id, message: 'XOR-Ausgang ohne Bedingung.' });
    }
  }

  return w;
}
