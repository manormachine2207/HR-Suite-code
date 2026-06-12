import { describe, it, expect } from 'vitest';

import { outcomeLabelKey, payloadRows, problemDetailOf, statusKeyOf } from './review.logic';

describe('outcomeLabelKey', () => {
  it("maps 'approve' to its review.outcome.* key", () => {
    expect(outcomeLabelKey('approve')).toBe('review.outcome.approve');
  });

  it("maps 'reject' to its review.outcome.* key", () => {
    expect(outcomeLabelKey('reject')).toBe('review.outcome.reject');
  });

  it('returns null for undeclared outcomes so the raw value renders', () => {
    expect(outcomeLabelKey('escalate')).toBeNull();
    expect(outcomeLabelKey('')).toBeNull();
  });

  it('is case-sensitive (backend outcomes are lowercase by contract)', () => {
    expect(outcomeLabelKey('Approve')).toBeNull();
  });
});

describe('statusKeyOf', () => {
  it('returns the status.* key for known Antrag statuses incl. COMPLETED', () => {
    expect(statusKeyOf('SUBMITTED')).toBe('status.SUBMITTED');
    expect(statusKeyOf('IN_REVIEW')).toBe('status.IN_REVIEW');
    expect(statusKeyOf('COMPLETED')).toBe('status.COMPLETED');
  });

  it('returns null for unknown statuses so the raw enum renders', () => {
    expect(statusKeyOf('SOMETHING_NEW')).toBeNull();
  });
});

describe('problemDetailOf', () => {
  it('extracts a non-blank ProblemDetail.detail from an HttpErrorResponse-like object', () => {
    const e = { status: 422, error: { detail: "Outcome 'escalate' ist nicht erlaubt." } };
    expect(problemDetailOf(e)).toBe("Outcome 'escalate' ist nicht erlaubt.");
  });

  it('returns null for blank, missing or non-string detail', () => {
    expect(problemDetailOf({ error: { detail: '   ' } })).toBeNull();
    expect(problemDetailOf({ error: {} })).toBeNull();
    expect(problemDetailOf({ error: { detail: 42 } })).toBeNull();
    expect(problemDetailOf({})).toBeNull();
    expect(problemDetailOf(null)).toBeNull();
  });
});

describe('payloadRows', () => {
  it('returns an empty list for null/undefined payload', () => {
    expect(payloadRows(null)).toEqual([]);
    expect(payloadRows(undefined)).toEqual([]);
  });

  it('renders scalars verbatim (string/number/boolean)', () => {
    expect(payloadRows({ grund: 'Bahnticket', betrag: 84, eilig: true })).toEqual([
      { key: 'grund', value: 'Bahnticket' },
      { key: 'betrag', value: '84' },
      { key: 'eilig', value: 'true' },
    ]);
  });

  it('renders empty/null values as an em dash', () => {
    expect(payloadRows({ datum: '', frei: null })).toEqual([
      { key: 'datum', value: '—' },
      { key: 'frei', value: '—' },
    ]);
  });

  it('joins arrays and stringifies nested objects', () => {
    expect(payloadRows({ tags: ['a', 'b'], extra: { x: 1 } })).toEqual([
      { key: 'tags', value: 'a, b' },
      { key: 'extra', value: '{"x":1}' },
    ]);
  });
});
