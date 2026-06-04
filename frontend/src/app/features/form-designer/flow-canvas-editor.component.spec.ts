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
});
