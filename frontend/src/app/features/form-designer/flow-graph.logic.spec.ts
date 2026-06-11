import { describe, it, expect } from 'vitest';
import { GraphDefinition } from './flow-graph.model';
import { emptyGraph, addNode, connect, validateGraph, cloneGraph, removeNode } from './flow-graph.logic';

describe('flow-graph.logic', () => {
  it('emptyGraph has no nodes/edges', () => {
    expect(emptyGraph()).toEqual({ nodes: [], edges: [] });
  });

  it('addNode appends a typed node with an id and position', () => {
    let g = emptyGraph();
    g = addNode(g, 'START', { x: 0, y: 0 });
    g = addNode(g, 'ACTION', { x: 100, y: 0 });
    expect(g.nodes).toHaveLength(2);
    expect(g.nodes[0].type).toBe('START');
    expect(g.nodes[1].type).toBe('ACTION');
    expect(new Set(g.nodes.map(n => n.id)).size).toBe(2);   // unique ids
    expect(g.nodes[1].position).toEqual({ x: 100, y: 0 });
  });

  it('connect adds an edge between two existing nodes', () => {
    let g = emptyGraph();
    g = addNode(g, 'START', { x: 0, y: 0 });
    g = addNode(g, 'END', { x: 100, y: 0 });
    const [a, b] = g.nodes;
    g = connect(g, a.id, b.id);
    expect(g.edges).toHaveLength(1);
    expect(g.edges[0]).toMatchObject({ source: a.id, target: b.id });
  });

  it('cloneGraph is a deep copy (mutating clone leaves original intact)', () => {
    let g = addNode(emptyGraph(), 'FORM', { x: 0, y: 0 });
    const c = cloneGraph(g);
    c.nodes[0].data.key = 'changed';
    expect(g.nodes[0].data.key).toBeUndefined();
  });

  it('validateGraph flags missing START, missing END, invalid key, duplicate key, disconnected node, XOR without conditions', () => {
    const g: GraphDefinition = {
      nodes: [
        { id: 'a', type: 'FORM', position: { x: 0, y: 0 }, data: { key: 'bad-key' } },     // invalid key (hyphen)
        { id: 'b', type: 'FORM', position: { x: 0, y: 0 }, data: { key: 'bad-key' } },     // duplicate key
        { id: 'x', type: 'XOR', position: { x: 0, y: 0 }, data: { key: 'gw' } },           // XOR
        { id: 'd', type: 'ACTION', position: { x: 0, y: 0 }, data: { key: 'd', ref: 'r' } }, // disconnected
      ],
      edges: [
        { id: 'e1', source: 'a', target: 'b' },
        { id: 'e2', source: 'b', target: 'x' },
        { id: 'e3', source: 'x', target: 'a' },   // XOR outgoing WITHOUT condition
      ],
    };
    const warnings = validateGraph(g);
    const codes = warnings.map(w => w.code);
    expect(codes).toContain('NO_START');
    expect(codes).toContain('NO_END');
    expect(codes).toContain('INVALID_KEY');
    expect(codes).toContain('DUPLICATE_KEY');
    expect(codes).toContain('DISCONNECTED');
    expect(codes).toContain('XOR_UNCONDITIONED');
  });

  it('validateGraph emits i18n message keys + params instead of hardcoded German (BDR-005)', () => {
    const g: GraphDefinition = {
      nodes: [
        { id: 'a', type: 'FORM', position: { x: 0, y: 0 }, data: { key: 'bad-key' } },
        { id: 'b', type: 'FORM', position: { x: 0, y: 0 }, data: { key: 'bad-key' } },
        { id: 'x', type: 'XOR', position: { x: 0, y: 0 }, data: { key: 'gw' } },
        { id: 'd', type: 'ACTION', position: { x: 0, y: 0 }, data: { key: 'd', ref: 'r' } },
      ],
      edges: [
        { id: 'e1', source: 'a', target: 'b' },
        { id: 'e2', source: 'b', target: 'x' },
        { id: 'e3', source: 'x', target: 'a' },
      ],
    };
    const byCode = (code: string) => validateGraph(g).find(w => w.code === code)!;

    expect(byCode('NO_START').messageKey).toBe('flow.canvas.warning.noStart');
    expect(byCode('NO_END').messageKey).toBe('flow.canvas.warning.noEnd');
    expect(byCode('INVALID_KEY').messageKey).toBe('flow.canvas.warning.invalidKey');
    expect(byCode('INVALID_KEY').params).toEqual({ key: 'bad-key' });
    expect(byCode('DUPLICATE_KEY').messageKey).toBe('flow.canvas.warning.duplicateKey');
    expect(byCode('DUPLICATE_KEY').params).toEqual({ key: 'bad-key' });
    expect(byCode('DISCONNECTED').messageKey).toBe('flow.canvas.warning.disconnected');
    expect(byCode('XOR_UNCONDITIONED').messageKey).toBe('flow.canvas.warning.xorUnconditioned');

    // No warning carries a hardcoded (German) message anymore.
    for (const w of validateGraph(g)) {
      expect(w).not.toHaveProperty('message');
    }
  });

  it('validateGraph returns empty for a valid linear START->ACTION->END', () => {
    let g = emptyGraph();
    g = addNode(g, 'START', { x: 0, y: 0 });
    g = addNode(g, 'ACTION', { x: 100, y: 0 });
    g = addNode(g, 'END', { x: 200, y: 0 });
    const [s, a, e] = g.nodes;
    a.data = { key: 'prov', ref: 'r', inputMapping: {} };
    g = connect(g, s.id, a.id);
    g = connect(g, a.id, e.id);
    expect(validateGraph(g)).toEqual([]);
  });

  it('removeNode removes the node and its dangling edges, leaving the input intact', () => {
    let g = emptyGraph();
    g = addNode(g, 'START', { x: 0, y: 0 });
    g = addNode(g, 'END', { x: 100, y: 0 });
    const [s, e] = g.nodes;
    g = connect(g, s.id, e.id);
    const before = g;                       // capture reference to assert no mutation
    const after = removeNode(g, s.id);
    expect(after.nodes).toHaveLength(1);
    expect(after.nodes[0].id).toBe(e.id);
    expect(after.edges).toHaveLength(0);    // dangling edge removed
    expect(before.nodes).toHaveLength(2);   // input not mutated
    expect(before.edges).toHaveLength(1);
  });

  it('addNode does not mutate the input graph', () => {
    const g0 = emptyGraph();
    const g1 = addNode(g0, 'ACTION', { x: 0, y: 0 });
    expect(g0.nodes).toHaveLength(0);   // original untouched
    expect(g1.nodes).toHaveLength(1);
  });
});
