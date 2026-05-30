import { FormDefinition } from '../form-designer/form-definition.model';

/**
 * Read model for an Antragstyp version, mirroring the backend
 * `AntragsTypVersionResponse` DTO (`GET /api/v1/antragstyp/{id}/versions`).
 */
export interface AntragsTypVersion {
  id: string;
  antragstypId: string;
  major: number;
  minor: number;
  status: string;
  formDefinition: FormDefinition;
  workflowBpmn?: string | null;
  publishedAt?: string | null;
  createdAt: string;
  updatedAt: string;
}
