import { ChangeDetectorRef, Component, OnInit, inject } from '@angular/core';
import { FormBuilder, FormGroup, ReactiveFormsModule } from '@angular/forms';
import { TranslateModule } from '@ngx-translate/core';

import { PlattformService } from '../plattform.service';
import { OidcConfig } from '../sso.model';

/**
 * SSO tab of the Plattform module (ADR-019 Stufe 2). Configures the GLOBAL OIDC
 * identity provider (SDR-001: one provider for the whole platform). SDR-004: the
 * client secret is never shown or entered here — the form takes the env-var
 * REFERENCE name; `secretConfigured` shows whether that variable is set.
 *
 * <p><b>Configure-only.</b> Stufe 2 stores the config but does NOT enforce it — a
 * persistent notice makes the "saved but not active" state explicit. Live sign-in
 * is the security-focused Stufe-2b follow-up. A 403 renders the operators-only
 * notice. Zoneless: every async callback calls cdr.markForCheck().
 */
@Component({
  selector: 'app-sso',
  standalone: true,
  imports: [TranslateModule, ReactiveFormsModule],
  templateUrl: './sso.component.html',
  styleUrl: './sso.component.scss',
})
export class SsoComponent implements OnInit {
  private readonly service = inject(PlattformService);
  private readonly cdr = inject(ChangeDetectorRef);
  private readonly fb = inject(FormBuilder);

  loading = true;
  failed = false;
  forbidden = false;
  saving = false;

  config: OidcConfig | null = null;
  errorKey = '';
  errorDetail = '';
  successKey = '';

  readonly form: FormGroup = this.fb.group({
    issuer: this.fb.control('', { nonNullable: true }),
    clientId: this.fb.control('', { nonNullable: true }),
    clientSecretRef: this.fb.control('', { nonNullable: true }),
    scopes: this.fb.control('openid profile email', { nonNullable: true }),
    redirectUri: this.fb.control('', { nonNullable: true }),
    tenantClaim: this.fb.control('', { nonNullable: true }),
    enabled: this.fb.control(false, { nonNullable: true }),
  });

  ngOnInit(): void {
    this.reload();
  }

  private reload(): void {
    this.loading = true;
    this.failed = false;
    this.forbidden = false;
    this.service.getOidc().subscribe({
      next: cfg => {
        this.config = cfg;
        this.form.patchValue({
          issuer: cfg.issuer ?? '', clientId: cfg.clientId ?? '',
          clientSecretRef: cfg.clientSecretRef ?? '', scopes: cfg.scopes ?? '',
          redirectUri: cfg.redirectUri ?? '', tenantClaim: cfg.tenantClaim ?? '',
          enabled: cfg.enabled,
        });
        this.loading = false;
        this.cdr.markForCheck();
      },
      error: e => {
        this.loading = false;
        if ((e as { status?: number })?.status === 403) {
          this.forbidden = true;
        } else {
          this.failed = true;
        }
        this.cdr.markForCheck();
      },
    });
  }

  save(): void {
    const v = this.form.getRawValue() as Record<string, unknown>;
    const issuer = (v['issuer'] as string).trim();
    const clientId = (v['clientId'] as string).trim();
    const enabled = v['enabled'] as boolean;
    this.errorKey = '';
    this.errorDetail = '';
    this.successKey = '';
    // Mirror the backend guard (ADR-019-spec): marking the config active requires at
    // least an issuer and a client id — even though Stufe 2 does not enforce the flag.
    if (enabled && (!issuer || !clientId)) {
      this.errorKey = 'plattform.sso.enableNeedsConfig';
      this.cdr.markForCheck();
      return;
    }
    this.saving = true;
    this.service.updateOidc({
      issuer: issuer || null,
      clientId: clientId || null,
      clientSecretRef: (v['clientSecretRef'] as string).trim() || null,
      scopes: (v['scopes'] as string).trim() || null,
      redirectUri: (v['redirectUri'] as string).trim() || null,
      tenantClaim: (v['tenantClaim'] as string).trim() || null,
      enabled,
    }).subscribe({
      next: cfg => {
        this.config = cfg;
        this.saving = false;
        this.successKey = 'plattform.sso.saved';
        this.cdr.markForCheck();
      },
      error: e => {
        this.saving = false;
        this.applyError(e, 'plattform.sso.saveError');
        this.cdr.markForCheck();
      },
    });
  }

  private applyError(e: unknown, fallbackKey: string): void {
    const detail = (e as { error?: { detail?: unknown } })?.error?.detail;
    if (typeof detail === 'string' && detail.trim().length > 0) {
      this.errorDetail = detail;
      this.errorKey = '';
    } else {
      this.errorDetail = '';
      this.errorKey = fallbackKey;
    }
  }
}
