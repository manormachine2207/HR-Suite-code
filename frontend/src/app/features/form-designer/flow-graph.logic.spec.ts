import { describe, it, expect } from 'vitest';
import { GraphDefinition, GraphNode } from './flow-graph.model';
import {
  emptyGraph, addNode, connect, validateGraph, cloneGraph, removeNode, updateEdge, removeEdge,
  nextFreePosition, DEFAULT_NODE_SIZE,
} from './flow-graph.logic';

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
        // TWO unconditioned XOR outgoing edges — at most one default branch is allowed
        { id: 'e3', source: 'x', target: 'a' },
        { id: 'e4', source: 'x', target: 'b' },
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
        { id: 'e4', source: 'x', target: 'b' },
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

  // ---- SP1: edge editing + backend-aligned XOR semantics -------------------

  it('updateEdge patches label and condition immutably', () => {
    let g = emptyGraph();
    g = addNode(g, 'XOR', { x: 0, y: 0 });
    g = addNode(g, 'END', { x: 100, y: 0 });
    g = connect(g, g.nodes[0].id, g.nodes[1].id);
    const edgeId = g.edges[0].id;

    const patched = updateEdge(g, edgeId, { label: 'ja', condition: "gw_outcome == 'approve'" });

    expect(patched.edges[0].label).toBe('ja');
    expect(patched.edges[0].condition).toBe("gw_outcome == 'approve'");
    expect(g.edges[0].label).toBeUndefined();   // input untouched
  });

  it('removeEdge drops exactly that edge', () => {
    let g = emptyGraph();
    g = addNode(g, 'FORM', { x: 0, y: 0 });
    g = addNode(g, 'END', { x: 100, y: 0 });
    g = connect(g, g.nodes[0].id, g.nodes[1].id);
    g = connect(g, g.nodes[0].id, g.nodes[1].id);
    const removed = removeEdge(g, g.edges[0].id);
    expect(removed.edges).toHaveLength(1);
    expect(g.edges).toHaveLength(2);   // input untouched
  });

  it('validateGraph accepts a single unconditioned XOR branch as the default (backend semantics)', () => {
    const g: GraphDefinition = {
      nodes: [
        { id: 's', type: 'START', position: { x: 0, y: 0 }, data: {} },
        { id: 'x', type: 'XOR', position: { x: 0, y: 0 }, data: { key: 'gw' } },
        { id: 'e1n', type: 'END', position: { x: 0, y: 0 }, data: {} },
        { id: 'e2n', type: 'END', position: { x: 0, y: 0 }, data: {} },
      ],
      edges: [
        { id: 'a', source: 's', target: 'x' },
        { id: 'b', source: 'x', target: 'e1n', condition: "gw_outcome == 'approve'" },
        { id: 'c', source: 'x', target: 'e2n' },   // exactly one default branch -> ok
      ],
    };
    expect(validateGraph(g)).toEqual([]);
  });

  it('validateGraph flags a condition outside the closed syntax (no free-form JUEL)', () => {
    const g: GraphDefinition = {
      nodes: [
        { id: 's', type: 'START', position: { x: 0, y: 0 }, data: {} },
        { id: 'x', type: 'XOR', position: { x: 0, y: 0 }, data: { key: 'gw' } },
        { id: 'e1n', type: 'END', position: { x: 0, y: 0 }, data: {} },
        { id: 'e2n', type: 'END', position: { x: 0, y: 0 }, data: {} },
      ],
      edges: [
        { id: 'a', source: 's', target: 'x' },
        { id: 'b', source: 'x', target: 'e1n', condition: 'evilBean.run()' },
        { id: 'c', source: 'x', target: 'e2n' },
      ],
    };
    const bad = validateGraph(g).find(w => w.code === 'XOR_BAD_CONDITION');
    expect(bad).toBeDefined();
    expect(bad!.messageKey).toBe('flow.canvas.warning.xorBadCondition');
    expect(bad!.params).toEqual({ condition: 'evilBean.run()' });
  });

  it('numeric edge conditions are valid; ordering with a string literal warns (mirror of backend rule)', () => {
    let g = emptyGraph();
    g = addNode(g, 'START', { x: 0, y: 0 });
    g = addNode(g, 'XOR', { x: 100, y: 0 });
    g = addNode(g, 'END', { x: 200, y: -40 });
    g = addNode(g, 'END', { x: 200, y: 40 });
    const [s, x, e1, e2] = g.nodes;
    x.data = { key: 'kostenpruefung' };
    g = connect(g, s.id, x.id);
    g = connect(g, x.id, e1.id);
    g = connect(g, x.id, e2.id);
    const [, conditioned] = [g.edges[0], g.edges[1]];

    const ok = updateEdge(g, conditioned.id, { condition: "kosten > 5000" });
    expect(validateGraph(ok).filter(w => w.code === 'XOR_BAD_CONDITION')).toEqual([]);

    const bad = updateEdge(g, conditioned.id, { condition: "kosten > 'fuenftausend'" });
    expect(validateGraph(bad).some(w => w.code === 'XOR_BAD_CONDITION')).toBe(true);
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

// ---- SP3: collision-free spawn position ------------------------------------

describe('nextFreePosition', () => {
  const size = DEFAULT_NODE_SIZE;

  /** AABB overlap with all nodes assumed `size` (uniform node cards on the canvas). */
  function overlaps(a: { x: number; y: number }, b: { x: number; y: number }): boolean {
    return a.x < b.x + size.width && b.x < a.x + size.width
        && a.y < b.y + size.height && b.y < a.y + size.height;
  }

  it('returns the start position on an empty canvas', () => {
    expect(nextFreePosition([], size)).toEqual({ x: 80, y: 80 });
  });

  it('places the new node 80px right of the rightmost bounding box, same row', () => {
    const g = addNode(emptyGraph(), 'START', { x: 80, y: 80 });
    expect(nextFreePosition(g.nodes, size)).toEqual({ x: 80 + size.width + 80, y: 80 });
  });

  it('NEVER overlaps an existing bounding box, even on a dense canvas', () => {
    let g = emptyGraph();
    for (let i = 0; i < 12; i++) {
      const p = nextFreePosition(g.nodes, size);
      for (const n of g.nodes) {
        expect(overlaps(p, n.position)).toBe(false);
      }
      g = addNode(g, 'ACTION', p);
    }
  });

  it('staggers vertically into a new row when the row is full (viewportHint)', () => {
    const hint = { maxRowWidth: 600 };
    let g = emptyGraph();
    const placed: { x: number; y: number }[] = [];
    for (let i = 0; i < 4; i++) {
      const p = nextFreePosition(g.nodes, size, hint);
      expect(p.x + size.width).toBeLessThanOrEqual(hint.maxRowWidth);  // row constraint holds
      placed.push(p);
      g = addNode(g, 'FORM', p);
    }
    expect(new Set(placed.map(p => p.y)).size).toBeGreaterThan(1);     // wrapped to a 2nd row
    for (let i = 0; i < placed.length; i++) {
      for (let j = i + 1; j < placed.length; j++) {
        expect(overlaps(placed[i], placed[j])).toBe(false);
      }
    }
  });

  it('skips an occupied slot in the wrapped row (manually dragged node in the way)', () => {
    // Row is full (maxRowWidth 600) AND the first slot of the new row is already taken.
    const nodes: GraphNode[] = [
      { id: 'a', type: 'START', position: { x: 80, y: 80 }, data: {} },
      { id: 'b', type: 'FORM', position: { x: 350, y: 80 }, data: { key: 'f' } },
      { id: 'c', type: 'END', position: { x: 80, y: 184 }, data: {} },   // squats on the wrap target
    ];
    const p = nextFreePosition(nodes, size, { maxRowWidth: 600 });
    expect(p.y).toBeGreaterThan(80);                                   // new row, not row 1
    expect(p.x + size.width).toBeLessThanOrEqual(600);
    for (const n of nodes) {
      expect(overlaps(p, n.position)).toBe(false);
    }
  });

  it('is pure — does not mutate the input nodes', () => {
    const g = addNode(addNode(emptyGraph(), 'START', { x: 80, y: 80 }), 'END', { x: 400, y: 80 });
    const snapshot = JSON.stringify(g.nodes);
    nextFreePosition(g.nodes, size);
    expect(JSON.stringify(g.nodes)).toBe(snapshot);
  });
});
