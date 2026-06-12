import { describe, it, expect } from 'vitest';

import {
  DECIDED_ANTRAG_STATUSES,
  OPEN_ANTRAG_STATUSES,
  countDecidedAntraege,
  countOpenAntraege,
} from './dashboard-kpis.logic';

/**
 * Pure-function spec for the dashboard KPI counters (ADR-017 Stufe 1): per Tenet 14
 * the tests pin the decision "DRAFT/SUBMITTED/IN_REVIEW = offen, APPROVED/REJECTED/
 * COMPLETED = entschieden, alles andere zählt nirgends".
 */
const a = (status: string): { status: string } => ({ status });

describe('countOpenAntraege', () => {
  it('counts exactly DRAFT, SUBMITTED and IN_REVIEW as open', () => {
    const list = [
      a('DRAFT'), a('SUBMITTED'), a('IN_REVIEW'),
      a('APPROVED'), a('REJECTED'), a('COMPLETED'), a('CANCELLED'),
    ];
    expect(countOpenAntraege(list)).toBe(3);
  });

  it('returns 0 for an empty list', () => {
    expect(countOpenAntraege([])).toBe(0);
  });

  it('ignores unknown future backend statuses', () => {
    expect(countOpenAntraege([a('ESCALATED'), a('DRAFT')])).toBe(1);
  });
});

describe('countDecidedAntraege', () => {
  it('counts exactly APPROVED, REJECTED and COMPLETED as decided', () => {
    const list = [
      a('APPROVED'), a('REJECTED'), a('COMPLETED'),
      a('DRAFT'), a('SUBMITTED'), a('IN_REVIEW'), a('CANCELLED'),
    ];
    expect(countDecidedAntraege(list)).toBe(3);
  });

  it('returns 0 for an empty list', () => {
    expect(countDecidedAntraege([])).toBe(0);
  });

  it('CANCELLED counts neither as open nor as decided', () => {
    const list = [a('CANCELLED'), a('CANCELLED')];
    expect(countOpenAntraege(list)).toBe(0);
    expect(countDecidedAntraege(list)).toBe(0);
  });
});

describe('status sets', () => {
  it('open and decided sets are disjoint (no Antrag counts twice)', () => {
    for (const s of OPEN_ANTRAG_STATUSES) {
      expect(DECIDED_ANTRAG_STATUSES.has(s)).toBe(false);
    }
  });
});
