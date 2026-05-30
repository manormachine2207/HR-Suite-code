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
