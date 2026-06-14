/**
 * Read model for an in-app notification (ADR-017 Stufe 2), mirroring the backend
 * `NotificationResponse`. `type` + `params` are rendered via i18n; `antragId` is the
 * deep-link target. Kept as plain strings so a new backend type never breaks the build.
 */
export interface NotificationItem {
  readonly id: string;
  readonly type: string;
  readonly antragId: string | null;
  readonly params: Record<string, unknown>;
  readonly read: boolean;
  readonly createdAt: string;
}
