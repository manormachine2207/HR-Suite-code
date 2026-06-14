/**
 * Read/write models for the global SMTP relay API (`/api/v1/platform/smtp`,
 * platform-admin — ADR-019 Stufe 3). SDR-004: the password is NEVER part of these
 * models — only `passwordRef` (the env var NAME) + a `secretConfigured` flag.
 */
export type SmtpSecurity = 'NONE' | 'STARTTLS' | 'TLS';

export interface SmtpRelay {
  readonly host: string;
  readonly port: number;
  readonly security: string;
  readonly fromAddress: string;
  readonly fromName: string | null;
  readonly username: string | null;
  readonly passwordRef: string | null;
  readonly secretConfigured: boolean;
  readonly enabled: boolean;
  readonly verified: boolean;
  readonly lastTestAt: string | null;
  readonly lastTestOutcome: string | null;
}

export interface UpdateSmtpRelay {
  readonly host: string;
  readonly port: number;
  readonly security: SmtpSecurity;
  readonly fromAddress: string;
  readonly fromName?: string | null;
  readonly username?: string | null;
  readonly passwordRef?: string | null;
  readonly enabled: boolean;
}

export interface TestSendResult {
  readonly success: boolean;
  readonly outcome: string;
}
