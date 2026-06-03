import { TestBed } from '@angular/core/testing';
import { TranslateModule } from '@ngx-translate/core';
import { describe, it, expect, beforeEach } from 'vitest';

import { FlowStepEditorComponent } from './flow-step-editor.component';
import { FlowDefinition } from './flow-definition.model';

describe('FlowStepEditorComponent', () => {
  let cmp: FlowStepEditorComponent;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [FlowStepEditorComponent, TranslateModule.forRoot()],
    }).compileComponents();
    const fixture = TestBed.createComponent(FlowStepEditorComponent);
    cmp = fixture.componentInstance;
    fixture.componentRef.setInput('availableRefs', ['provision-ad-account']);
    fixture.detectChanges();
  });

  it('starts empty and addStep appends a typed step', () => {
    expect(cmp.steps.length).toBe(0);
    cmp.addStep('FORM');
    cmp.addStep('ACTION');
    expect(cmp.steps.length).toBe(2);
    expect(cmp.steps.at(0).controls.kind.value).toBe('FORM');
    expect(cmp.steps.at(1).controls.kind.value).toBe('ACTION');
  });

  it('flags an invalid (hyphenated) step key', () => {
    cmp.addStep('FORM');
    cmp.steps.at(0).controls.key.setValue('bad-key');
    expect(cmp.steps.at(0).controls.key.invalid).toBe(true);
    cmp.steps.at(0).controls.key.setValue('good_key');
    expect(cmp.steps.at(0).controls.key.valid).toBe(true);
  });

  it('moveUp swaps order', () => {
    cmp.addStep('FORM'); cmp.steps.at(0).controls.key.setValue('first');
    cmp.addStep('ACTION'); cmp.steps.at(1).controls.key.setValue('second');
    cmp.moveUp(1);
    expect(cmp.steps.at(0).controls.key.value).toBe('second');
  });

  it('toFlowDefinition emits null when empty, omits empty title locales, keeps ACTION ref/inputMapping', () => {
    expect(cmp.toFlowDefinition()).toBeNull();

    cmp.addStep('ACTION');
    const g = cmp.steps.at(0);
    g.controls.key.setValue('provision');
    (g.controls.title as any).controls.de.setValue('Konto');
    g.controls.ref!.setValue('provision-ad-account');
    cmp.addInputMappingRow(0);
    const rows = cmp.inputMapping(0);
    rows.at(0).controls.k.setValue('upn');
    rows.at(0).controls.v.setValue('a@b.ch');

    const def = cmp.toFlowDefinition() as FlowDefinition;
    expect(def.steps).toHaveLength(1);
    expect(def.steps[0]).toEqual({
      kind: 'ACTION', key: 'provision', title: { de: 'Konto' },
      ref: 'provision-ad-account', inputMapping: { upn: 'a@b.ch' },
    });
  });

  it('loadFlow rehydrates an APPROVAL step', () => {
    cmp.loadFlow({ steps: [
      { kind: 'APPROVAL', key: 'review', title: { de: 'Freigabe' }, assigneeRole: 'hr-reviewer', outcomes: ['approve','reject'] },
    ]});
    expect(cmp.steps.length).toBe(1);
    expect(cmp.steps.at(0).controls.kind.value).toBe('APPROVAL');
    expect(cmp.steps.at(0).controls.assigneeRole!.value).toBe('hr-reviewer');
  });
});
