import { TestBed } from '@angular/core/testing';
import { TranslateModule } from '@ngx-translate/core';
import { describe, it, expect, beforeEach } from 'vitest';
import { FlowCanvasEditorComponent } from './flow-canvas-editor.component';

describe('FlowCanvasEditorComponent', () => {
  let cmp: FlowCanvasEditorComponent;
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [FlowCanvasEditorComponent, TranslateModule.forRoot()],
    }).compileComponents();
    const f = TestBed.createComponent(FlowCanvasEditorComponent);
    cmp = f.componentInstance;
    f.componentRef.setInput('availableRefs', ['provision-ad-account']);
  });

  /**
   * Render regression test — covers the "Cannot read 'firstCreatePass' of null" crash
   * that happened when `changesController` and `connect` bare attributes were added to
   * <vflow>, double-instantiating directives that are already applied as host-directives
   * on VflowComponent.  This test MUST call detectChanges() to exercise the template.
   */
  it('renders the canvas without throwing (palette buttons exist in DOM)', async () => {
    const fixture = TestBed.createComponent(FlowCanvasEditorComponent);
    fixture.componentRef.setInput('availableRefs', []);
    // This detectChanges() would have thrown "Cannot read 'firstCreatePass' of null"
    // before the fix was applied.
    expect(() => fixture.detectChanges()).not.toThrow();
    await fixture.whenStable();
    const compiled: HTMLElement = fixture.nativeElement;
    // Palette buttons (one per NodeType) must be rendered.
    const buttons = compiled.querySelectorAll('.palette button');
    expect(buttons.length).toBeGreaterThan(0);
  });

  it('addNode adds to the graph; toGraphDefinition round-trips via loadGraph', () => {
    cmp.addNode('START');
    cmp.addNode('ACTION');
    expect(cmp.graph().nodes.length).toBe(2);
    const def = cmp.toGraphDefinition();
    const cmp2 = TestBed.createComponent(FlowCanvasEditorComponent).componentInstance;
    cmp2.loadGraph(def);
    expect(cmp2.graph().nodes.length).toBe(2);
    expect(cmp2.graph().nodes.map(n => n.type)).toEqual(['START', 'ACTION']);
  });

  it('toGraphDefinition returns null when the graph is empty', () => {
    expect(cmp.toGraphDefinition()).toBeNull();
  });

  it('exposes validation warnings from the pure logic', () => {
    cmp.addNode('XOR');   // no START/END, XOR unconditioned, missing key
    expect(cmp.warnings().map(w => w.code)).toContain('NO_START');
  });

  it('addNode spawns nodes collision-free (SP3 — no overlapping bounding boxes)', () => {
    cmp.addNode('START');
    cmp.addNode('FORM');
    cmp.addNode('END');
    const size = { width: 190, height: 64 };   // DEFAULT_NODE_SIZE
    const ps = cmp.graph().nodes.map(n => n.position);
    for (let i = 0; i < ps.length; i++) {
      for (let j = i + 1; j < ps.length; j++) {
        const a = ps[i], b = ps[j];
        const overlap = a.x < b.x + size.width && b.x < a.x + size.width
                     && a.y < b.y + size.height && b.y < a.y + size.height;
        expect(overlap).toBe(false);
      }
    }
  });

  // ---- ADR-016: APPROVAL role dropdown ------------------------------------

  it('renders the five ADR-016 approver groups as role dropdown options', async () => {
    const fixture = TestBed.createComponent(FlowCanvasEditorComponent);
    fixture.componentRef.setInput('availableRefs', []);
    const c = fixture.componentInstance;
    c.addNode('APPROVAL');
    c.select(c.graph().nodes[0].id);
    fixture.detectChanges();
    await fixture.whenStable();
    const options = Array.from(
      (fixture.nativeElement as HTMLElement).querySelectorAll<HTMLOptionElement>('.inspector select option')
    );
    expect(options.map(o => o.value)).toEqual([
      'approver-vg', 'approver-hr-bp', 'approver-hal', 'hr-reviewer', 'tenant-admin',
    ]);
  });

  it('new APPROVAL nodes default to approver-vg (ADR-016)', () => {
    cmp.addNode('APPROVAL');
    expect(cmp.graph().nodes[0].data.assigneeRole).toBe('approver-vg');
  });

  it('a legacy role outside the vocabulary appears as an extra "(current: …)" option', async () => {
    const fixture = TestBed.createComponent(FlowCanvasEditorComponent);
    fixture.componentRef.setInput('availableRefs', []);
    const c = fixture.componentInstance;
    c.loadGraph({
      nodes: [{ id: 'n1', type: 'APPROVAL', position: { x: 0, y: 0 }, data: { key: 'genehmigung', assigneeRole: 'legacy-role' } }],
      edges: [],
    });
    c.select('n1');
    fixture.detectChanges();
    await fixture.whenStable();
    expect(c.selectedNodeLegacyRole()).toBe('legacy-role');
    const select = (fixture.nativeElement as HTMLElement).querySelector<HTMLSelectElement>('.inspector select')!;
    const options = Array.from(select.options);
    expect(options).toHaveLength(6);                  // legacy + the 5 known groups
    expect(options[0].value).toBe('legacy-role');     // listed first, stays selectable
    expect(select.value).toBe('legacy-role');         // nothing is silently rewritten
  });

  it('selectedNodeLegacyRole is null for known roles', () => {
    cmp.addNode('APPROVAL');
    cmp.select(cmp.graph().nodes[0].id);
    expect(cmp.selectedNodeLegacyRole()).toBeNull();
  });

  it('onSelectionChange wires canvas selection to the inspector', () => {
    cmp.addNode('START');
    const nodeId = cmp.graph().nodes[0].id;
    cmp.onSelectionChange([{ id: nodeId, type: 'select', selected: true }]);
    expect(cmp.selectedNode()).not.toBeNull();
    expect(cmp.selectedNode()!.id).toBe(nodeId);
    // deselecting clears the inspector
    cmp.onSelectionChange([{ id: nodeId, type: 'select', selected: false }]);
    expect(cmp.selectedNode()).toBeNull();
  });
});
