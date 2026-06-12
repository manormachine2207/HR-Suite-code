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
