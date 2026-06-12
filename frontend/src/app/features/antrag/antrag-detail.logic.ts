/**
 * Pure view logic for the Antrag detail page (Cut D, approval-chain stepper).
 * Kept free of Angular so the derivation rules are unit-testable without TestBed
 * (Tenet 14). The stepper renders only what the Flowable history delivers — it
 * never invents "theoretical" future steps.
 */

import { AntragProgressStep } from './antrag.model';

/** Visual state of one stepper circle. */
export type StepState = 'done' | 'active' | 'pending' | 'cancelled' | 'rejected';

/**
 * Derives the visual state per step from the history steps + the Antrag status:
 * - `CANCELLED` Antrag → every step greys out (the process was aborted).
 * - completed step → 'done'; the first non-completed step → 'active'; any further
 *   non-completed steps (e.g. parallel branches) → 'pending'.
 * - `REJECTED` Antrag → the last step turns 'rejected' (the chain ended there).
 */
export function stepStates(steps: AntragProgressStep[], antragStatus: string): StepState[] {
  if (antragStatus === 'CANCELLED') {
    return steps.map(() => 'cancelled');
  }
  let activeSeen = false;
  const states: StepState[] = steps.map(s => {
    if (s.completed) {
      return 'done';
    }
    if (!activeSeen) {
      activeSeen = true;
      return 'active';
    }
    return 'pending';
  });
  if (antragStatus === 'REJECTED' && states.length > 0) {
    states[states.length - 1] = 'rejected';
  }
  return states;
}

/** Whether the applicant may still withdraw (cancel) the Antrag. */
export function canCancel(antragStatus: string): boolean {
  return antragStatus === 'DRAFT' || antragStatus === 'SUBMITTED';
}
