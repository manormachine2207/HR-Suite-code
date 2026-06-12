/**
 * Read model for an Antrag, mirroring the backend `AntragResponse` DTO
 * (`/api/v1/antrag`). `status` is the Antrag lifecycle (DRAFT → SUBMITTED → …);
 * kept as a string so a new backend status never breaks the frontend build.
 * `workflowProcessId` is the Flowable process-instance id stamped on submit
 * (ADR-009 §5) — null for drafts and for majors published before the workflow cut.
 */
export interface Antrag {
  id: string;
  antragstypId: string;
  antragstypVersionId?: string | null;
  submittedMinor?: number | null;
  status: string;
  payload: Record<string, unknown>;
  antragstellerSubject: string;
  workflowProcessId?: string | null;
  submittedAt?: string | null;
  createdAt: string;
  updatedAt: string;
}

/**
 * One user task of the Flowable process instance, in execution order (from the
 * Flowable history, so it stays available after the process has ended).
 * Mirrors `AntragProgressResponse.Step` of `GET /api/v1/antrag/{id}/progress`.
 */
export interface AntragProgressStep {
  stepKey: string;
  name: string;
  completed: boolean;
  startedAt?: string | null;
  completedAt?: string | null;
}

/**
 * Workflow progress of an Antrag for the approval-chain stepper (Cut D).
 * `steps` is empty for drafts (no process yet) and for Anträge submitted before
 * the workflow cut; `workflowProcessId` doubles as the trace reference.
 */
export interface AntragProgress {
  antragId: string;
  workflowProcessId: string | null;
  steps: AntragProgressStep[];
}
