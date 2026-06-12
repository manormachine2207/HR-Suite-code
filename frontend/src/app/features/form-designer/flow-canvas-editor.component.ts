import { Component, computed, input, signal, viewChild } from '@angular/core';
import { NgTemplateOutlet } from '@angular/common';
import { ReactiveFormsModule } from '@angular/forms';
import { TranslateModule } from '@ngx-translate/core';
import {
  Vflow, VflowComponent, createNodes, createEdges, Node, Edge,
  NodePositionChange, NodeSelectedChange, Background,
} from 'ngx-vflow';

import {
  GraphDefinition,
  GraphNode,
  GraphWarning,
  NodeType,
  NODE_TYPES,
  ASSIGNEE_ROLES,
} from './flow-graph.model';
import {
  addNode,
  connect,
  emptyGraph,
  nextFreePosition,
  removeEdge,
  removeNode,
  updateEdge,
  validateGraph,
  cloneGraph,
  DEFAULT_NODE_SIZE,
} from './flow-graph.logic';
import { GraphEdge } from './flow-graph.model';

/** Data carried into the vflow html-template node (SP3 custom node cards). */
interface VflowNodeData {
  type: NodeType;
  key: string;
}

@Component({
  selector: 'app-flow-canvas-editor',
  standalone: true,
  imports: [...Vflow, NgTemplateOutlet, ReactiveFormsModule, TranslateModule],
  templateUrl: './flow-canvas-editor.component.html',
  styleUrl: './flow-canvas-editor.component.scss',
})
export class FlowCanvasEditorComponent {
  readonly availableRefs = input<string[]>([]);
  readonly nodeTypes: readonly NodeType[] = NODE_TYPES;
  readonly assigneeRoles = ASSIGNEE_ROLES;

  /** vflow instance — only touched from user-event handlers (zoom/fit controls). */
  private readonly vflow = viewChild(VflowComponent);

  readonly graph = signal<GraphDefinition>(emptyGraph());
  readonly selectedNodeId = signal<string | null>(null);
  readonly selectedEdgeId = signal<string | null>(null);
  readonly warnings = computed<GraphWarning[]>(() => validateGraph(this.graph()));

  readonly hasStart = computed<boolean>(() => this.graph().nodes.some(n => n.type === 'START'));

  /**
   * Dotted canvas background (n8n look). Literal colors: this is a component input
   * rendered into SVG attributes by ngx-vflow, where CSS custom properties do not
   * resolve — values mirror the --bs-border/--bs-tertiary-bg fallbacks used in SCSS.
   */
  readonly canvasBackground: Background = {
    type: 'dots',
    gap: 24,
    size: 1.5,
    color: '#c9d2dc',
    backgroundColor: '#fafbfc',
  };

  readonly selectedNode = computed<GraphNode | null>(() => {
    const id = this.selectedNodeId();
    return id ? (this.graph().nodes.find(n => n.id === id) ?? null) : null;
  });

  readonly selectedEdge = computed<GraphEdge | null>(() => {
    const id = this.selectedEdgeId();
    return id ? (this.graph().edges.find(e => e.id === id) ?? null) : null;
  });

  /** Conditions only make sense on XOR outgoing edges (SP1, mirrors backend rule). */
  readonly selectedEdgeSourceIsXor = computed<boolean>(() => {
    const e = this.selectedEdge();
    if (!e) return false;
    return this.graph().nodes.find(n => n.id === e.source)?.type === 'XOR';
  });

  /**
   * vflow-compatible node array, re-derived from domain graph signal.
   * SP3: 'html-template' nodes render the custom card via <ng-template nodeHtml>
   * (icon + title + type subtitle); handles live inside the template.
   */
  readonly vflowNodes = computed<Node[]>(() =>
    createNodes(
      this.graph().nodes.map(domainNode => ({
        id: domainNode.id,
        type: 'html-template' as const,
        point: { x: domainNode.position.x, y: domainNode.position.y },
        width: DEFAULT_NODE_SIZE.width,
        height: DEFAULT_NODE_SIZE.height,
        data: { type: domainNode.type, key: domainNode.data.key ?? '' } satisfies VflowNodeData,
      }))
    )
  );

  /**
   * vflow-compatible edge array, re-derived from domain graph signal.
   * SP3: arrow markers + a default center edge label showing `label` and/or the
   * ƒ marker for a set condition (the inspector edge list stays the editor).
   */
  readonly vflowEdges = computed<Edge[]>(() =>
    createEdges(
      this.graph().edges.map(domainEdge => {
        const labelText = this.edgeLabelText(domainEdge);
        return {
          id: domainEdge.id,
          source: domainEdge.source,
          target: domainEdge.target,
          ...(domainEdge.sourceHandle ? { sourceHandle: domainEdge.sourceHandle } : {}),
          markers: { end: { type: 'arrow-closed' as const, width: 22, height: 22 } },
          ...(labelText
            ? { edgeLabels: { center: { type: 'default' as const, text: labelText } } }
            : {}),
        };
      })
    )
  );

  /** Canvas caption of one edge: its label and/or the ƒ condition marker. */
  private edgeLabelText(e: GraphEdge): string {
    const label = e.label?.trim() ?? '';
    const hasCondition = !!e.condition?.trim();
    if (label && hasCondition) return `${label} · ƒ`;
    if (hasCondition) return 'ƒ';
    return label;
  }

  addNode(type: NodeType): void {
    if (type === 'START' && this.hasStart()) return;
    const position = nextFreePosition(this.graph().nodes, DEFAULT_NODE_SIZE);
    this.graph.set(addNode(this.graph(), type, position));
  }

  removeNode(id: string): void {
    this.graph.set(removeNode(this.graph(), id));
    if (this.selectedNodeId() === id) this.selectedNodeId.set(null);
  }

  /** Fired by [connect] directive on the <vflow> element. */
  onConnect(ev: { source: string; target: string; sourceHandle?: string }): void {
    this.graph.set(connect(this.graph(), ev.source, ev.target, ev.sourceHandle));
  }

  select(id: string): void {
    this.selectedNodeId.set(id);
  }

  /** Fired by changesController (nodesChanges.select) when the user clicks a node on the canvas. */
  onSelectionChange(changes: NodeSelectedChange[]): void {
    const selected = changes.find(c => c.selected);
    this.selectedNodeId.set(selected ? selected.id : null);
    if (selected) this.selectedEdgeId.set(null);
  }

  // ---- canvas viewport controls (SP3) --------------------------------------

  zoomIn(): void {
    const v = this.vflow();
    if (v) v.zoomTo(v.viewport().zoom * 1.2);
  }

  zoomOut(): void {
    const v = this.vflow();
    if (v) v.zoomTo(v.viewport().zoom / 1.2);
  }

  fitView(): void {
    this.vflow()?.fitView({ padding: 0.15, duration: 250 });
  }

  // ---- edge editing (SP1) -------------------------------------------------

  selectEdge(id: string): void {
    this.selectedEdgeId.set(this.selectedEdgeId() === id ? null : id);
    this.selectedNodeId.set(null);
  }

  patchSelectedEdge(patch: Pick<Partial<GraphEdge>, 'label' | 'condition'>): void {
    const id = this.selectedEdgeId();
    if (id) this.graph.set(updateEdge(this.graph(), id, patch));
  }

  removeSelectedEdge(): void {
    const id = this.selectedEdgeId();
    if (!id) return;
    this.graph.set(removeEdge(this.graph(), id));
    this.selectedEdgeId.set(null);
  }

  /** Human-readable edge caption for the inspector list: `key → key` (falls back to type). */
  edgeDisplay(e: GraphEdge): string {
    const name = (id: string) => {
      const n = this.graph().nodes.find(x => x.id === id);
      return n ? (n.data.key ?? n.type) : id;
    };
    return `${name(e.source)} → ${name(e.target)}`;
  }

  /** Persist drag-position changes emitted by changesController (nodesChanges.position). */
  onPositionChange(changes: NodePositionChange[]): void {
    let g = this.graph();
    for (const change of changes) {
      const cloned = cloneGraph(g);
      const n = cloned.nodes.find(x => x.id === change.id);
      if (n) {
        n.position = { x: change.point.x, y: change.point.y };
        g = cloned;
      }
    }
    this.graph.set(g);
  }

  /** Mutates the selected node's data immutably. */
  patchSelected(patch: Partial<GraphNode['data']>): void {
    const id = this.selectedNodeId();
    if (!id) return;
    const g = cloneGraph(this.graph());
    const n = g.nodes.find(x => x.id === id);
    if (n) {
      n.data = { ...n.data, ...patch };
      this.graph.set(g);
    }
  }

  /** Persist position updates (also available for manual wiring). */
  updatePosition(id: string, position: { x: number; y: number }): void {
    const g = cloneGraph(this.graph());
    const n = g.nodes.find(x => x.id === id);
    if (n) {
      n.position = position;
      this.graph.set(g);
    }
  }

  toGraphDefinition(): GraphDefinition | null {
    const g = this.graph();
    return g.nodes.length === 0 ? null : cloneGraph(g);
  }

  loadGraph(def: GraphDefinition | null | undefined): void {
    this.graph.set(def ? cloneGraph(def) : emptyGraph());
    this.selectedNodeId.set(null);
  }
}
