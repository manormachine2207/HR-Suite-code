/**
 * Read model for an Antragstyp definition, mirroring the backend
 * `AntragsTypResponse` DTO (`GET /api/v1/antragstyp`). i18n fields are
 * locale → text maps (jsonb on the backend, BDR-005). `status` is the
 * AntragsTyp lifecycle (DRAFT → LIVE → …); kept as a string so a new
 * backend status never breaks the frontend build.
 */
export interface AntragsTypSummary {
  id: string;
  key: string;
  title: Record<string, string>;
  description?: Record<string, string> | null;
  status: string;
  currentVersionId?: string | null;
  createdAt: string;
  updatedAt: string;
}
