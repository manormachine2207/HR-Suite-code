import { describe, it, expect } from 'vitest';

import { canCancel, stepStates } from './antrag-detail.logic';
import { AntragProgressStep } from './antrag.model';

function step(key: string, completed: boolean): AntragProgressStep {
  return {
    stepKey: key,
    name: key,
    completed,
    startedAt: '2026-06-12T08:00:00Z',
    completedAt: completed ? '2026-06-12T08:05:00Z' : null,
  };
}

describe('stepStates', () => {
  it('marks completed steps done and the first open step active', () => {
    const states = stepStates(
      [step('erfassen', true), step('pruefen', true), step('freigabe', false)],
      'IN_REVIEW'
    );
    expect(states).toEqual(['done', 'done', 'active']);
  });

  it('marks further open steps after the active one as pending (parallel branches)', () => {
    const states = stepStates(
      [step('erfassen', true), step('freigabe-a', false), step('freigabe-b', false)],
      'IN_REVIEW'
    );
    expect(states).toEqual(['done', 'active', 'pending']);
  });

  it('returns an empty array for an empty history (DRAFT / pre-workflow Antrag)', () => {
    expect(stepStates([], 'DRAFT')).toEqual([]);
  });

  it('greys out every step when the Antrag is CANCELLED', () => {
    const states = stepStates(
      [step('erfassen', true), step('freigabe', false)],
      'CANCELLED'
    );
    expect(states).toEqual(['cancelled', 'cancelled']);
  });

  it('marks the last step rejected when the Antrag is REJECTED', () => {
    const states = stepStates(
      [step('erfassen', true), step('freigabe', true)],
      'REJECTED'
    );
    expect(states).toEqual(['done', 'rejected']);
  });

  it('REJECTED with an empty history stays empty (no phantom step)', () => {
    expect(stepStates([], 'REJECTED')).toEqual([]);
  });
});

describe('canCancel', () => {
  it('allows withdrawing only DRAFT and SUBMITTED Anträge', () => {
    expect(canCancel('DRAFT')).toBe(true);
    expect(canCancel('SUBMITTED')).toBe(true);
    expect(canCancel('IN_REVIEW')).toBe(false);
    expect(canCancel('APPROVED')).toBe(false);
    expect(canCancel('REJECTED')).toBe(false);
    expect(canCancel('CANCELLED')).toBe(false);
  });
});
